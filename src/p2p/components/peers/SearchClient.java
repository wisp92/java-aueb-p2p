package p2p.components.peers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Pair;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A SimpleDownload client object first sends a search request to the tracker
 * about a specific file. The trackers responds with a list of peers that can
 * provide the requested file. Then it sends a check alive message to all peers
 * to order them by their response time and tries get the file from a peer
 * according to the order.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SearchClient extends ClientChannel {

	private final int	 session_id;
	private final String filename;

	private List<Pair<String, InetSocketAddress>> peer_list;

	/**
	 * Allocates a new SearchClient object.
	 *
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 * @param session_id
	 * @param filename
	 *            The requested filename.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public SearchClient(final ThreadGroup group, final String name, final InetSocketAddress socket_address,
	        final int session_id, final String filename) throws IOException {
		super(group, name, socket_address);

		this.filename = filename;
		this.session_id = session_id;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		this.out.writeObject(new Request<>(Request.Type.SEARCH, new Pair<>(this.session_id, this.filename)));

		LoggerManager.tracedLog(this, Level.FINE,
		        String.format("A new search request for the file <%s> was sent through the channel.", this.filename)); //$NON-NLS-1$

		try {

			/*
			 * Reads the peer list from the reply and validates the data.
			 */
			final LinkedList<?> data = Reply.getValidatedData(this.in.readObject(), LinkedList.class);
			this.peer_list = data.parallelStream().map(x -> Pair.class.cast(x))
			        .map(x -> new Pair<>(String.class.cast(x.getFirst()), InetSocketAddress.class.cast(x.getSecond())))
			        .collect(Collectors.toList());

			this.status = Status.SUCCESSFULL;

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		} catch (@SuppressWarnings("unused") final FailedRequestException ex) {

			this.status = Status.FAILED;

		}

	}

	public List<Pair<String, InetSocketAddress>> getPeerList() {

		return this.peer_list.parallelStream().map(x -> new Pair<>(x)).collect(Collectors.toList());
	}

}
