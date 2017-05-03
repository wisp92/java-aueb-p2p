package p2p.components.trackers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import p2p.components.communication.ServerChannelManager;

/**
 * A TrackerServerManager is a {@link ServerChannelManager} object that
 * listens's for incoming connections from the peers and allocates a new
 * {@link TrackerServerChannel} object to handle each one.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class TrackerServerManager extends ServerChannelManager<TrackerServerChannel> {

	private final TrackerDatabase database;
	private final SessionManager  session_manager = new SessionManager();

	/**
	 * Allocates a new TrackerServerManager object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this manager belongs to.
	 * @param name
	 *            The name of this manager.
	 * @param port
	 *            The port number that the {@link ServerSocket} object is going
	 *            to listen to.
	 * @param database_path
	 *            The path of the database that the {@link TrackerServerChannel}
	 *            objects created by this manager are going to communicate with.
	 * @throws IOException
	 *             I an error occurs during the allocation of the
	 *             {@link ServerSocket} object.
	 */
	public TrackerServerManager(ThreadGroup group, String name, int port, String database_path) throws IOException {
		super(group, name, port);

		this.database = new TrackerDatabase(database_path);

	}

	/**
	 * Allocates a new TrackerServerManager object that is going to listen to a
	 * random port. Use the {@link ServerChannelManager#getSocketAddress
	 * getSocketAddress()} method to get the required information to remotely
	 * access the server.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this thread belongs to.
	 * @param name
	 *            The name of this Thread.
	 * @param database_path
	 *            The path of the database that the {@link TrackerServerChannel}
	 *            objects created by this manager are going to communicate with.
	 * @throws IOException
	 *             IOException If an error occurs during the allocation of the
	 *             {@link ServerSocket} object.
	 */
	public TrackerServerManager(ThreadGroup group, String name, String database_path) throws IOException {
		this(group, name, 0, database_path);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.ServerChannelManager#close()
	 */
	@Override
	public void close() throws IOException {

		super.close();
		this.database.close();

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.ServerChannelManager#
	 * newServerChannel(java. lang.ThreadGroup, java.lang.String,
	 * java.net.Socket)
	 */
	@Override
	protected TrackerServerChannel newServerChannel(ThreadGroup group, String name, Socket socket) throws IOException {

		return new TrackerServerChannel(group, name, socket, this.database, this.session_manager);
	}

}
