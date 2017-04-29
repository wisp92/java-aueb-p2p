package p2p.peer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

import p2p.common.stubs.connection.ServerChannel;
import p2p.common.stubs.connection.message.Request;

class PeerServerChannel extends ServerChannel {

	private final File shared_directory;

	public PeerServerChannel(ThreadGroup group, String name, Socket socket, File shared_directory) throws IOException {
		super(group, name, socket);

		this.shared_directory = shared_directory;

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.stubs.connection.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {

		try {

			Request<?> request = Request.class.cast(this.in.readObject());
			Request.Type request_type = request.getType();

			switch (request_type) {
			default:

				/*
				 * In the request's type is not supported do not reply
				 * in order to raise an IOException to the client.
				 */

				this.log(Level.WARNING, String.format("detected unsupported request type with name <%s>", //$NON-NLS-1$
				        request_type.name()));

			}

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		}

	}

}
