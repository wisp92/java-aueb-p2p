package p2p.peer;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import p2p.common.CloseableThread;
import p2p.common.LoggerManager;
import p2p.common.structures.Credentials;
import p2p.common.structures.SocketDescription;
import p2p.common.stubs.connection.ClientChannel;

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
				LoggerManager.getDefault().getLogger(Peer.class.getName()).severe(ex.toString());
			}

		}

	}

	/**
	 * Checks if the lock is held by the current thread an tries to
	 * lock it the otherwise.
	 *
	 * @param lock
	 *        The lock to be locked.
	 * @return If the locked was able to be held at the end by the
	 *         current thread.
	 */
	public static boolean safeLock(ReentrantLock lock) {

		return lock.isHeldByCurrentThread() || lock.tryLock();

	}

	private final ReentrantLock	configuration_lock	= new ReentrantLock();
	private final ReentrantLock	authentication_lock	= new ReentrantLock();

	private final Condition	  tried_logged_in = this.authentication_lock.newCondition();
	private final ThreadGroup clients_group	  = new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.Clients", this.getName()));								//$NON-NLS-1$

	private final ThreadGroup server_managers_group	 = new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.ServerManagers", this.getName()));							 //$NON-NLS-1$
	private PeerServerManager current_server_manager = null;
	private String			  shared_directory_path	 = null;
	private SocketDescription tracker_description	 = null;

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
	public synchronized void close() throws IOException {

		CloseableThread.interrupt(this.clients_group);

		if (this.current_server_manager != null) {
			this.stopManager();
		}

		CloseableThread.interrupt(this.server_managers_group);

	}

	/**
	 * @return The id associated with current session.
	 */
	public Integer getSessionID() {

		return this.session_id;
	}

	/**
	 * @return The shared directory of the peer.
	 */
	public String getSharedDirectory() {

		return this.shared_directory_path;
	}

	/**
	 * @return The server's socket description if one is currently
	 *         running.
	 */
	public SocketDescription getSocketDescription() {

		if (this.isWaitingConnections()) return this.current_server_manager.getSocketDescription();

		LoggerManager.getDefault().getLogger(this.getName())
		        .warning(String.format("%s> The Peer is not currently listening to any ports.", this.getName())); //$NON-NLS-1$

		return null;
	}

	/**
	 * @return Information about the tracker's listening socket.
	 */
	public SocketDescription getTracker() {

		return this.tracker_description;
	}

	/**
	 * @return If the peer is currently waiting for incoming
	 *         connections.
	 */
	public boolean isWaitingConnections() {

		return (this.current_server_manager != null) && this.current_server_manager.isAlive();
	}

	/**
	 * Creates a new {@link PeerLoginClient} object to send a login
	 * request to the tracker and stores the session id if the login
	 * attempt was successful.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return If the login was successful.
	 */
	public boolean login(Credentials user_credentials) {

		if (!Peer.safeLock(this.configuration_lock) || !Peer.safeLock(this.authentication_lock)) return false;

		if (this.tracker_description != null) {

			try (PeerLoginClient client_channel = new PeerLoginClient(this.clients_group,
			        String.format("%s.Login", //$NON-NLS-1$
			                this.getName()),
			        this, this.configuration_lock, this.authentication_lock, this.tried_logged_in, user_credentials)) {

				client_channel.start();

				this.tried_logged_in.await();
				this.configuration_lock.unlock();

				if (client_channel.getStatus() != ClientChannel.Status.FAILED) {

					this.session_id = client_channel.getSessionID();

					LoggerManager.getDefault().getLogger(this.getName())
					        .fine(String.format(
					                "%s> User with credentials <%s> logged in to tracker with session id <%d>.", //$NON-NLS-1$
					                this.getName(), user_credentials.toString(), this.getSessionID()));

					return true;
				}

			} catch (IOException | InterruptedException | IllegalThreadStateException ex) {
				LoggerManager.getDefault().getLogger(this.getName()).warning(ex.toString());
			}

		}

		LoggerManager.getDefault().getLogger(this.getName())
		        .warning(String.format("%s> Registration of user with credentials <%s> failed.", //$NON-NLS-1$
		                this.getName(), user_credentials.toString()));

		return false;

	}

	/**
	 * @param user_credentials
	 * @return
	 */
	/**
	 * Creates a new {@link PeerRegisterClient} object to send a
	 * registration request to the tracker.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return If the registration was successful.
	 */
	public boolean register(Credentials user_credentials) {

		if (!Peer.safeLock(this.configuration_lock)) return false;

		if (this.tracker_description != null) {

			try (PeerRegisterClient client_channel
			        = new PeerRegisterClient(this.clients_group, String.format("%s.Register", //$NON-NLS-1$
			                this.getName()), this.tracker_description, user_credentials)) {

				client_channel.start();

				/*
				 * Since registration is a synchronous operation we
				 * need to wait for the thread to die.
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
				LoggerManager.getDefault().getLogger(this.getName()).warning(ex.toString());
			}

		}

		LoggerManager.getDefault().getLogger(this.getName())
		        .warning(String.format("%s> Registration of user with credentials <%s> failed.", //$NON-NLS-1$
		                this.getName(), user_credentials.toString()));

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

		if (!Peer.safeLock(this.configuration_lock)) return false;

		this.shared_directory_path = shared_directory_path;

		LoggerManager.getDefault().getLogger(this.getName())
		        .fine(String.format("%s> The shared directory's path changed to <%s>.", //$NON-NLS-1$
		                this.getName(), this.shared_directory_path));

		return true;

	}

	/**
	 * Updates the tracker's description.
	 *
	 * @param tracker_description
	 *        The new tracker's description.
	 * @return If the tracker description was updated successfully.
	 */
	public boolean setTracker(SocketDescription tracker_description) {

		if (!Peer.safeLock(this.configuration_lock)) return false;

		this.tracker_description = tracker_description;

		LoggerManager.getDefault().getLogger(this.getName())
		        .fine(String.format("%s> The tracker's description updated to <%s>.", //$NON-NLS-1$
		                this.getName(), this.tracker_description.toString()));

		return true;

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

		if (!Peer.safeLock(this.configuration_lock)) return false;

		if (this.isWaitingConnections() || (this.shared_directory_path == null)) return false;

		try {

			this.current_server_manager = new PeerServerManager(this.server_managers_group,
			        String.format("%s.ServerManager", this.getName()), port, this.shared_directory_path); //$NON-NLS-1$
			this.current_server_manager.start();

			return true;

		} catch (IOException ex) {
			LoggerManager.getDefault().getLogger(this.getName()).severe(ex.toString());
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

		if (!this.isWaitingConnections()) return false;

		this.current_server_manager.interrupt();

		try {

			this.current_server_manager.join();
			return true;

		} catch (InterruptedException ex) {
			LoggerManager.getDefault().getLogger(this.getName()).warning(ex.toString());
		}

		return false;

	}

}
