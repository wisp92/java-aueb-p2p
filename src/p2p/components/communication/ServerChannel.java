package p2p.components.communication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A ServerChannel is a {@link Channel} object that is going to accept
 * the connection from a remote socket. The ServerChannel and the
 * {@link ClientChannel} objects can refer to the same logical
 * connection accessed from different sockets.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class ServerChannel extends Channel {
	
	/*
	 * The order of declaration is important for the 'in' and 'out'
	 * variables.
	 */
	
	/**
	 * The stream from which the channel is going to read.
	 */
	protected final ObjectInputStream  in  = this.getInputStream();
	/**
	 * The steam to which the channel is going to write.
	 */
	protected final ObjectOutputStream out = this.getOutputStream();
	
	/**
	 * Allocates a new ClientChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket
	 *        The {@link Socket} object associated with this channel.
	 *        Should already be binded by the
	 *        {@link ServerSocket#accept() accept()} method
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object's streams.
	 */
	public ServerChannel(ThreadGroup group, String name, Socket socket) throws IOException {
		super(group, name, socket);
	}
	
}
