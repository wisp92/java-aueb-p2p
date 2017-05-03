package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
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
	private final File				shared_directory;
	
	private ClientChannel.Status status = ClientChannel.Status.UNKNOWN;
	
	/**
	 * @param group
	 * @param name
	 */
	public DownloadScheduler(final ThreadGroup group, final String name, final InetSocketAddress tracker_socket_address,
	        final int session_id, final String filename, String shared_directory) {
		super(group, name);
		
		this.tracker_socket_address = tracker_socket_address;
		this.session_id = session_id;
		this.filename = filename;
		this.shared_directory = new File(shared_directory);
		
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
	
	@Override
	public void run() {
		
		LoggerManager.tracedLog(Level.INFO, String.format("File <%s> was scheduled for download.", this.filename)); //$NON-NLS-1$
		
		List<Pair<String, InetSocketAddress>> peer_list = Collections.emptyList();
		List<Pair<InetSocketAddress, Long>> response_times = Collections.emptyList();
		String sender = null;
		
		try {
			
			try (SearchClient client_channel = new SearchClient(this.clients_group,
			        String.format("%s.SearchClient", this.getName()), this.tracker_socket_address, this.session_id, //$NON-NLS-1$
			        this.filename)) {
				
				client_channel.run();
				client_channel.join();
				
				if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {
					
					peer_list = client_channel.getPeerList();
					
				}
			}
			
			if (!peer_list.isEmpty()) {
				
				response_times = Channel.getResponseTime(
				        peer_list.parallelStream().map(x -> x.getSecond()).collect(Collectors.toSet()));
				
				LoggerManager.tracedLog(Level.FINE, String.format(
				        "The file <%s> was found in the following peers, appearing in ascending order based on their response time: %s", //$NON-NLS-1$
				        this.filename, response_times.toString()));
				
			}
			
			if (!response_times.isEmpty()) {
				
				for (Pair<InetSocketAddress, Long> pair : response_times) {
					
					try (SimpleDownloadClient client_channel = new SimpleDownloadClient(this.clients_group,
					        String.format("%s.SimpleDownloadClient", this.getName()), pair.getFirst(), this.filename, //$NON-NLS-1$
					        this.shared_directory)) {
						
						client_channel.start();
						client_channel.join();
						
						if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {
							
							this.status = ClientChannel.Status.SUCCESSFULL;
							sender = peer_list.stream().filter(x -> x.getSecond().equals(pair.getFirst())).findAny()
							        .get().getFirst();
							
							break;
						}
						
					}
					
				}
				
			}
			
		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the download attempt.", //$NON-NLS-1$
			        ex);
		} catch (final InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The download attempt was interrupted.", ex); //$NON-NLS-1$
		}
		
		if (this.status == ClientChannel.Status.UNKNOWN) {
			this.status = ClientChannel.Status.FAILED;
		}
		
	}
	
	public File getFile() {
		return new File(this.shared_directory, this.filename);
	}
	
}
