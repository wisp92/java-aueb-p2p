package p2p.tracker;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

import p2p.common.CloseableThread;
import p2p.common.LoggerManager;
import p2p.common.structures.SocketDescription;

/**
 * A Tracker object synchronizes and interconnects the peers that it
 * manages. Provides information about which peers are available at
 * any point of time, the files each one can provide and the required
 * information to request a file from a specific peer.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Tracker extends CloseableThread {

	/**
	 * Starts the execution of the tracker.
	 *
	 * @param args
	 *        The console arguments.
	 */
	public static void main(String[] args) {

		LoggerManager logger_manager = new LoggerManager(new ConsoleHandler());
		logger_manager.setLoggingLevel(Level.FINE);
		logger_manager.setPropagate(false);
		LoggerManager.setAsDefault(logger_manager);

		ThreadGroup trackers = new ThreadGroup("Trackers"); //$NON-NLS-1$

		try (Scanner in_scanner = new Scanner(System.in); PrintWriter out_writer = new PrintWriter(System.out)) {

			try (Tracker tracker = new Tracker(trackers, Tracker.class.getSimpleName())) {

				new TrackerStartX(tracker, in_scanner, out_writer).start();

			} catch (IOException ex) {
				LoggerManager.getDefault().getLogger(Tracker.class.getName()).severe(ex.toString());
			}

		}

	}

	private final ThreadGroup server_managers
	        = new ThreadGroup(this.getThreadGroup(), String.format("%s.ServerManagers", this.getName())); //$NON-NLS-1$

	private TrackerServerManager current_server_manager = null;

	/**
	 * Allocates a new Tracker object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
	 */
	public Tracker(ThreadGroup group, String name) {
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
	 * @return The server's socket description if one is currently
	 *         running.
	 */
	public SocketDescription getSocketDescription() {

		if (this.isWaitingConnections()) return this.current_server_manager.getSocketDescription();

		LoggerManager.getDefault().getLogger(this.getName())
		        .warning(String.format("%s> The tracker is not currently listening to any ports.", this.getName())); //$NON-NLS-1$

		return null;
	}

	/**
	 * @return If the tracker is currently waiting for incoming
	 *         connections.
	 */
	public boolean isWaitingConnections() {

		return (this.current_server_manager != null) && this.current_server_manager.isAlive();
	}

	/**
	 * Start a new {@link TrackerServerManager} object if one is not
	 * already running.
	 *
	 * @param port
	 *        The port number that the {@link ServerSocket} object is
	 *        going to listen to. 0 means that a random port is going
	 *        to be selected.
	 * @param database_path
	 *        The path of the database that the manager is going to
	 *        pass to the {@link TrackerServerChannel} objects.
	 * @return If the server was started successfully.
	 */
	public boolean startManager(int port, String database_path) {

		if (this.isWaitingConnections()) return false;

		try {

			this.current_server_manager = new TrackerServerManager(this.server_managers,
			        String.format("%s.ServerManager", this.getName()), port, database_path); //$NON-NLS-1$
			this.current_server_manager.start();

			return true;

		} catch (IOException ex) {
			LoggerManager.getDefault().getLogger(this.getName()).severe(ex.toString());
		}

		return false;

	}

	/**
	 * Stops the {@link TrackerServerManager} object if it is
	 * currently running.
	 *
	 * @return If the server was stopped successfully.
	 */
	public boolean stopManager() {

		if (!this.isWaitingConnections()) return false;

		this.current_server_manager.interrupt();

		try {

			this.current_server_manager.join();
			return true;

		} catch (InterruptedException ex) {
			LoggerManager.getDefault().getLogger(this.getName()).warning(ex.toString());
		}

		return false;

	}

}
