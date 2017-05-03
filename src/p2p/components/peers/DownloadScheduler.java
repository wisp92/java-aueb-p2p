package p2p.components.peers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Pair;
import p2p.components.communication.Channel;
import p2p.components.communication.ClientChannel;
import p2p.components.communication.CloseableThread;
import p2p.utilities.LoggerManager;

/**
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class DownloadScheduler extends CloseableThread {
	
	private final ThreadGroup clients_group = new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.Clients", this.getName()));
	
	private final InetSocketAddress	tracker_socket_address;
	private final int				session_id;
	private final String			filename;
	
	private ClientChannel.Status status = ClientChannel.Status.UNKNOWN;
	
	/**
	 * @param group
	 * @param name
	 */
	public DownloadScheduler(ThreadGroup group, String name, InetSocketAddress tracker_socket_address, int session_id,
	        String filename) {
		super(group, name);
		
		this.tracker_socket_address = tracker_socket_address;
		this.session_id = session_id;
		this.filename = filename;
		
	}
	
	public void run() {
		
		LoggerManager.tracedLog(Level.INFO, String.format("File <%s> was scheduled for download.", this.filename)); //$NON-NLS-1$
		
		try (SearchClient client_channel = new SearchClient(this.clients_group,
		        String.format("%s.SearchClient", this.getName()), this.tracker_socket_address, this.session_id, //$NON-NLS-1$
		        this.filename)) {
			
			client_channel.run();
			client_channel.join();
			
			this.status = client_channel.getStatus();
			
			if (this.status == ClientChannel.Status.SUCCESSFULL) {
				
				List<Pair<InetSocketAddress, Long>> response_times = Channel.getResponseTime(client_channel
				        .getPeerList().parallelStream().map(x -> x.getSecond()).collect(Collectors.toSet()));
				
				LoggerManager.tracedLog(Level.FINE,
				        String.format(
				                "The file <%s> was found int the following peers, appearing in ascending order based on their response time", //$NON-NLS-1$
				                this.filename, response_times));
				
			}
			
		} catch (IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the download attempt.", //$NON-NLS-1$
			        ex);
		} catch (InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The download attempt was interrupted.", ex); //$NON-NLS-1$
		}
		
		if (this.status == ClientChannel.Status.UNKNOWN) {
			this.status = ClientChannel.Status.FAILED;
		}
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		CloseableThread.interrupt(this.clients_group);
		
	}
	
	public ClientChannel.Status getStatus() {
		
		return this.status;
	}
	
}
