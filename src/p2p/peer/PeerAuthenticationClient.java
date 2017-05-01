package p2p.peer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import p2p.common.structures.Credentials;
import p2p.common.structures.PeerDescription;
import p2p.common.stubs.connection.ClientChannel;
import p2p.common.stubs.connection.exceptions.FailedRequestException;
import p2p.common.stubs.connection.message.Message;
import p2p.common.stubs.connection.message.Reply;
import p2p.common.stubs.connection.message.Request;

/**
 * A PeerAuthenticationClient object sends a login request to the
 * tracker and stores the session id provides as its response.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerAuthenticationClient extends ClientChannel {

	private final Peer			caller;
	private final Credentials	user_credentials;
	private final ReentrantLock	authentication_lock;
	private final Condition		waits_response;

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
	 * @param authentication_lock
	 *        A lock that indicates which is currently processing the
	 *        authentication request.
	 * @param waits_response
	 *        A condition associated with the autentication's lock and
	 *        should be signaled when the session id of the session is
	 *        retrieved from the tracker.
	 * @param user_credentials
	 *        The credentials of the new user.
	 * @throws IOException
	 *         If an error occurs during the initialization of the
	 *         {@link Socket Socket} object.
	 */
	public PeerAuthenticationClient(ThreadGroup group, String name, Peer caller, ReentrantLock authentication_lock,
	        Condition waits_response, Credentials user_credentials) throws IOException {
		super(group, name, caller.getTrackerAddress());

		this.caller = caller;
		this.authentication_lock = authentication_lock;
		this.user_credentials = user_credentials;
		this.waits_response = waits_response;

	}

	@Override
	public void close() throws IOException {

		super.close();

		if (this.authentication_lock.isHeldByCurrentThread()) {

			if (this.authentication_lock.getWaitQueueLength(this.waits_response) > 0) {
				this.waits_response.signalAll();
			}

			this.authentication_lock.unlock();
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
			
			Object reply = this.in.readObject();
			
			/*
			 * For maximum efficiency the lock should be locked at
			 * this point.
			 */
			
			this.authentication_lock.lock();
			
			this.session_id = Reply.getValidatedData(reply, Integer.class);
			if (this.session_id == null) throw new FailedRequestException();
			
			/*
			 * Signal the peer immediately when the tracker replies
			 * with a session id.
			 */
			
			this.waits_response.signalAll();
			this.waits_response.await();
			
			InetSocketAddress socket_address = this.caller.getServerAddress();
			if (socket_address == null) throw new FailedRequestException();

			this.out.writeObject(new Message<>(new PeerDescription(socket_address, this.caller.getSharedFiles())));

			Reply.getValidatedData(this.in.readObject(), Boolean.class);
			
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

		return new Integer(this.session_id);

	}

	/**
	 * @return The user's credentials.
	 */
	public Credentials getUserCredentials() {

		return new Credentials(this.user_credentials);
	}

}
