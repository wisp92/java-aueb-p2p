package p2p.peer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Level;

import p2p.common.stubs.connection.ServerChannel;
import p2p.common.stubs.connection.message.Request;

/**
 * A PeerServerChannel object handles an incoming connection from the
 * tracker or other peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerServerChannel extends ServerChannel {
	
	private final File shared_directory;
	
	/**
	 * Allocates a new PeerServerChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket
	 *        The {@link Socket} object associated with this channel.
	 * @param shared_directory
	 *        The directory where the shared files are located.
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object.
	 */
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
