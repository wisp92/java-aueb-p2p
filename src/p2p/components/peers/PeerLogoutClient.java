package p2p.components.peers;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A PeerLogoutClient object sends a logout request to the tracker and stores
 * its response.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerLogoutClient extends ClientChannel {

	private final Peer caller;

	/**
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param caller
	 *            The peer that started the execution of this client. Used
	 *            retrieve information about the peer.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public PeerLogoutClient(final ThreadGroup group, final String name, final Peer caller) throws IOException {
		super(group, name, caller.getTrackerAddress());

		this.caller = caller;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.LOGOUT, this.caller.getSessionID()));

		LoggerManager.tracedLog(this, Level.FINE, "A new logout request was sent through the channel.");

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
