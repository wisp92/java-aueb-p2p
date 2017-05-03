package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Pair;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.ClientChannel.Status;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.components.exceptions.FailedRequestException;
import p2p.utilities.LoggerManager;


/**
 * @author {@literal p3100161 <Joseph Sakos>}
 *
 */
public class SimpleDownloadClient extends ClientChannel {
	
	private final String filename;
	private final File shared_directory;
	
	/**
	 * @param group
	 * @param name
	 * @param socket_address
	 * @throws IOException
	 */
	public SimpleDownloadClient(ThreadGroup group, String name, InetSocketAddress socket_address, String filename, File shared_directory) throws IOException {
		super(group, name, socket_address);
		
		this.filename = filename;
		this.shared_directory = shared_directory;
	}
	
	/* (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate() throws IOException, InterruptedException {
		
		if (this.shared_directory.isDirectory()) {
			
			File file = new File(this.shared_directory, this.filename);
			
			if (!file.exists()) {
			
        		this.out.writeObject(new Request<>(Request.Type.SIMPLE_DOWNLOAD, this.filename));
        
        		LoggerManager.tracedLog(this, Level.FINE,
        		        String.format("A new download request for the file <%s> was sent through the channel.", this.filename)); //$NON-NLS-1$
        
        		try {
        
        			byte[] file_data = Reply.getValidatedData(this.in.readObject(), byte[].class);
        			
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
