package p2p.components.trackers;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.Hash;
import p2p.components.common.Credentials;
import p2p.components.common.PeerContactInformation;
import p2p.components.common.PeerDescription;
import p2p.components.communication.ServerChannel;
import p2p.components.communication.messages.Message;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.utilities.LoggerManager;

/**
 * A TrackerServerChannel object handles an incoming connection from
 * one of the tracker's peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TrackerServerChannel extends ServerChannel {

	public static final boolean PEER_SERVER_REMOTE_HOST_POLICY = false;

	private final TrackerDatabase database;
	private final SessionManager  session_manager;

	/**
	 * Allocates a new TrackerServerChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket
	 *        The {@link Socket} object associated with this channel.
	 * @param database
	 *        The database that the user's information are stored.
	 * @param session_manager
	 *        The SessionManager object that is going to store
	 *        information about the current session after the login.
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object.
	 */
	public TrackerServerChannel(ThreadGroup group, String name, Socket socket, TrackerDatabase database,
	        SessionManager session_manager) throws IOException {
		super(group, name, socket);

		this.database = database;
		this.session_manager = session_manager;

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		try {

			Request<?> request = Request.class.cast(this.in.readObject());
			Request.Type request_type = request.getType();

			switch (request_type) {
			case REGISTER:

				this.register(request);
				break;

			case LOGIN:

				this.login(request);
				break;

			case LOGOUT:

				this.logout(request);
				break;

			default:

				/*
				 * In the request's type is not supported do not reply
				 * in order to raise an IOException to the client.
				 */
				LoggerManager.logMessage(this, Level.WARNING,
				        String.format("detected unsupported request type with name <%s>", request_type.name())); //$NON-NLS-1$

			}

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		}

	}

	protected boolean login(Request<?> request) throws IOException, ClassCastException, ClassNotFoundException {

		Credentials user_credentials = Message.getData(request, Credentials.class);

		String username = user_credentials.getUsername();
		Credentials registered_user = null;

		synchronized (this.database) {

			if (this.database.fix(this.database.getSchema())) {
				registered_user = this.database.getUser(user_credentials.getUsername());
			}

		}

		if (registered_user != null) {

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
							 * Should lock the session id until peer
							 * description is received or the login
							 * process fails.
							 */
							if (session_id != null) {
								this.session_manager.lockSessionID(session_id);
							}

						}

					}

					/*
					 * Another check should be implement here in case
					 * the generator fails.
					 */
					if (session_id != null) {

						this.out.writeObject(new Reply<>(Reply.Type.Success, session_id));

						/*
						 * Receive the peer's information.
						 */
						PeerDescription peer_description = Message.getData(this.in.readObject(), PeerDescription.class);

						/*
						 * Update the peer's host information
						 * according to known data and current policy.
						 */

						String peer_reveived_host = peer_description.getHostAddress();
						String peer_known_host = this.socket.getInetAddress().getHostAddress();

						if (!peer_reveived_host.equals(peer_known_host)) {
							if (!TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY) {
								peer_reveived_host = peer_known_host;
							}
						}

						boolean session_added = false;

						synchronized (this.session_manager) {

							this.session_manager.unlockSessionID(session_id);

							session_added = this.session_manager.addSession(
							        session_id, new PeerContactInformation(peer_reveived_host,
							                peer_description.getPort(), username),
							        peer_description.getSharedFileDescriptions());

						}

						if (session_added) {

							this.out.writeObject(Reply.getSimpleSuccessMessage());

							LoggerManager.logMessage(this, Level.INFO,
							        String.format("user with username <%s> logged in to session with id <%d>", //$NON-NLS-1$
							                user_credentials.getUsername(), session_id));

							return true;

						}

					}

				} finally {

					if (session_id != null) {

						synchronized (this.session_manager) {
							this.session_manager.unlockSessionID(session_id);
						}

					}

				}

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		LoggerManager.logMessage(this, Level.WARNING,
		        String.format("user with username <%s> tried to login but failed", user_credentials.getUsername())); //$NON-NLS-1$

		return false;

	}

	protected boolean logout(Request<?> request) throws IOException {

		Integer session_id = Message.getData(request, Integer.class);

		if (session_id != null) {

			boolean session_removed = false;

			synchronized (this.session_manager) {

				if (this.session_manager.isActiveSession(session_id)) {
					if (TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY
					        || this.session_manager.getPeerContactInformation(session_id).getHostAddress()
					                .equals(this.socket.getInetAddress().getHostAddress())) {
						session_removed = this.session_manager.removeSession(session_id);
					}
				}

			}

			if (session_removed) {

				this.out.writeObject(Reply.getSimpleSuccessMessage());

				LoggerManager.logMessage(this, Level.WARNING,
				        String.format("user with session id <%s> logged out successfully", session_id)); //$NON-NLS-1$

				return true;

			}

		}

		this.out.writeObject(Reply.getSimpleFailureMessage());

		LoggerManager.logMessage(this, Level.WARNING,
		        String.format("user with session id <%s> tried to logout but failed", session_id)); //$NON-NLS-1$

		return false;
	}

	/**
	 * Tries to insert the new user's username and hashed password in
	 * the database. If the user already exists this method return
	 * False.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return True the registration was successful.
	 * @throws IOException
	 */
	protected boolean register(Request<?> request) throws IOException {

		Credentials user_credentials = Message.getData(request, Credentials.class);

		/*
		 * TODO Add a salt.
		 */
		BigInteger hashed_password = Hash.getSHA1(user_credentials.getPassword());
		String username = user_credentials.getUsername();
		boolean user_registered = false;

		synchronized (this.database) {

			if (this.database.fix(this.database.getSchema()) && (this.database.getUser(username) == null)) {
				user_registered = this.database.setUser(username, hashed_password.toString(16));
			}

		}

		if (user_registered) {

			this.out.writeObject(Reply.getSimpleSuccessMessage());

			LoggerManager.logMessage(this, Level.WARNING,
			        String.format("registered a new user with username <%s>", user_credentials.getUsername())); //$NON-NLS-1$

		}
		else {

			this.out.writeObject(Reply.getSimpleFailureMessage());

			LoggerManager.logMessage(this, Level.WARNING, String
			        .format("tried to register an existing user with username <%s>", user_credentials.getUsername())); //$NON-NLS-1$

		}

		return user_registered;

	}

}
