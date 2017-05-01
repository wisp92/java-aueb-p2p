package p2p.common.stubs.connection;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;

import p2p.common.utilities.LoggerManager;

/**
 * A ServerChannelManager object listens to specified
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

	private final ThreadGroup servers
	        = new ThreadGroup(this.getThreadGroup().getParent(), String.format("%s.Servers", this.getName())); //$NON-NLS-1$

	/**
	 * Allocates a new ServerChannelManager object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
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
	 * method. * @param group The {@link ThreadGroup} object that this
	 * thread belongs to.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
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

		if (!this.server_socket.isClosed()) {
			this.server_socket.close();
		}

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
	 * Logs the given message to the manager's logger.
	 *
	 * @param level
	 *        The logging level of the message.
	 * @param message
	 *        The message.
	 */
	public synchronized void log(Level level, String message) {

		LoggerManager.getDefault().getLogger(this.getClass().getName()).log(level,
		        String.format("The %s object with name <%s> %s.", //$NON-NLS-1$
		                this.getClass().getSimpleName(), this.getName(), message));

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
	 *        The socket associated with the new channel.
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

				this.log(Level.INFO,
				        String.format("started (%d active servers)", CloseableThread.countActive(this.servers))); //$NON-NLS-1$

				for (int i = 0;; i++) {

					synchronized (this.servers) {

						/*
						 * The close() method of the
						 * ServerChannelManager makes sure to prevent
						 * any memory leaks from the ServerChannel
						 * object's.
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

						this.log(Level.INFO, String.format("started a new communication (%d active servers)", //$NON-NLS-1$
						        CloseableThread.countActive(this.servers)));

					}

				}

			}

		} finally {

			try {

				this.close();

				this.log(Level.INFO,
				        String.format("closed (%d active servers)", CloseableThread.countActive(this.servers))); //$NON-NLS-1$

			} catch (IOException ex) {
				LoggerManager.logException(LoggerManager.getDefault().getLogger(this.getClass().getName()),
				        Level.SEVERE, ex);
			}

		}

	}

}
