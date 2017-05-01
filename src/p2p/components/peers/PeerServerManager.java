package p2p.components.peers;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import p2p.components.communication.ServerChannelManager;

/**
 * A PeerServerManager is a {@link ServerChannelManager} object that
 * listens's for incoming connections from the peers or the tracker
 * and allocates a new {@link PeerServerChannel} object to handle each
 * one.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class PeerServerManager extends ServerChannelManager<PeerServerChannel> {

	private final File shared_directory;

	/**
	 * Allocates a new PeerServerManager object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
	 * @param port
	 *        The port number that the {@link ServerSocket} object is
	 *        going to listen to.
	 * @param shared_directory_path
	 *        The path to the shared directory.
	 * @throws IOException
	 *         I an error occurs during the allocation of the
	 *         {@link ServerSocket} object.
	 */
	public PeerServerManager(ThreadGroup group, String name, int port, String shared_directory_path)
	        throws IOException {
		super(group, name, port);

		this.shared_directory = new File(shared_directory_path);

	}

	/**
	 * Allocates a new PeerServerManager object that is going to
	 * listen to a random port. Use the
	 * {@link ServerChannelManager#getSocketDescription()
	 * getSocketDescription()} method to get the required information
	 * to remotely access the server.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
	 * @param shared_directory_path
	 *        The path to the shared directory.
	 * @throws IOException
	 *         I an error occurs during the allocation of the
	 *         {@link ServerSocket} object.
	 */
	public PeerServerManager(ThreadGroup group, String name, String shared_directory_path) throws IOException {
		this(group, name, 0, shared_directory_path);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.ServerChannelManager#close()
	 */
	@Override
	public void close() throws IOException {

		super.close();

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.ServerChannelManager#
	 * newServerChannel(java. lang.ThreadGroup, java.lang.String,
	 * java.net.Socket)
	 */
	@Override
	protected PeerServerChannel newServerChannel(ThreadGroup group, String name, Socket socket) throws IOException {

		return new PeerServerChannel(group, name, socket, this.shared_directory);
	}

}
