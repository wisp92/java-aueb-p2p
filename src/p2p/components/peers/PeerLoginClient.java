package p2p.components.peers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Credentials;
import p2p.components.common.FileDescription;
import p2p.components.common.Pair;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Message;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A PeerLoginClient object sends a login request to the tracker and stores the
 * associate session id if the request completed successfully.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerLoginClient extends ClientChannel {

	private final Peer			caller;
	private final Credentials	user_credentials;
	private final ReentrantLock	authentication_lock;
	private final Condition		waits_response;

	private Integer session_id = null;

	/**
	 * Allocates a new LoginClientChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param caller
	 *            The peer that started the execution of this client. Used to
	 *            retrieve information about the peer.
	 * @param authentication_lock
	 *            A lock that indicates which Thread is currently processing the
	 *            authentication request.
	 * @param waits_response
	 *            A condition associated with the autentication's lock and
	 *            should be signaled when the session id of the session is
	 *            retrieved from the tracker.
	 * @param user_credentials
	 *            The credentials of the user.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public PeerLoginClient(final ThreadGroup group, final String name, final Peer caller,
	        final ReentrantLock authentication_lock, final Condition waits_response, final Credentials user_credentials)
	        throws IOException {
		super(group, name, caller.getTrackerAddress());

		this.caller = caller;
		this.authentication_lock = authentication_lock;
		this.user_credentials = user_credentials;
		this.waits_response = waits_response;

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#close()
	 */
	@Override
	public void close() throws IOException {

		super.close();

		/*
		 * Should also release the locks associated with this Thread and signal
		 * any threads waiting for the associated conditions.
		 */

		if (this.authentication_lock.isHeldByCurrentThread()) {

			if (this.authentication_lock.getWaitQueueLength(this.waits_response) > 0) {
				this.waits_response.signalAll();
			}

			this.authentication_lock.unlock();
		}

	}

	/**
	 * @return The session id received on a successful login attempt.
	 */
	public Integer getSessionID() {

		return this.session_id == null ? null : new Integer(this.session_id.intValue());

	}

	/**
	 * @return A copy of the user's credentials.
	 */
	public Credentials getUserCredentials() {

		return new Credentials(this.user_credentials);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.LOGIN, this.user_credentials));

		LoggerManager.tracedLog(this, Level.FINE, "A new login request was sent through the channel.");

		try {

			final Object reply = this.in.readObject();

			/*
			 * For maximum efficiency the lock should be locked at this point.
			 */

			this.authentication_lock.lock();

			this.session_id = Reply.getValidatedData(reply, Integer.class);
			if (this.session_id == null) throw new FailedRequestException();

			/*
			 * Signal the peer immediately when the tracker replies with a
			 * session id.
			 */

			this.waits_response.signalAll();
			this.waits_response.await();

			/*
			 * Wait for the server manager to be started and sends the peer's
			 * description to the tracker.
			 */

			final InetSocketAddress socket_address = this.caller.getServerAddress();

			if (socket_address == null) throw new FailedRequestException();

			/*
			 * Convert a list of files to a list of file description a send this
			 * new list along with socket address of the server to the tracker.
			 */
			this.out.writeObject(new Message<>(new Pair<>(socket_address, new HashSet<>(this.caller.getSharedFiles()
			        .parallelStream().map(x -> new FileDescription(x)).collect(Collectors.toSet())))));

			Reply.getValidatedData(this.in.readObject(), Boolean.class);

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		} catch (@SuppressWarnings("unused") final FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

}
