package p2p.components.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import p2p.utilities.LoggerManager;

/**
 * A ServerChannelManager object listens to a specified {@link ServerSocket} for
 * incoming connections and allocates the necessary resources to handle the
 * traffic.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <S>
 *            The specific type of {@link ServerChannel} objects manages by this
 *            ServerChannelManager.
 */
public abstract class ServerChannelManager<S extends ServerChannel> extends CloseableThread {
	
	private final ServerSocket	server_socket;
	private final ThreadGroup	servers		   = CloseableThread.newThreadGroup(this, "Servers");
	private final Thread		server_cleaner = new ChannelCleaner(this.servers);
	private final ReentrantLock	listening_lock = new ReentrantLock();
	
	/**
	 * Allocates a new ServerChannelManager object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this manager belongs to.
	 * @param name
	 *            The name of this manager.
	 * @param port
	 *            The port number that the {@link ServerSocket} object is going
	 *            to listen to.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link ServerSocket} object.
	 */
	public ServerChannelManager(final ThreadGroup group, final String name, final int port) throws IOException {
		super(group, name);
		
		this.server_socket = new ServerSocket(port);
	}
	
	/**
	 * Allocates a new ServerChannelManager object. Not recommended since the
	 * {@link ServerSocket} object is going to be closed afterwards by the
	 * {@link ServerChannelManager#close close()} method.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this manager belongs to.
	 * @param name
	 *            The name of this manager.
	 * @param server_socket
	 *            The ServerSocket object that is associated with this manager.
	 */
	public ServerChannelManager(final ThreadGroup group, final String name, final ServerSocket server_socket) {
		super(group, name);
		
		this.server_socket = server_socket;
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		/*
		 * First interrupt the manager by closing the server's socket.
		 */
		while (!this.server_socket.isClosed()) {
			this.server_socket.close();
		}
		
		try {
			
			this.listening_lock.lock();
			
			/*
			 * Stop the server cleaner. All remaining servers are going to be
			 * closed in the following command.
			 */
			this.server_cleaner.interrupt();
			
			/*
			 * Then interrupt the execution of any remaining servers.
			 */
			CloseableThread.interrupt(this.servers);
			
		} finally {
			
			this.listening_lock.unlock();
			
		}
		
	}
	
	/**
	 * Close the server socket so no more incoming connection can initialized
	 * but keep any server channels until they are completed their transactions
	 * or interrupted by the cleaner.
	 */
	public void gentleInterrupt() {
		
		try {
			
			if (!this.server_socket.isClosed()) {
				this.server_socket.close();
			}
			
		} catch (final IOException ex) {
			
			LoggerManager.tracedLog(this, Level.WARNING, "The thread could not be closed properly.", ex);
			
		}
		
		while (true) {
			
			if (this.numberOfActiveServers() == 0) {
				
				this.interrupt();
				break;
				
			}
			
		}
		
	}
	
	/**
	 * @return A {@link InetSocketAddress} object that contains the required
	 *         information to communicate with the server's socket.
	 */
	public InetSocketAddress getSocketAddress() {
		
		return (InetSocketAddress) this.server_socket.getLocalSocketAddress();
		
	}
	
	/**
	 * @return The number of currently active servers.
	 */
	public int numberOfActiveServers() {
		
		return CloseableThread.countActive(this.servers);
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		
		try {
			
			if (!this.server_socket.isClosed()) {
				
				/*
				 * Start the cleaner.
				 */
				this.server_cleaner.start();
				
				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The server started listening for incoming connection"));
				
				for (int i = 0; !this.server_socket.isClosed(); i++) {
					
					try {
						
						
						
						try {
							
							this.listening_lock.lock();
							
							/*
							 * The {@link ServerChannelManager#close close()}
							 * prevents any memory leaks from the {@link
							 * ServerChannel} objects.
							 */
							
							@SuppressWarnings("resource")
							S server_channel = this.newServerChannel(this.servers,
							        String.format("%s.Server-%d", this.getName(), new Integer(i)),
							        this.server_socket.accept());
							
							server_channel.start();
							
							LoggerManager.tracedLog(this, Level.FINE,
							        String.format(
							                "A new connection started (%d active server(s) currently handled by the manager).",
							                new Integer(CloseableThread.countActive(this.servers))));
							
						} catch (@SuppressWarnings("unused") final SocketException ex) {
							
							/*
							 * Through an exception is the only way to stop the
							 * server's socket from waiting for incoming
							 * connections.
							 */
							
							break;
							
						} finally {
							
							this.listening_lock.unlock();
							
						}
						
						
					} catch (final IOException ex) {
						
						LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the communication.",
						        ex);
						
					}
					
				}
				
			}
			
		} finally {
			
			try {
				
				this.close();
				
				LoggerManager.tracedLog(this, Level.FINE,
				        String.format(
				                "A server stopped listening for incoming connections (%d active server remain and reducing).",
				                new Integer(CloseableThread.countActive(this.servers))));
				
			} catch (final IOException ex) {
				
				LoggerManager.tracedLog(this, Level.WARNING, "The server manager could not be closed properly.", ex);
				
			}
			
		}
		
	}
	
	/**
	 * Returns a new {@link ServerChannel} object with specific type S
	 * initialized with the given parameters.
	 *
	 * @param group
	 *            The {@link ThreadGroup} that the new channel is going to
	 *            belong to.
	 * @param name
	 *            The name of the new channel.
	 * @param socket
	 *            The binded socket associated with the new channel.
	 * @return A newly allocated ServerChannel object of type S.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object.
	 */
	protected abstract S newServerChannel(ThreadGroup group, String name, Socket socket) throws IOException;
	
}
