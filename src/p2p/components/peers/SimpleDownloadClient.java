package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.logging.Level;

import p2p.components.communication.ClientChannel;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;

/**
 * A SimpleDownloadClient object is responsible for implementign the transfer of
 * the requested file to the local shared directory.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SimpleDownloadClient extends ClientChannel {

	private final String filename;
	private final File	 shared_directory;

	/**
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @param filename
	 *            The requested filename.
	 * @param shared_directory
	 *            The peer's shared directory.
	 * @throws IOException
	 *             If an error occurs during the initialization of the
	 *             {@link Socket Socket} object.
	 */
	public SimpleDownloadClient(final ThreadGroup group, final String name, final InetSocketAddress socket_address,
	        final String filename, final File shared_directory) throws IOException {
		super(group, name, socket_address);

		this.filename = filename;
		this.shared_directory = shared_directory;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		if (this.shared_directory.isDirectory()) {

			final File file = new File(this.shared_directory, this.filename);

			if (!file.exists()) {

				this.out.writeObject(new Request<>(Request.Type.SIMPLE_DOWNLOAD, this.filename));

				LoggerManager.tracedLog(this, Level.FINE, String.format(
				        "A new download request for the file <%s> was sent through the channel.", this.filename));

				try {

					final byte[] file_data = Reply.getValidatedData(this.in.readObject(), byte[].class);

					Files.write(file.toPath(), file_data);

					this.status = Status.SUCCESSFULL;

				} catch (ClassCastException | ClassNotFoundException ex) {
					throw new IOException(ex);
				} catch (@SuppressWarnings("unused") final FailedRequestException ex) {

					this.status = Status.FAILED;

				}

			}

		}

		if (this.status == Status.UNKNOWN) {
			this.status = Status.FAILED;
		}

	}

}
