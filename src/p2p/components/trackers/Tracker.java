package p2p.components.trackers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.logging.Level;

import p2p.components.communication.CloseableThread;
import p2p.utilities.LoggerManager;

/**
 * A Tracker object synchronizes and interconnects the peers that it manages.
 * Provides information about which peers are available at any point of time,
 * the files each one can provide and the required information to request a file
 * from a specific peer.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Tracker extends CloseableThread {

	private final ThreadGroup server_managers = CloseableThread.newThreadGroup(this, "ServerManagers");

	private TrackerServerManager current_server_manager = null;

	/**
	 * Allocates a new Tracker object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this tracker belongs to.
	 * @param name
	 *            The name of this tracker.
	 */
	public Tracker(final ThreadGroup group, final String name) {
		super(group, name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		if (this.current_server_manager != null) {
			this.stopManager();
		}

		CloseableThread.interrupt(this.server_managers);

	}

	/**
	 * @return The server's socket description if one is currently running.
	 */
	public InetSocketAddress getServerAddress() {

		if (!this.isWaitingConnections()) {

			LoggerManager.tracedLog(this, Level.WARNING,
			        "Tried to retrieve the server's socket address while the server manager is inactive.");

			return null;
		}

		return this.current_server_manager.getSocketAddress();

	}

	/**
	 * @return True If the tracker is currently waiting for incoming
	 *         connections.
	 */
	public boolean isWaitingConnections() {

		return (this.current_server_manager != null) && this.current_server_manager.isAlive();
	}

	/**
	 * Start a new {@link TrackerServerManager} object if one is not already
	 * running.
	 *
	 * @param port
	 *            The port number that the {@link ServerSocket} object is going
	 *            to listen to. 0 means that a random port is going to be
	 *            selected.
	 * @param database_path
	 *            The path of the database that the manager is going to pass to
	 *            the {@link TrackerServerChannel} objects.
	 * @return True If the server was started successfully.
	 */
	public boolean startManager(final int port, final String database_path) {

		if (this.isWaitingConnections()) return false;

		try {

			this.current_server_manager = new TrackerServerManager(this.server_managers,
			        String.format("%s.ServerManager", this.getName()), port, database_path);
			this.current_server_manager.start();

			return true;

		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE,
			        "An IOException occurred while initializing the server manager.", ex);
		}

		return false;

	}

	/**
	 * Stops the {@link TrackerServerManager} object if it is currently running.
	 *
	 * @return If the server was stopped successfully.
	 */
	public boolean stopManager() {

		if (!this.isWaitingConnections()) return false;

		this.current_server_manager.interrupt();

		try {

			this.current_server_manager.join();
			return true;

		} catch (final InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "An IOException occurred while stopping the server manager.",
			        ex);
		}

		return false;

	}

}
