package p2p.components.trackers;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.Configuration;
import p2p.components.Hash;
import p2p.components.common.Credentials;
import p2p.components.common.FileDescription;
import p2p.components.common.Pair;
import p2p.components.communication.ServerChannel;
import p2p.components.communication.messages.Message;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.utilities.LoggerManager;

/**
 * A TrackerServerChannel object handles an incoming connection from one of the
 * tracker's peers. Specifies all the capabilities of the server in its
 * communication() method.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TrackerServerChannel extends ServerChannel {

	/**
	 * A TrackerServerChannel#UserStatus enumeration indicates the current
	 * status of a user.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum UserStatus {
		/**
		 * Indicates that the user is not currently active.
		 */
		ABSENT,
		/**
		 * Indicates that the user is currently active.
		 */
		PRESENT,
		/**
		 * Indicates that the user active and has already sent a file to another
		 * peer.
		 */
		SEEDER
	}

	/**
	 * The default time penalty that a user that has not yet send any files is
	 * going to experience.
	 */
	public static final int default_peer_penalty = Configuration.getDefault().getInteger("peer_penalty", 100);

	/**
	 * If a remote host server policy is applied then the peer can indicate a
	 * remote host as a server manager. Not recommended because a peer can take
	 * advantage of the policy and receive no Incoming traffic.
	 */
	public static final boolean PEER_SERVER_REMOTE_HOST_POLICY = false;

	private final TrackerDatabase database;

	private final SessionManager session_manager;

	/**
	 * Allocates a new TrackerServerChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket
	 *            The {@link Socket} object associated with this channel.
	 * @param database
	 *            The database that the user's information are stored.
	 * @param session_manager
	 *            The SessionManager object that is going to store information
	 *            about the current session.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object.
	 */
	public TrackerServerChannel(final ThreadGroup group, final String name, final Socket socket,
	        final TrackerDatabase database, final SessionManager session_manager) throws IOException {
		super(group, name, socket);

		this.database = database;
		this.session_manager = session_manager;

	}

	private final void applyPenalty(final String username) throws InterruptedException {

		if (this.getUserStatus(username) != UserStatus.SEEDER) {

			LoggerManager.tracedLog(this, Level.WARNING, String.format("Apply penalty to user <%s>.", username));

			Thread.sleep(TrackerServerChannel.default_peer_penalty);
		}

	}

	/**
	 * Process an acknowledge request. A acknowledge request updates the file
	 * information of the associated users and also aplies any benefits to the
	 * sender.
	 *
	 * @param request
	 *            The request that should be processed. The session id of the
	 *            peer sending the request, the name of the file to be
	 *            acknowledged and the username of the sender.
	 * @return True If the acknowledge request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 * @throws ClassCastException
	 *             If a unexpected data type is received.
	 */
	protected boolean acknowledge(final Request<?> request) throws IOException {

		Pair<?, ?> pair;

		pair = Pair.class.cast(request.getData());
		final Integer session_id = Integer.class.cast(pair.getFirst());

		pair = Pair.class.cast(pair.getSecond());
		final String username = String.class.cast(pair.getFirst());
		final String filename = String.class.cast(pair.getSecond());

		if ((session_id != null) && (this.getValidUser(session_id.intValue()) != null)) {

			boolean session_updated;

			synchronized (this.session_manager) {

				session_updated = this.session_manager.addDownloadFileFrom(session_id.intValue(), username, filename);

				synchronized (this.database) {

					if (this.database.fix(this.database.getSchema()) && (this.database.getUser(username) != null)) {
						session_updated &= this.database.addDownload(username);
					}

				}

			}

			if (session_updated) {

				this.out.writeObject(Reply.getSimpleSuccessMessage());

				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The session with id <%d> downloaded file <%s> from user with username <%s>.",
				                session_id, filename, username));

				return true;

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		LoggerManager.tracedLog(this, Level.WARNING,
		        String.format("Could not acknowledge user with username <%s>.", username));

		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate(final Request<?> request) throws IOException {

		try {

			final Request.Type request_type = request.getType();

			switch (request_type) {
			case REGISTER:

				this.register(request);
				break;

			case LOGIN:

				this.login(request);
				break;

			case SEARCH:

				this.search(request);
				break;

			case ACKNOWLEDGE:
				this.acknowledge(request);
				break;

			case LOGOUT:

				this.logout(request);
				break;

			default:

				/*
				 * In the request's type is not supported do not reply. A valid
				 * client should counter this case with a timeout. As far as the
				 * server is concerned the communication ended.
				 */

				LoggerManager.tracedLog(this, Level.WARNING,
				        String.format("Detected unsupported request type with name <%s>", request_type.name()));

			}

		} catch (ClassCastException | ClassNotFoundException | InterruptedException ex) {
			throw new IOException(ex);
		}

	}

	/**
	 * Check the session manager and the database to determine the status of the
	 * specified user. A user is characterized as absent or present depending on
	 * if the has an active session or not.Furthermore an active user with more
	 * than zero active downloads is considered a seeder.
	 *
	 * @param username
	 *            The username of the user to be checked.
	 * @return The status of the user.
	 */
	protected final UserStatus getUserStatus(final String username) {

		UserStatus user_status = UserStatus.ABSENT;

		synchronized (this.session_manager) {

			if (this.session_manager.isUserActive(username)) {
				user_status = UserStatus.PRESENT;
			}

		}

		if (user_status != UserStatus.ABSENT) {

			synchronized (this.database) {

				if (this.database.fix(this.database.getSchema())) {

					final Pair<Credentials, Integer> pair = this.database.getUser(username);

					if (pair != null) {

						if (pair.getSecond() != null) {
							if (pair.getSecond().intValue() >= 1) {
								user_status = UserStatus.SEEDER;
							}
						}

					}

				}

			}

		}

		return user_status;

	}

	/**
	 * Check if the session is active and corresponds to stored socket if peer
	 * remote server host policy is applied. If the session is active return the
	 * associated username.
	 *
	 * @param session_id
	 *            The session to be checked
	 * @return The associated username or null if the session is inactive.
	 */
	protected final String getValidUser(final int session_id) {

		String username = null;

		synchronized (this.session_manager) {

			if (this.session_manager.isActiveSession(session_id)) {
				if (TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY
				        || this.session_manager.getPeerInformation(session_id).getSecond().getAddress().getHostAddress()
				                .equals(this.socket.getInetAddress().getHostAddress())) {
					username = this.session_manager.getPeerInformation(session_id).getFirst();
				}
			}
		}

		return username;

	}

	/**
	 * Process a login request. A login attempt is successful if a user with the
	 * provided username is register to the database and if the hashed value of
	 * the provided password matches the stored password of the user.
	 * Furthermore a session can not be initialized until the associated peer
	 * does not provide the required contact information and shared file
	 * descriptions.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the user
	 *            credentials.
	 * @return True If the login request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 * @throws ClassCastException
	 *             If a unexpected data type is received.
	 * @throws ClassNotFoundException
	 *             If an unknown data type is received.
	 */
	protected boolean login(final Request<?> request)
	        throws IOException, ClassCastException, ClassNotFoundException, InterruptedException {

		final Credentials user_credentials = Message.getData(request, Credentials.class);

		final String username = user_credentials.getUsername();
		Credentials registered_user = null;

		/*
		 * Check if the user is registered is a synchronized block to avoid race
		 * case with the other authentication methods and threads.
		 */

		synchronized (this.database) {

			if (this.database.fix(this.database.getSchema())) {

				final Pair<Credentials, Integer> pair = this.database.getUser(user_credentials.getUsername());

				if (pair != null) {
					registered_user = pair.getFirst();
				}

			}

		}

		if (registered_user != null) {

			this.applyPenalty(username);

			/*
			 * Authenticate user.
			 */

			if (Hash.getSHA1(user_credentials.getPassword()).toString(16)
			        .equalsIgnoreCase(registered_user.getPassword())) {

				Integer session_id = null;

				try {

					/*
					 * Get user's id if active or generate a new one.
					 */

					synchronized (this.session_manager) {

						session_id = this.session_manager.getSessionID(username);

						if (session_id == null) {

							session_id = this.session_manager.getGeneratedID();

							/*
							 * Should lock the session id until peer description
							 * is received or the login process fails.
							 */

							if (session_id != null) {
								this.session_manager.lockSessionID(session_id.intValue());
							}

						}

					}

					/*
					 * Another check should be implemented here in case the
					 * generator fails.
					 */

					if (session_id != null) {

						this.out.writeObject(new Reply<>(Reply.Type.SUCCESS, session_id));

						/*
						 * Receive the peer's information.
						 */

						final Pair<?, ?> peer_description = Message.getData(this.in.readObject(), Pair.class);
						final InetSocketAddress peer_server_socket_address = InetSocketAddress.class
						        .cast(peer_description.getFirst());
						final HashSet<?> unknown_set = HashSet.class.cast(peer_description.getSecond());
						final HashSet<FileDescription> peer_shared_files = new HashSet<>(unknown_set.parallelStream()
						        .map(x -> FileDescription.class.cast(x)).collect(Collectors.toSet()));
						/*
						 * Update the peer's host information according to known
						 * data and current policy.
						 */

						String peer_reveived_host = peer_server_socket_address.getAddress().getHostAddress();
						final String peer_known_host = this.socket.getInetAddress().getHostAddress();

						if (!peer_reveived_host.equals(peer_known_host)) {
							if (!TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY) {
								peer_reveived_host = peer_known_host;
							}
						}

						boolean session_added = false;

						synchronized (this.session_manager) {

							/*
							 * Unlock the session id and add the new session to
							 * the manager.
							 */

							this.session_manager.unlockSessionID(session_id.intValue());

							session_added = this.session_manager.addSession(session_id.intValue(), username,
							        new InetSocketAddress(peer_reveived_host, peer_server_socket_address.getPort()),
							        peer_shared_files);

						}

						if (session_added) {

							this.out.writeObject(Reply.getSimpleSuccessMessage());

							LoggerManager.tracedLog(this, Level.FINE,
							        String.format(
							                "A new session with id <%d> was created for the user with username <%s>.",
							                session_id, user_credentials.getUsername()));

							return true;

						}

					}

				} finally {

					if (session_id != null) {

						/*
						 * Make sure to unlock the session id even if the
						 * request fails.
						 */

						synchronized (this.session_manager) {
							this.session_manager.unlockSessionID(session_id.intValue());
						}

					}

				}

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		LoggerManager.tracedLog(this, Level.WARNING, String
		        .format("The user with username <%s> tried to login but failed.", user_credentials.getUsername()));

		return false;

	}

	/**
	 * Process a logout request from the peer. In order for a peer to logout
	 * successfully the provided session id should be active. Also as long as a
	 * remote server host policy is not applied the request's sender is
	 * validated also through its IP address.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the
	 *            peer's session id.
	 * @return True If the logout process completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean logout(final Request<?> request) throws IOException {

		final Integer session_id = Message.getData(request, Integer.class);

		if ((session_id != null) && (this.getValidUser(session_id.intValue()) != null)) {

			boolean session_removed;

			synchronized (this.session_manager) {

				session_removed = this.session_manager.removeSession(session_id.intValue());

			}

			if (session_removed) {

				this.out.writeObject(Reply.getSimpleSuccessMessage());

				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The session with id <%d> was terminated by user's request.", session_id));

				return true;

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		LoggerManager.tracedLog(this, Level.WARNING, String.format(
		        "The logout request for the session with id <%d> could not be completed successfully.", session_id));

		return false;
	}

	/**
	 * Process a registration request. As long as the user identified by its
	 * name does not exists a registration is always possible.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the new
	 *            user's credentials.
	 * @return True If the registration request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean register(final Request<?> request) throws IOException {

		final Credentials user_credentials = Message.getData(request, Credentials.class);

		/*
		 * TODO Add a salt.
		 */

		/*
		 * The password's are always stored to the database hashed and never in
		 * plaintext format.
		 */

		final BigInteger hashed_password = Hash.getSHA1(user_credentials.getPassword());
		final String username = user_credentials.getUsername();
		boolean user_registered = false;

		synchronized (this.database) {

			if (this.database.fix(this.database.getSchema()) && (this.database.getUser(username) == null)) {
				user_registered = this.database.setUser(username, hashed_password.toString(16));
			}

		}

		if (user_registered) {

			this.out.writeObject(Reply.getSimpleSuccessMessage());

			LoggerManager.tracedLog(this, Level.FINE, String.format(
			        "A new user with username <%s> was registered to the tracker.", user_credentials.getUsername()));

		}
		else {

			this.out.writeObject(Reply.getSimpleFailureMessage());

			LoggerManager.tracedLog(this, Level.WARNING,
			        String.format("A registration with username <%s> could not be completed successfully.",
			                user_credentials.getUsername()));

		}

		return user_registered;

	}

	/**
	 * Process a search request for the specified file. Before sending the
	 * result back to the peer the tracker checks if the corresponding servers
	 * are already running.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the
	 *            requested filename.
	 * @return True If the search request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean search(final Request<?> request) throws IOException, InterruptedException {

		final Pair<?, ?> pair;

		pair = Pair.class.cast(request.getData());
		final Integer session_id = Integer.class.cast(pair.getFirst());
		final String filename = String.class.cast(pair.getSecond());
		String username = null;

		LinkedList<Pair<String, InetSocketAddress>> peers_information = new LinkedList<>();

		if (session_id != null) {

			synchronized (this.session_manager) {

				username = this.getValidUser(session_id.intValue());

				if (username != null) {

					peers_information = new LinkedList<>(this.session_manager.searchFilename(filename));

				}

			}

			if (username != null) {

				this.applyPenalty(username);

				this.out.writeObject(new Reply<>(Reply.Type.SUCCESS, peers_information));

				return true;

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		return false;

	}

}
