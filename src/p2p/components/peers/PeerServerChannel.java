package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.logging.Level;

import p2p.components.common.Credentials;
import p2p.components.communication.ServerChannel;
import p2p.components.communication.messages.Message;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.utilities.LoggerManager;

/**
 * A PeerServerChannel object handles an incoming connection from the tracker or
 * other peers.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerServerChannel extends ServerChannel {
	
	private final File shared_directory;
	
	/**
	 * Allocates a new PeerServerChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket
	 *            The {@link Socket} object associated with this channel.
	 * @param shared_directory
	 *            The directory where the shared files are located.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object.
	 */
	public PeerServerChannel(final ThreadGroup group, final String name, final Socket socket,
	        final File shared_directory) throws IOException {
		super(group, name, socket);
		
		this.shared_directory = shared_directory;
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate(final Request<?> request) throws IOException {
		
		try {
			
			final Request.Type request_type = request.getType();
			
			switch (request_type) {
			case SIMPLE_DOWNLOAD:
				
				this.transfer(request);
				break;
			
			default:
				
				/*
				 * In the request's type is not supported do not reply. A valid
				 * client should counter this case with a timeout. As far as the
				 * server is concerned the communication ended.
				 */
				
				LoggerManager.tracedLog(this, Level.WARNING,
				        String.format("Detected unsupported request type with name <%s>", request_type.name())); //$NON-NLS-1$
				
			}
			
		} catch (final ClassCastException ex) {
			throw new IOException(ex);
		}
		
	}
	
	protected boolean transfer(Request<?> request) throws IOException {
		
		final String filename = Message.getData(request, String.class);
		final File file = new File(this.shared_directory, filename);
		
		if (file.isFile()) {
			
			byte[] file_data = null;
			
			try {
				
				file_data = Files.readAllBytes(file.toPath());
				
			} catch (IOException ex) {
				LoggerManager.tracedLog(Level.SEVERE,
				        String.format("The file <%s> could not be serialized.", filename)); //$NON-NLS-1$
			}
			
			if (file_data != null) {
				
				this.out.writeObject(new Reply<>(Reply.Type.Success, file_data));
				
				return true;
				
			}
			
		}
		
		this.out.writeObject(Reply.getSimpleFailureMessage());
		
		return false;
		
	}
	
}
