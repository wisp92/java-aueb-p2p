package p2p.components.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A CheckAliveClient object is a low level channel that can be used to find if
 * a server is alive and its response time. It is recommend for the caller to
 * make asynchronous calls for each server.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public final class CheckAliveClient extends ClientChannel {

	/**
	 * Allocates a new CheckAliveClient object.
	 *
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public CheckAliveClient(final ThreadGroup group, final String name, final InetSocketAddress socket_address)
	        throws IOException {
		super(group, name, socket_address);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected final void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.CHECK_ALIVE, Boolean.TRUE));

		LoggerManager.tracedLog(this, Level.FINE, "A new check alive request was sent through the channel.");

		try {

			Reply.getValidatedData(this.in.readObject(), Boolean.class);

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {

			throw new IOException(ex);

		} catch (@SuppressWarnings("unused") final FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

}
