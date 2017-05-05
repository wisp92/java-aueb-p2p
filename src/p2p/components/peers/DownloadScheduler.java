package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Pair;
import p2p.components.communication.Channel;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.CloseableThread;
import p2p.utilities.LoggerManager;

/**
 * A DownloadScheduler object sends a search request to tracker to identify the
 * peers that own the requested file. Then sort the the peers according to their
 * response time by batch sending check alive requests to each one and try to
 * download the file from each of them in order. Finally acknowledges the seeder
 * by sending an acknowledge request to the tracker.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class DownloadScheduler extends CloseableThread {

	private final ThreadGroup		clients_group = new ThreadGroup(String.format("%s.Clients", this.getName()));
	private final InetSocketAddress	tracker_socket_address;

	private final String filename;
	private final File	 shared_directory;

	private final int session_id;

	private ClientChannel.Status ack_status	= ClientChannel.Status.UNKNOWN;
	private ClientChannel.Status status		= ClientChannel.Status.UNKNOWN;

	/**
	 * Allocates a new scheduler object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this scheduler belongs to.
	 * @param name
	 *            The name of this scheduler.
	 * @param tracker_socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @param session_id
	 *            The session's id of the peer.
	 * @param filename
	 *            The filename to request.
	 * @param shared_directory_path
	 *            The path to peer's shared directory.
	 */
	public DownloadScheduler(final ThreadGroup group, final String name, final InetSocketAddress tracker_socket_address,
	        final int session_id, final String filename, final String shared_directory_path) {
		super(group, name);

		this.tracker_socket_address = tracker_socket_address;
		this.session_id = session_id;
		this.filename = filename;
		this.shared_directory = new File(shared_directory_path);

	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		/*
		 * Interrupt all associated threads.
		 */

		CloseableThread.interrupt(this.clients_group);

	}

	/**
	 * @return The current status of the acknowledgement request.
	 */
	public ClientChannel.Status getAcknowledgeStatus() {

		return this.ack_status;
	}

	/**
	 * @return The file this scheduler downloads.
	 */
	public File getFile() {

		return new File(this.shared_directory, this.filename);
	}

	/**
	 * @return The current status of the download.
	 */
	public ClientChannel.Status getStatus() {

		return this.status;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		LoggerManager.tracedLog(Level.INFO, String.format("File <%s> was scheduled for download.", this.filename));

		try {

			if (this.shared_directory.isDirectory()) {

				final List<Pair<String, InetSocketAddress>> peers_list = this.search();
				final InetSocketAddress peer = this.simpleDownload(this.tryCheckAlive(
				        peers_list.parallelStream().map(y -> y.getSecond()).collect(Collectors.toSet())));
				if (peer != null) {
					this.acknowledge(peers_list.parallelStream().filter(x -> x.getSecond().equals(peer)).findAny().get()
					        .getFirst());
				}

			}

		} catch (final IOException ex) {

			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the download attempt.", ex);

		} finally {

			/*
			 * If the download does no complete successfully change the status
			 * to FAILED.
			 */

			if (this.status == ClientChannel.Status.UNKNOWN) {
				this.status = ClientChannel.Status.FAILED;
			}

		}

	}

	/**
	 * Acknowledges the download of the file to the peer.
	 *
	 * @param username
	 *            The username of the peer that downloaded the file.
	 * @throws IOException
	 *             If the allocation of the {@link PeerAcknowledgeClient} object
	 *             fails.
	 */
	protected final void acknowledge(final String username) throws IOException {

		if (username != null) {

			try (PeerAcknowledgeClient client_channel = new PeerAcknowledgeClient(this.clients_group,
			        String.format("%s.PeerAcknowledgeClient", this.getName()), this.tracker_socket_address,
			        this.session_id, username, this.filename)) {

				client_channel.run();

				if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

					this.ack_status = ClientChannel.Status.SUCCESSFULL;

					LoggerManager.tracedLog(Level.FINE,
					        String.format("The download of file <%s> from peer <%s> was acknowledged by the tracker.",
					                this.filename, username));

				}
				else {

					this.ack_status = ClientChannel.Status.FAILED;

					LoggerManager.tracedLog(Level.WARNING,
					        String.format(
					                "The download of file <%s> from peer <%s> was not acknowledged by the tracker.",
					                this.filename, username));

				}

			}

		}

	}

	/**
	 * Sends a search request about the file to the tracker and returns the list
	 * of peers that are known to own the file.
	 *
	 * @return The list of peers that can provide the file.
	 * @throws IOException
	 *             If the allocation of the {@link SearchClient} object fails.
	 */
	protected final List<Pair<String, InetSocketAddress>> search() throws IOException {

		try (SearchClient client_channel = new SearchClient(this.clients_group,
		        String.format("%s.SearchClient", this.getName()), this.tracker_socket_address, this.session_id,
		        this.filename)) {

			client_channel.run();

			if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) return client_channel.getPeerList();
		}

		return Collections.emptyList();
	}

	/**
	 * Downloads the specified file from the first available peer in the list of
	 * sockets.
	 *
	 * @param peer_sockets_list
	 *            A list of sockets to try.
	 * @return The socket of the peer that downloaded the file or null on
	 *         failure.
	 * @throws IOException
	 *             If the allocation of the {@link SimpleDownloadClient} object
	 *             fails.
	 */
	protected final InetSocketAddress simpleDownload(final List<InetSocketAddress> peer_sockets_list)
	        throws IOException {

		if (!peer_sockets_list.isEmpty()) {

			int untried = peer_sockets_list.size();

			for (final InetSocketAddress peer_socket : peer_sockets_list) {

				try (SimpleDownloadClient client_channel = new SimpleDownloadClient(this.clients_group,
				        String.format("%s.SimpleDownloadClient", this.getName()), peer_socket, this.filename,
				        this.shared_directory)) {

					client_channel.run();

					untried--;

					if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

						this.status = ClientChannel.Status.SUCCESSFULL;

						LoggerManager.tracedLog(Level.INFO,
						        String.format(
						                "The file <%s> downloaded from peer <%s> (%d more from total %d could also have been used).",
						                this.filename, peer_socket.toString(), new Integer(untried),
						                new Integer(peer_sockets_list.size())));

						return peer_socket;

					}

					if (untried > 0) {

						LoggerManager.tracedLog(Level.WARNING,
						        String.format(
						                "The file <%s> was not possible to be downloaded from the peer <%s>, trying the next peer (%d remaining).",
						                this.filename, peer_socket.toString(), new Integer(untried)));
					}
				}

			}

		}

		return null;

	}

	/**
	 * Orders a list of peers according to their response times to check alive
	 * requests.
	 *
	 * @param unordered_peers_list
	 *            The set of peers sockets to be sorted.
	 * @return An ordered list of peers sockets.
	 */
	protected final List<InetSocketAddress> tryCheckAlive(final Set<InetSocketAddress> unordered_peers_list) {

		if (!unordered_peers_list.isEmpty()) {

			final List<InetSocketAddress> ordered_peers_list = Channel.getResponseTime(unordered_peers_list).stream()
			        .map(x -> x.getFirst()).collect(Collectors.toList());

			LoggerManager.tracedLog(Level.FINE,
			        String.format(
			                "The file <%s> was found in the following peers, appearing in ascending order based on their response time: %s",
			                this.filename, ordered_peers_list.toString()));

			return ordered_peers_list;

		}

		return Collections.emptyList();
	}

}
