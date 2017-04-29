package p2p.peer;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import p2p.common.structures.Credentials;
import p2p.common.stubs.connection.ClientChannel;
import p2p.common.stubs.connection.exceptions.FailedRequestException;
import p2p.common.stubs.connection.message.Reply;
import p2p.common.stubs.connection.message.Request;

/**
 * A PeerLoginClient object sends a login request to the tracker and
 * stores the session id provides as its response.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerLoginClient extends ClientChannel {

	private final Credentials	user_credentials;
	private final ReentrantLock	configuration_lock;
	private final ReentrantLock	authentication_lock;
	private final Condition		tried_logged_in;
	private final Peer			caller;

	private Integer session_id = null;

	/**
	 * Allocates a new Peer.LoginClientChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup ThreadGroup} object that this
	 *        channel belongs to.
	 * @param name
	 *        The name of this channel.
	 * @param caller
	 *        The peer that started the execution of this client. Used
	 *        to create the PeerServerManager.
	 * @param configuration_lock
	 *        A lock that indicates which thread can currently update
	 *        the peer's information.
	 * @param authentication_lock
	 *        A lock that indicates which is currently processing the
	 *        authentication request.
	 * @param tried_logged_in
	 *        A condition associated with the autentication's lock and
	 *        should be signaled when the session id of the session is
	 *        retrieved from the tracker.
	 * @param user_credentials
	 *        The credentials of the new user.
	 * @throws IOException
	 *         If an error occurs during the initialization of the
	 *         {@link Socket Socket} object.
	 */
	public PeerLoginClient(ThreadGroup group, String name, Peer caller, ReentrantLock configuration_lock,
	        ReentrantLock authentication_lock, Condition tried_logged_in, Credentials user_credentials)
	        throws IOException {
		super(group, name, caller.getTracker());

		this.caller = caller;
		this.configuration_lock = configuration_lock;
		this.authentication_lock = authentication_lock;
		this.user_credentials = user_credentials;
		this.tried_logged_in = tried_logged_in;

	}

	@Override
	public void close() throws IOException {

		super.close();

		/*
		 * Unlock both locks just in case of an error but do not
		 * signal the condition for security.
		 */

		if (this.authentication_lock.isHeldByCurrentThread()) {
			this.authentication_lock.unlock();
		}
		if (this.configuration_lock.isHeldByCurrentThread()) {
			this.configuration_lock.unlock();
		}

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.stubs.connection.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.LOGIN, this.user_credentials));

		this.log(Level.FINE, "sent a login request"); //$NON-NLS-1$

		try {

			this.session_id = Reply.getValidatedData(this.in.readObject(), Integer.class);

			/*
			 * Signal the peer immediately when the tracker replies
			 * with a session id.
			 */

			this.authentication_lock.lock();
			this.tried_logged_in.signalAll();
			this.authentication_lock.unlock();

			if (this.session_id != null) {

				this.configuration_lock.lockInterruptibly();
				if (this.caller.startManager(0)) {
					/*
					 * TODO Send peer's description back to the
					 * tracker.
					 */
					System.out.println("Peer's server started."); //$NON-NLS-1$
				}

			}

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		} catch (@SuppressWarnings("unused") FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

	/**
	 * @return The stored session id associates with the login
	 *         success.
	 */
	public Integer getSessionID() {

		return this.session_id;

	}

	/**
	 * @return The user's credentials.
	 */
	public Credentials getUserCredentials() {

		return this.user_credentials;
	}

}
