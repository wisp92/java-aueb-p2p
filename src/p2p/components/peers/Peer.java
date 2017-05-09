package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Credentials;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.CloseableThread;
import p2p.utilities.LoggerManager;

/**
 * A Peer object acts on behalf of the user and communicates with the tracker in
 * order to learn information about the user's requests. It is responsible to
 * find and pull the file that the user needs and to respond to similar requests
 * from the tracker or other peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Peer extends CloseableThread {

	/**
	 * Indicates if a cooperative approach is going to be enforced above logout.
	 * Meaning that a logout request is going to be sent even if the server ha
	 * already been stopped just by checking if the ssion's id value has not
	 * been reset. This policy helps the tracker rather than the peer so its
	 * optional. Notice that it is not required for the correct communication
	 * because that tracker eventually is going to remove the session due to a
	 * failed CheckAlive request.
	 */
	public static final boolean COOPERATIVE_LOGOUT_POLICY = true;
	
	/*
	 * This lock should be accessible for the peer to be updated in any way.
	 */
	private final ReentrantLock configuration_lock = new ReentrantLock();

	/**
	 * A group that contains all the alive client channels of this peer. Used
	 * for closing and to keep track of the download processes.
	 */
	protected final ThreadGroup clients_group = CloseableThread.newThreadGroup(this, "Clients");

	/**
	 * A group that contains all the alive server managers. Should contain one
	 * or none for the basic implementation.
	 */
	protected final ThreadGroup server_managers_group = CloseableThread.newThreadGroup(this, "ServerManagers");

	private final LinkedList<DownloadScheduler>	download_managers	   = new LinkedList<>();
	private PeerServerManager					current_server_manager = null;
	private String								shared_directory_path  = null;
	private InetSocketAddress					tracker_socket_address = null;

	private Integer session_id = null;

	/**
	 * Allocates a new Peer object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this peer belongs to.
	 * @param name
	 *            The name of this peer.
	 */
	public Peer(final ThreadGroup group, final String name) {
		super(group, name);
	}

	/**
	 * Implement a new download request through the use of a
	 * {@link SearchClient} object. If the new file is going to be added to the
	 * shared files directory and the tracker is going to be notified for the
	 * change. Also to reward the seeder a acknowledgement message is going to
	 * be sent to the tracker. To check about the progress of a download the
	 * checkDownloads() method can be used.
	 *
	 * @param filename
	 *            The requested filename.
	 * @return True If the download was started successfully.
	 */
	public boolean addDownload(final String filename) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			if ((filename != null) && this.isTrackerSet() && this.isLoggedIn()) {

				final String download_name = String.format("%s.SimpleDownload-%s", this.getName(), filename);
				boolean request_processed = true;

				/*
				 * Check that only a single download request exist for the same
				 * file.
				 */

				synchronized (this.clients_group) {
					request_processed = CloseableThread.getActive(this.clients_group).parallelStream()
					        .filter(x -> x.getName().equals(download_name)).count() > 0;
				}

				if (request_processed) {

					LoggerManager.tracedLog(Level.WARNING,
					        String.format("A request to download the file <%s> is currently under process.", filename));

					return false;

				}

				/*
				 * The {@link Peer#close close()} prevents any memory leaks from
				 * the {@link SearchClient} objects.
				 */

				synchronized (this.clients_group) {

					final DownloadScheduler download_manager = new DownloadScheduler(this.clients_group, download_name,
					        this.tracker_socket_address, this.session_id.intValue(), filename,
					        this.shared_directory_path);

					download_manager.start();

					/*
					 * Since the group only stores active threads we use another
					 * structure to store all the user's download requests. This
					 * variable should only be used here to avoid race
					 * conditions.
					 */
					this.download_managers.add(download_manager);

				}

			}

		} finally {
			this.configuration_lock.unlock();
		}

		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		/*
		 * First stop the client channels associated with this peer to rare
		 * cases that is going to restart the server.
		 */

		CloseableThread.interrupt(this.clients_group);

		/*
		 * Then stop the server manager.
		 */

		if (this.current_server_manager != null) {
			this.stopManager();
		}

		/*
		 * Not really necessary but implemented for expansion. Close all the
		 * associated server managers.
		 */

		CloseableThread.interrupt(this.server_managers_group);

	}

	/**
	 * @return The number of successful downloads that the tracker acknowledged.
	 */
	public long getAcknowledgedDownloads() {

		return this.download_managers.parallelStream()
		        .filter(x -> x.getAcknowledgeStatus() == ClientChannel.Status.SUCCESSFULL).count();
	}

	/**
	 * @return The number of currently active downloads.
	 */
	public long getActiveDownloads() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.UNKNOWN)
		        .count();
	}

	/**
	 * @return The list of completed download files.
	 */
	public List<File> getCompletedDownloadFiles() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.SUCCESSFULL)
		        .map(x -> x.getFile()).distinct().collect(Collectors.toList());
	}

	/**
	 * @return The number of completed downloads.
	 */
	public long getCompletedDownloads() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.SUCCESSFULL)
		        .count();
	}

	/**
	 * @return The list of downloaded files.
	 */
	public List<File> getDownloadedFiles() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.SUCCESSFULL)
		        .map(x -> x.getFile()).distinct().collect(Collectors.toList());
	}

	/**
	 * @return The list of failed download files.
	 */
	public List<File> getFailedDownloadFiles() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.FAILED)
		        .map(x -> x.getFile()).distinct().collect(Collectors.toList());
	}

	/**
	 * @return The number of failed downloads.
	 */
	public long getFailedDownloads() {

		return this.download_managers.parallelStream().filter(x -> x.getStatus() == ClientChannel.Status.FAILED)
		        .count();
	}

	/**
	 * @return The server's socket description if one is currently running.
	 */
	public InetSocketAddress getServerAddress() {

		if (!this.isWaitingConnections()) {

			LoggerManager.tracedLog(this, Level.WARNING,
			        "Tried to retrieve the server's socket address while the server manager is inactive.");

			return null;

		}

		return this.current_server_manager.getSocketAddress();

	}

	/**
	 * @return The id associated with current session.
	 */
	public Integer getSessionID() {

		return this.session_id == null ? null : new Integer(this.session_id.intValue());
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

		return Arrays.asList(new File(this.shared_directory_path).listFiles()).parallelStream().filter(x -> x.isFile())
		        .collect(Collectors.toList());
	}

	/**
	 * @return Information about the tracker's listening socket.
	 */
	public InetSocketAddress getTrackerAddress() {

		return new InetSocketAddress(this.tracker_socket_address.getAddress().getHostAddress(),
		        this.tracker_socket_address.getPort());
	}

	/**
	 * @return True If the peer has a valid session id and also the peer's
	 *         server is waiting for connections. The second check is necessary
	 *         since the tracker could stop the session any time if not applied.
	 */
	public boolean isLoggedIn() {

		/*
		 * If the server manager is inactive then the tracker potentially logged
		 * out the session and should try to login again.
		 */
		return this.isWaitingConnections() && (this.session_id != null);
	}

	/**
	 * @return True If the shared directory is set.
	 */
	public boolean isSharedDirecorySet() {

		return this.shared_directory_path != null;
	}

	/**
	 * @return True If the tracker's contact information have been set.
	 */
	public boolean isTrackerSet() {

		return this.tracker_socket_address != null;
	}

	/**
	 * @return True If the peer is currently waiting for incoming connections.
	 */
	public boolean isWaitingConnections() {

		return (this.current_server_manager != null) && this.current_server_manager.isAlive();
	}

	/**
	 * Implement a new login request through the use of a
	 * {@link PeerLoginClient} object. If the request was successful a new
	 * session id is stored for future use and the server manager of the peer is
	 * activated to listen for incoming connections.
	 *
	 * @param user_credentials
	 *            The user's credentials.
	 * @return True If the login was successful.
	 */
	public boolean login(final Credentials user_credentials) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			/*
			 * Both the tracker information and the shared directory should be
			 * set for this operation to succeed. Also the specified user
			 * credentials should at least correspond to a valid username.
			 */

			if ((user_credentials != null) && this.isTrackerSet() && this.isSharedDirecorySet()) {

				/*
				 * A login request consists of more steps that require the
				 * coordination of the peer and the client channel. To avoid any
				 * race condition and deadlocks a lock object and a check
				 * condition are used to synchronize the two objects.
				 */

				final ReentrantLock authentication_lock = new ReentrantLock();
				final Condition waits_authentication_response = authentication_lock.newCondition();

				authentication_lock.lock();

				try (PeerLoginClient client_channel = new PeerLoginClient(this.clients_group,
				        String.format("%s.Login", this.getName()), this, authentication_lock,
				        waits_authentication_response, user_credentials)) {

					client_channel.start();

					/*
					 * At this point the peer waits for confirmation before
					 * starting the server manager to avoid unnecessary traffic
					 * in case of a failed login attempt.
					 */

					waits_authentication_response.await();

					@SuppressWarnings("hiding")
					final Integer session_id = client_channel.getSessionID();

					if ((client_channel.getStatus() == ClientChannel.Status.UNKNOWN) && (session_id != null)) {

						this.startManager(0);

						/*
						 * Signal the client that it is now safe to send the
						 * server's address to the tracker in order to complete
						 * the request
						 */

						waits_authentication_response.signalAll();
						authentication_lock.unlock();

						client_channel.join();

						if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

							this.session_id = session_id;

							LoggerManager.tracedLog(this, Level.INFO,
							        String.format(
							                "The peer logged in to the tracker with session id <%d> and credentials <%s>.",
							                this.getSessionID(), user_credentials.toString()));

							return true;

						}

						/*
						 * Make sure to stop the server in a failed login
						 * attempt.
						 */

						this.stopManager();

					}

				} catch (final IOException ex) {
					LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the login attempt.",
					        ex);
				} catch (final InterruptedException ex) {

					LoggerManager.tracedLog(this, Level.WARNING, "The login attempt was interrupted.", ex);

					/*
					 * Make sure the server is stopped and the session id is
					 * reseted in case of an error.
					 */

					this.stopManager();

					this.session_id = null;

				}

			}

			LoggerManager.tracedLog(this, Level.WARNING,
			        String.format("The authentication of the peer with credentials <%s> failed.",
			                user_credentials == null ? null : user_credentials.toString()));

		} finally {
			this.configuration_lock.unlock();
		}

		return false;

	}

	/**
	 * Implement a new logout request through the user of a
	 * {@link PeerLogoutClient} object. Two different types of logout's are
	 * considered. The server-side logout occurs when the a failed CheckAlive
	 * request occurs from the tracker or the peer explicitly asks for one. Note
	 * in the later case that the peer should request the logout providing the
	 * session id. The tracker further checks that the request was made from the
	 * id associated we the session if his policy demands it. The second type of
	 * logout is client-side where the peer simply stops the server and clears
	 * the session id. In this case a server-side logout occurs later. This
	 * method always performs a client-side logout and in the right conditions
	 * also a server-side logout.
	 *
	 * @return True If the logout was successful.
	 */
	public boolean logout() {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			boolean successful_serverside_logout = false;

			/*
			 * The following steps implement a server-side logout. For this kind
			 * of logout to occur the session id should be set. Also the server
			 * should be active if a cooperative policy is not applied.
			 */

			if (this.isLoggedIn() || (Peer.COOPERATIVE_LOGOUT_POLICY && (this.session_id != null))) {

				try (PeerLogoutClient client_channel = new PeerLogoutClient(this.clients_group,
				        String.format("%s.Login", this.getName()), this)) {

					client_channel.start();

					client_channel.join();

					successful_serverside_logout = (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL);

					if (successful_serverside_logout) {

						LoggerManager.tracedLog(this, Level.INFO,
						        String.format("The peer ended the session with id <%d> from the server's side.",
						                this.getSessionID()));

					}

				} catch (final IOException ex) {
					LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the logout attempt.",
					        ex);
				} catch (final InterruptedException ex) {
					LoggerManager.tracedLog(this, Level.WARNING, "The logout attempt was interrupted.", ex);
				}

			}

			/*
			 * Whatever the response, peer's best interest is to stop the server
			 * and remove the session id. The tracker is going to remove the
			 * peer's session because of a failed checkAlive message latter.
			 */

			this.stopManager();
			this.session_id = null;

			/*
			 * A successful logout is defined as the logical and of a successful
			 * server side logout and client side logout.
			 */

			final boolean successful_clientside_logout = !this.isWaitingConnections() && (this.session_id == null);

			if (successful_clientside_logout) {

				LoggerManager.tracedLog(this, Level.INFO, "The peer ended the session from the client's side.");

			}

			return successful_serverside_logout && successful_clientside_logout;

		} finally {
			this.configuration_lock.unlock();
		}

	}

	/**
	 * @return The number of currently active servers on this peer.
	 */
	public int numberOfActiveServers() {

		if (!this.isWaitingConnections()) {
			return 0;
		}
		
		return this.current_server_manager.numberOfActiveServers();
	}

	/**
	 * Implement a new registration request through the use of a
	 * {@link PeerRegistrationClient} object. If the request was successful a
	 * all further login attempts to the tracker with the specific credentials
	 * should be successful if no network error or interruptions occur. The
	 * request fails If the user is already registered.
	 *
	 * @param user_credentials
	 *            The new user's credentials.
	 * @return True If the registration was successful.
	 */
	public boolean register(final Credentials user_credentials) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			/*
			 * At least the tracker's information should before the request can
			 * be attempted. Also the user's credentials should be a valid
			 * {@link Credentials} object.
			 */

			if ((user_credentials != null) && (this.tracker_socket_address != null)) {

				try (PeerRegistrationClient client_channel = new PeerRegistrationClient(this.clients_group,
				        String.format("%s.Register", this.getName()), this.tracker_socket_address, user_credentials)) {

					client_channel.start();

					client_channel.join();

					if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

						LoggerManager.tracedLog(this, Level.INFO,
						        String.format("The peer registered to the tracker with credentials <%s>.",
						                user_credentials.toString()));

						return true;
					}

				} catch (final IOException ex) {
					LoggerManager.tracedLog(this, Level.SEVERE,
					        "An IOException occurred during the registration attempt.", ex);
				} catch (final InterruptedException ex) {
					LoggerManager.tracedLog(this, Level.WARNING, "The registration attempt was interrupted.", ex);
				}

			}

			LoggerManager.tracedLog(this, Level.WARNING,
			        String.format("The registration of the peer with credentials <%s> failed.",
			                user_credentials == null ? null : user_credentials.toString()));

		} finally {
			this.configuration_lock.unlock();
		}

		return false;

	}

	/**
	 * Updates the path to the shared directory. Fails if the peer is currently
	 * logged in to the tracker.
	 *
	 * @param shared_directory_path
	 *            The path to the peer's shared directory.
	 * @return True If the shared directory updated successfully.
	 */
	public boolean setSharedDirectory(final String shared_directory_path) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (!this.isLoggedIn()) {

				this.shared_directory_path = shared_directory_path;

				LoggerManager.tracedLog(this, Level.INFO,
				        String.format("The shared directory's path changed to <%s>.", this.shared_directory_path));

				return true;

			}

		} finally {
			this.configuration_lock.unlock();
		}

		return false;
	}

	/**
	 * Updates the tracker's description. Fails if the peer is currently logged
	 * in to the tracker.
	 *
	 * @param tracker_socket_address
	 *            The tracker's socket address.
	 * @return True If the tracker description was updated successfully.
	 */
	public boolean setTracker(final InetSocketAddress tracker_socket_address) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (!this.isLoggedIn()) {

				this.tracker_socket_address = tracker_socket_address;

				LoggerManager.tracedLog(this, Level.INFO, String.format("The tracker's description updated to <%s>",
				        this.getTrackerAddress().toString()));

				return true;

			}

		} finally {
			this.configuration_lock.unlock();
		}

		return false;

	}

	/**
	 * Start a new {@link PeerServerManager} object if one is not already
	 * running.
	 *
	 * @param port
	 *            The port number that the {@link ServerSocket} object is going
	 *            to listen to. 0 means that a random port is going to be
	 *            selected.
	 * @return True If the server was started successfully.
	 */
	public boolean startManager(final int port) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (this.isWaitingConnections() || !this.isSharedDirecorySet()) return false;

			try {

				this.current_server_manager = new PeerServerManager(this.server_managers_group,
				        String.format("%s.ServerManager", this.getName()), port, this.shared_directory_path);
				this.current_server_manager.start();

				return true;

			} catch (final IOException ex) {
				LoggerManager.tracedLog(this, Level.SEVERE,
				        "An IOException occurred while initializing the server manager.", ex);
			}

		} finally {
			this.configuration_lock.unlock();
		}

		return false;

	}

	/**
	 * Stops the {@link PeerServerManager} object if it is currently running.
	 *
	 * @return True If the server was stopped successfully.
	 */
	public boolean stopManager() {

		return this.stopManager(false);
	}

	/**
	 * Stops the {@link PeerServerManager} object if it is currently running.
	 *
	 * @param use_gentle_interrupt
	 *            If true then instead of stopping the server, the is going to
	 *            stop accepting new connection and wait for the other to finish
	 *            before terminating.
	 * @return True If the server was stopped successfully.
	 */
	public boolean stopManager(final boolean use_gentle_interrupt) {

		if (!this.configuration_lock.tryLock()) return false;

		try {

			if (!this.isWaitingConnections()) return false;

			if (use_gentle_interrupt) {
				this.current_server_manager.gentleInterrupt();
			}
			else {
				this.current_server_manager.interrupt();
			}

			try {

				this.current_server_manager.join();
				return true;

			} catch (final InterruptedException ex) {
				LoggerManager.tracedLog(this, Level.WARNING,
				        "An IOException occurred while stopping the server manager.", ex);
			}

		} finally {
			this.configuration_lock.unlock();
		}

		return false;

	}

}
