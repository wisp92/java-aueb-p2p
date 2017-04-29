package p2p.peer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

import p2p.common.stubs.connection.ServerChannelManager;

class PeerServerManager extends ServerChannelManager<PeerServerChannel> {

	private final File shared_directory;

	public PeerServerManager(ThreadGroup group, String name, int port, String shared_directory_path)
	        throws IOException {
		super(group, name, port);

		this.shared_directory = new File(shared_directory_path);

	}

	/**
	 * Allocates a new PeerServerManager object.
	 *
	 * @param group
	 * @param name
	 * @param shared_directory_path
	 * @throws IOException
	 */
	public PeerServerManager(ThreadGroup group, String name, String shared_directory_path) throws IOException {
		this(group, name, 0, shared_directory_path);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.stubs.connection.ServerChannelManager#close()
	 */
	@Override
	public void close() throws IOException {

		super.close();

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.stubs.connection.ServerChannelManager#
	 * newServerChannel(java. lang.ThreadGroup, java.lang.String,
	 * java.net.Socket)
	 */
	@Override
	protected PeerServerChannel newServerChannel(ThreadGroup group, String name, Socket socket) throws IOException {

		return new PeerServerChannel(group, name, socket, this.shared_directory);
	}

}
