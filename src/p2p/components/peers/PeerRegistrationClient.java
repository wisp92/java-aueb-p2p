package p2p.components.peers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.common.Credentials;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A PeerRegistrationClient object sends a registration request to the tracker
 * and stores its response.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerRegistrationClient extends ClientChannel {

	private final Credentials user_credentials;

	/**
	 * Allocates a new Peer.RegisterClientChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @param user_credentials
	 *            The credentials of the new user.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public PeerRegistrationClient(ThreadGroup group, String name, InetSocketAddress socket_address,
	        Credentials user_credentials) throws IOException {
		super(group, name, socket_address);

		this.user_credentials = user_credentials;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.REGISTER, this.user_credentials));

		LoggerManager.tracedLog(this, Level.FINE, "A new registration request was sent through the channel."); //$NON-NLS-1$

		try {

			Reply.getValidatedData(this.in.readObject(), Boolean.class);

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		} catch (@SuppressWarnings("unused") FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

	/**
	 * @return The user's credentials.
	 */
	public Credentials getUserCredentials() {

		return new Credentials(this.user_credentials);
	}

}
