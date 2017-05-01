package p2p.peer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import p2p.common.structures.Credentials;
import p2p.common.stubs.connection.ClientChannel;
import p2p.common.stubs.connection.CloseableThread;
import p2p.common.utilities.LoggerManager;

/**
 * A Peer object acts on behalf of the user and communicates with the
 * tracker in order to learn information about the user's requests. It
 * is responsible to find and pull the file that the user needs and to
 * respond to similar requests from the tracker or other peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Peer extends CloseableThread {
	
	/**
	 * Starts the execution of the peer.
	 *
	 * @param args
	 *        The console arguments.
	 */
	public static void main(String[] args) {
		
		LoggerManager logger_manager = new LoggerManager(new ConsoleHandler());
		logger_manager.setLoggingLevel(Level.FINE);
		logger_manager.setPropagate(false);
		LoggerManager.setAsDefault(logger_manager);
		
		ThreadGroup peers = new ThreadGroup("Peers"); //$NON-NLS-1$
		
		try (Scanner in_scanner = new Scanner(System.in); PrintWriter out_writer = new PrintWriter(System.out)) {
			
			try (Peer peer = new Peer(peers, Peer.class.getSimpleName())) {
				
				new PeerStartX(peer, in_scanner, out_writer).start();
				
			} catch (IOException ex) {
				LoggerManager.logException(LoggerManager.getDefault().getLogger(Peer.class.getName()), Level.SEVERE,
				        ex);
			}
			
		}
		
	}
	
	/**
	 * A lock that should be accessible when the peer is updated in
	 * any way.
	 */
	private final ReentrantLock configuration_lock = new ReentrantLock();
	
	private final ThreadGroup clients_group			= new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.Clients", this.getName()));									//$NON-NLS-1$
	private final ThreadGroup server_managers_group	= new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.ServerManagers", this.getName()));							//$NON-NLS-1$

	private PeerServerManager current_server_manager = null;
	private String			  shared_directory_path	 = null;
	private InetSocketAddress tracker_socket_address = null;
	
	private Integer session_id = null;
	
	/**
	 * Allocates a new Peer object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
	 */
	public Peer(ThreadGroup group, String name) {
		super(group, name);
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		CloseableThread.interrupt(this.clients_group);
		
		if (this.current_server_manager != null) {
			this.stopManager();
		}
		
		CloseableThread.interrupt(this.server_managers_group);
		
	}

	/**
	 * @return The server's socket description if one is currently
	 *         running.
	 */
	public InetSocketAddress getServerAddress() {
		
		if (this.isWaitingConnections()) return this.current_server_manager.getSocketAddress();
		
		LoggerManager.getDefault().getLogger(this.getName())
		        .warning(String.format("%s> The Peer is not currently listening to any ports.", this.getName())); //$NON-NLS-1$
		
		return null;
	}

	/**
	 * @return The id associated with current session.
	 */
	public Integer getSessionID() {
		
		return new Integer(this.session_id);
	}
	
	/**
	 * @return The shared directory of the peer.
	 */
	public String getSharedDirectory() {
		
		return this.shared_directory_path;
	}
	
	/**
	 * @return The list of files in the shared directory.
	 */
	public List<File> getSharedFiles() {

		return Arrays.asList(new File(this.shared_directory_path).listFiles((file, filename) -> file.isFile()));
	}
	
	/**
	 * @return Information about the tracker's listening socket.
	 */
	public InetSocketAddress getTrackerAddress() {
		
		return this.tracker_socket_address;
	}
	
	/**
	 * @return If the peer is currently waiting for incoming
	 *         connections.
	 */
	public boolean isWaitingConnections() {

		return this.current_server_manager != null && this.current_server_manager.isAlive();
	}
	
	/**
	 * Creates a new {@link PeerAuthenticationClient} object to send a login
	 * request to the tracker and stores the session id if the login
	 * attempt was successful.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return If the login was successful.
	 */
	public boolean login(Credentials user_credentials) {
		
		if (!this.configuration_lock.tryLock()) return false;
		
		try {
			
			if (this.tracker_socket_address != null) {
				
				ReentrantLock authentication_lock = new ReentrantLock();
				Condition waits_authentication_response = authentication_lock.newCondition();
				
				authentication_lock.lock();
				
				try (PeerAuthenticationClient client_channel = new PeerAuthenticationClient(this.clients_group,
				        String.format("%s.Login", this.getName()), this, authentication_lock, //$NON-NLS-1$
				        waits_authentication_response, user_credentials)) {
					
					client_channel.start();
					
					waits_authentication_response.await();

					@SuppressWarnings("hiding")
					Integer session_id = client_channel.getSessionID();
					
					if (client_channel.getStatus() == ClientChannel.Status.UNKNOWN && session_id != null) {
						
						if (!this.isWaitingConnections()) {
							this.startManager(0);
						}
						
						waits_authentication_response.signalAll();
						authentication_lock.unlock();

						client_channel.join();
						
						if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {
							
							this.session_id = session_id;

							LoggerManager.getDefault().getLogger(this.getName())
							        .fine(String.format(
							                "%s> User with credentials <%s> logged in to tracker with session id <%d>.", //$NON-NLS-1$
							                this.getName(), user_credentials.toString(), this.getSessionID()));
							
							return true;
							
						}
					}
					
				} catch (IOException | InterruptedException ex) {
					LoggerManager.logException(LoggerManager.getDefault().getLogger(this.getName()), Level.WARNING, ex);
				}
				
			}
			
			LoggerManager.getDefault().getLogger(this.getName())
			        .warning(String.format("%s> Authentication of user with credentials <%s> failed.", //$NON-NLS-1$
			                this.getName(), user_credentials.toString()));
			
		} finally {
			this.configuration_lock.unlock();
		}
		
		return false;
		
	}

	/**
	 * Creates a new {@link PeerRegisterClient} object to send a
	 * registration request to the tracker.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return If the registration was successful.
	 */
	public boolean register(Credentials user_credentials) {
		
		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (this.tracker_socket_address != null) {

				try (PeerRegisterClient client_channel
				        = new PeerRegisterClient(this.clients_group, String.format("%s.Register", //$NON-NLS-1$
				                this.getName()), this.tracker_socket_address, user_credentials)) {

					client_channel.start();

					/*
					 * Since registration is a synchronous operation
					 * we need to wait for the thread to die.
					 */

					client_channel.join();

					if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

						LoggerManager.getDefault().getLogger(this.getName())
						        .fine(String.format(
						                "%s> Registration of user with credentials <%s> completed successfully.", //$NON-NLS-1$
						                this.getName(), user_credentials.toString()));

						return true;
					}

				} catch (IOException | InterruptedException ex) {
					LoggerManager.logException(LoggerManager.getDefault().getLogger(this.getName()), Level.WARNING, ex);
				}

			}

			LoggerManager.getDefault().getLogger(this.getName())
			        .warning(String.format("%s> Registration of user with credentials <%s> failed.", //$NON-NLS-1$
			                this.getName(), user_credentials.toString()));

		} finally {
			this.configuration_lock.unlock();
		}
		
		return false;
		
	}
	
	/**
	 * Updates the path to the shared directory.
	 *
	 * @param shared_directory_path
	 *        The path to the peer's shared directory.
	 * @return If the shared directory updated successfully.
	 */
	public boolean setSharedDirectory(String shared_directory_path) {
		
		if (!this.configuration_lock.tryLock()) return false;

		try {

			this.shared_directory_path = shared_directory_path;
			
			LoggerManager.getDefault().getLogger(this.getName())
			        .fine(String.format("%s> The shared directory's path changed to <%s>.", //$NON-NLS-1$
			                this.getName(), this.shared_directory_path));
			
			return true;

		} finally {
			this.configuration_lock.unlock();
		}
		
	}
	
	/**
	 * Updates the tracker's description.
	 * 
	 * @param tracker_socket_address
	 *        The tracker's socket address.
	 * @return If the tracker description was updated successfully.
	 */
	public boolean setTracker(InetSocketAddress tracker_socket_address) {
		
		if (!this.configuration_lock.tryLock()) return false;

		try {

			this.tracker_socket_address = tracker_socket_address;
			
			LoggerManager.getDefault().getLogger(this.getName())
			        .fine(String.format("%s> The tracker's description updated to <%s>.", //$NON-NLS-1$
			                this.getName(), this.tracker_socket_address.toString()));
			
			return true;

		} finally {
			this.configuration_lock.unlock();
		}
		
	}
	
	/**
	 * Start a new {@link PeerServerManager} object if one is not
	 * already running.
	 *
	 * @param port
	 *        The port number that the {@link ServerSocket} object is
	 *        going to listen to. 0 means that a random port is going
	 *        to be selected.
	 * @return If the server was started successfully.
	 */
	public boolean startManager(int port) {
		
		if (!this.configuration_lock.tryLock()) return false;

		try {
			
			if (this.isWaitingConnections() || this.shared_directory_path == null) return false;
			
			try {
				
				this.current_server_manager = new PeerServerManager(this.server_managers_group,
				        String.format("%s.ServerManager", this.getName()), port, this.shared_directory_path); //$NON-NLS-1$
				this.current_server_manager.start();
				
				return true;
				
			} catch (IOException ex) {
				LoggerManager.logException(LoggerManager.getDefault().getLogger(this.getName()), Level.SEVERE, ex);
			}
			
		} finally {
			this.configuration_lock.unlock();
		}
		
		return false;
		
	}
	
	/**
	 * Stops the {@link PeerServerManager} object if it is currently
	 * running.
	 *
	 * @return If the server was stopped successfully.
	 */
	public boolean stopManager() {
		
		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (!this.isWaitingConnections()) return false;

			this.current_server_manager.interrupt();

			try {

				this.current_server_manager.join();
				return true;

			} catch (InterruptedException ex) {
				LoggerManager.logException(LoggerManager.getDefault().getLogger(this.getName()), Level.WARNING, ex);
			}

		} finally {
			this.configuration_lock.unlock();
		}
		
		return false;
		
	}
	
}
