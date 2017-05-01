package p2p.components.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

import p2p.utilities.LoggerManager;

/**
 * A ServerChannelManager object listens to a specified
 * {@link ServerSocket} for incoming connections and allocates the
 * necessary resources to handle the traffic.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <S>
 *        The specific type of {@link ServerChannel} objects manages
 *        by this ServerChannelManager.
 */
public abstract class ServerChannelManager<S extends ServerChannel> extends CloseableThread {
	
	private final ServerSocket server_socket;
	
	/*
	 * TODO Check later.
	 */
	private final ThreadGroup servers
	        = new ThreadGroup(this.getThreadGroup()/* .getParent() */, String.format("%s.Servers", this.getName())); //$NON-NLS-1$
	
	/**
	 * Allocates a new ServerChannelManager object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this manager belongs
	 *        to.
	 * @param name
	 *        The name of this manager.
	 * @param port
	 *        The port number that the {@link ServerSocket} object is
	 *        going to listen to.
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link ServerSocket} object.
	 */
	public ServerChannelManager(ThreadGroup group, String name, int port) throws IOException {
		super(group, name);
		
		this.server_socket = new ServerSocket(port);
	}
	
	/**
	 * Allocates a new ServerChannelManager object. Not recommended
	 * since the {@link ServerSocket} object is going to be closed
	 * afterwards by the {@link ServerChannelManager#close close()}
	 * method.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this manager belongs
	 *        to.
	 * @param name
	 *        The name of this manager.
	 * @param server_socket
	 *        The ServerSocket object that is associated with this
	 *        manager.
	 */
	public ServerChannelManager(ThreadGroup group, String name, ServerSocket server_socket) {
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
		if (!this.server_socket.isClosed()) {
			this.server_socket.close();
		}
		
		/*
		 * Then interrupt the execution of any remaining servers.
		 */
		synchronized (this.servers) {
			
			CloseableThread.interrupt(this.servers);
			
		}
		
	}
	
	/**
	 * @return A {@link InetSocketAddress} object that contains the
	 *         required information to communicate with the server's
	 *         socket.
	 */
	public InetSocketAddress getSocketAddress() {
		
		return (InetSocketAddress) this.server_socket.getLocalSocketAddress();
		
	}
	
	/**
	 * Returns a new {@link ServerChannel} object with specific type S
	 * initialized with the given parameters.
	 *
	 * @param group
	 *        The {@link ThreadGroup} that the new channel is going to
	 *        belong to.
	 * @param name
	 *        The name of the new channel.
	 * @param socket
	 *        The binded socket associated with the new channel.
	 * @return A newly allocated ServerChannel object of type S.
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object.
	 */
	protected abstract S newServerChannel(ThreadGroup group, String name, Socket socket) throws IOException;
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		
		try {
			
			if (!this.server_socket.isClosed()) {
				
				LoggerManager.tracedLog(Level.FINE,
				        String.format("The server started listening for incoming connection")); //$NON-NLS-1$
				
				for (int i = 0;; i++) {
					
					synchronized (this.servers) {
						
						/*
						 * The {@link ServerChannelManager#close
						 * close()} prevents any memory leaks from the
						 * {@link ServerChannel} objects.
						 */
						
						S server_channel = null;
						
						try {
							
							server_channel = this.newServerChannel(this.servers,
							        String.format("%s.Server-%d", this.getName(), i), //$NON-NLS-1$
							        this.server_socket.accept());
							
						} catch (@SuppressWarnings("unused") IOException ex) {
							
							/*
							 * Through an exception is the only way to
							 * stop the server's socket from waiting
							 * for incoming connections.
							 */
							
							break;
							
						}
						
						server_channel.start();
						
						LoggerManager.tracedLog(Level.FINE,
						        String.format("A new connection started (%d active server currently handled by the manager).", //$NON-NLS-1$
						                CloseableThread.countActive(this.servers)));
						
					}
					
				}
				
			}
			
		} finally {
			
			try {
				
				this.close();
				
				LoggerManager.tracedLog(Level.FINE,
				        String.format("A server stopped listening for incoming connections (%d active server remain and reducing).", //$NON-NLS-1$
				                CloseableThread.countActive(this.servers)));
				
			} catch (IOException ex) {
				LoggerManager.tracedLog(Level.WARNING, "The server manager could not be closed properly.", ex); //$NON-NLS-1$
			}
			
		}
		
	}
	
}
