package p2p.peer;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

import p2p.common.structures.Credentials;
import p2p.common.structures.SocketDescription;
import p2p.common.stubs.connection.ClientChannel;
import p2p.common.stubs.connection.exceptions.FailedRequestException;
import p2p.common.stubs.connection.message.Reply;
import p2p.common.stubs.connection.message.Request;

/**
 * A PeerRegisterClient object sends a registration request to the
 * tracker and stores its response.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerRegisterClient extends ClientChannel {

	private final Credentials user_credentials;

	/**
	 * Allocates a new Peer.RegisterClientChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup ThreadGroup} object that this
	 *        channel belongs to.
	 * @param name
	 *        The name of this channel.
	 * @param socket_description
	 *        The {@link SocketDescription SocketDescription} of the
	 *        tracker's socket.
	 * @param user_credentials
	 *        The credentials of the new user.
	 * @throws IOException
	 *         If an error occurs during the initialization of the
	 *         {@link Socket Socket} object.
	 */
	public PeerRegisterClient(ThreadGroup group, String name, SocketDescription socket_description,
	        Credentials user_credentials) throws IOException {
		super(group, name, socket_description);

		this.user_credentials = user_credentials;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.stubs.connection.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.REGISTER, this.user_credentials));

		this.log(Level.FINE, "sent a registration request"); //$NON-NLS-1$

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

		return this.user_credentials;
	}

}
