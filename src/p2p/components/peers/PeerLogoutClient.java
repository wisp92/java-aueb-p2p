package p2p.components.peers;

import java.io.IOException;
import java.util.logging.Level;

import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerLogoutClient extends ClientChannel {

	private final Peer caller;

	/**
	 * @param group
	 * @param name
	 * @param socket_address
	 * @throws IOException
	 */
	public PeerLogoutClient(ThreadGroup group, String name, Peer caller) throws IOException {
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

		LoggerManager.logMessage(this, Level.INFO, "sent a logout request"); //$NON-NLS-1$

		try {

			Reply.getValidatedData(this.in.readObject(), Boolean.class);

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		} catch (@SuppressWarnings("unused") FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

}
