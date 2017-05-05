package p2p.components.peers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.common.Pair;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A PeerAcknowledgeClient is responsible for notifying the tracker about a
 * successful download.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerAcknowledgeClient extends ClientChannel {

	private final int	 session_id;
	private final String username;
	private final String filename;

	/**
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @param session_id
	 *            The session's id of the peer.
	 * @param username
	 *            The username of the peer that the file was downloaded from.
	 * @param filename
	 *            The name of the downloaded file.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public PeerAcknowledgeClient(final ThreadGroup group, final String name, final InetSocketAddress socket_address,
	        final int session_id, final String username, final String filename) throws IOException {
		super(group, name, socket_address);

		this.username = username;
		this.filename = filename;
		this.session_id = session_id;

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.ACKNOWLEDGE,
		        new Pair<>(new Integer(this.session_id), new Pair<>(this.username, this.filename))));

		LoggerManager.tracedLog(this, Level.FINE,
		        String.format(
		                "A new acknowledge request about user<%s> for the file <%s> was sent through the channel.",
		                this.username, this.filename));

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
