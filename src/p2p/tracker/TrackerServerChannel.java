package p2p.tracker;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.logging.Level;

import p2p.common.Hash;
import p2p.common.structures.Credentials;
import p2p.common.stubs.connection.ServerChannel;
import p2p.common.stubs.connection.message.Message;
import p2p.common.stubs.connection.message.Reply;
import p2p.common.stubs.connection.message.Request;

/**
 * A TrackerServerChannel object handles an incoming connection from
 * one of the tracker's peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class TrackerServerChannel extends ServerChannel {

	private final TrackerDatabase database;
	private final SessionManager  session_manager;

	/**
	 * Allocates a new Tracker.BasicServerChannel object.
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
	 * @see p2p.common.stubs.connection.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		try {

			Request<?> request = Request.class.cast(this.in.readObject());
			Request.Type request_type = request.getType();

			switch (request_type) {
			case REGISTER:
			case LOGIN:

				/*
				 * Both a registration and login request contain the
				 * credentials of the user.
				 */
				Credentials user_credentials = Message.getData(request, Credentials.class);

				switch (request_type) {
				case REGISTER:

					/*
					 * In registration request we only need to notify
					 * the client if the registration was successful.
					 */

					if (this.register(user_credentials)) {

						this.out.writeObject(Reply.getSimpleSuccessMessage());

						this.log(Level.INFO, String.format("registered a new user with username <%s>", //$NON-NLS-1$
						        user_credentials.getUsername()));

					}
					else {

						this.out.writeObject(Reply.getSimpleFailureMessage());

						this.log(Level.WARNING, String.format("tried to register an existing user with username <%s>", //$NON-NLS-1$
						        user_credentials.getUsername()));

					}

					break;

				case LOGIN:

					/*
					 * In login request if the login was successful we
					 * need to send to the client the session id that
					 * was created.
					 */

					Integer session_id = this.login(user_credentials);

					if (session_id != null) {

						this.out.writeObject(new Reply<>(Reply.Type.Success, session_id));

						this.log(Level.INFO, String.format("user with username <%s> logged in to session with id <%d>", //$NON-NLS-1$
						        user_credentials.getUsername(), session_id));

					}
					else {

						this.out.writeObject(Reply.getSimpleFailureMessage());

						this.log(Level.WARNING, String.format("user with username <%s> tried to login but failed", //$NON-NLS-1$
						        user_credentials.getUsername()));

					}

					break;

				}

				break;

			default:

				/*
				 * In the request's type is not supported do not reply
				 * in order to raise an IOException to the client.
				 */

				this.log(Level.WARNING, String.format("detected unsupported request type with name <%s>", //$NON-NLS-1$
				        request_type.name()));

			}

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		}

	}

	protected Integer login(Credentials user_credentials) {

		/*
		 * TODO Implement login method.
		 */
		return this.session_manager.getGeneratedID();
	}

	/**
	 * Tries to insert the new user's username and hashed password in
	 * the database. If the user already exists this method return
	 * False.
	 *
	 * @param user_credentials
	 *        The new user's credentials.
	 * @return True the registration was successful.
	 */
	protected boolean register(Credentials user_credentials) {

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

		return user_registered;

	}

}
