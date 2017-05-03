package p2p.components.communication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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

	class ServerCleaner extends Thread {

		public static final int default_check_intervale = 5000;

		private final int check_interval;

		public ServerCleaner(final ThreadGroup group, final int check_interval) {
			super(new ThreadGroup(group, String.format("%s.Cleaners", group.getName())), //$NON-NLS-1$
			        String.format("%s.Cleaner", group.getName())); //$NON-NLS-1$

			this.check_interval = check_interval;
		}

		@Override
		public void run() {

			try {

				while (true) {

					Thread.sleep(this.check_interval);
					CloseableThread.getActive(this.getThreadGroup().getParent()).parallelStream().forEach(x -> {
						if (x instanceof ServerChannel) {
							((ServerChannel) x).clean(this.check_interval);
						}
					});

				}

			} catch (@SuppressWarnings("unused") final InterruptedException ex) {
				LoggerManager.tracedLog(Level.INFO, "The server cleaner was stopped."); //$NON-NLS-1$
			}

		}

	}

	private final ServerSocket server_socket;

	private final ThreadGroup servers		 = new ThreadGroup(this.getThreadGroup(),
	        String.format("%s.Servers", this.getName()));															  //$NON-NLS-1$
	private final Thread	  server_cleaner = new ServerCleaner(this.servers, ServerCleaner.default_check_intervale);

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
		if (!this.server_socket.isClosed()) {
			this.server_socket.close();
		}

		this.server_cleaner.interrupt();

		/*
		 * Then interrupt the execution of any remaining servers.
		 */
		synchronized (this.servers) {

			CloseableThread.interrupt(this.servers);

		}

	}

	public void gentleInterrupt() {

		try {

			if (!this.server_socket.isClosed()) {
				this.server_socket.close();
			}

		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The thread could not be closed properly.", ex); //$NON-NLS-1$
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

				this.server_cleaner.start();

				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The server started listening for incoming connection")); //$NON-NLS-1$

				for (int i = 0;; i++) {

					synchronized (this.servers) {

						/*
						 * The {@link ServerChannelManager#close close()}
						 * prevents any memory leaks from the {@link
						 * ServerChannel} objects.
						 */

						S server_channel = null;

						try {

							server_channel = this.newServerChannel(this.servers,
							        String.format("%s.Server-%d", this.getName(), i), //$NON-NLS-1$
							        this.server_socket.accept());

						} catch (@SuppressWarnings("unused") final IOException ex) {

							/*
							 * Through an exception is the only way to stop the
							 * server's socket from waiting for incoming
							 * connections.
							 */

							break;

						}

						server_channel.start();

						LoggerManager.tracedLog(this, Level.FINE, String.format(
						        "A new connection started (%d active server(s) currently handled by the manager).", //$NON-NLS-1$
						        CloseableThread.countActive(this.servers)));

					}

				}

			}

		} finally {

			try {

				this.close();

				LoggerManager.tracedLog(this, Level.FINE, String.format(
				        "A server stopped listening for incoming connections (%d active server remain and reducing).", //$NON-NLS-1$
				        CloseableThread.countActive(this.servers)));

			} catch (final IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The server manager could not be closed properly.", ex); //$NON-NLS-1$
			}

		}

	}

}
