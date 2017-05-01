package p2p.common.stubs.connection;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A ClientChannel is a {@link Channel} object that is going to
 * initialize the connection with the remote socket. The
 * {@link ServerChannel} and the ClientChannel objects can refer to
 * the same logical connection accessed from different sockets.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class ClientChannel extends Channel {
	
	/*
	 * The order of declaration is important for the 'in' and 'out'
	 * variables.
	 */
	
	/**
	 * A Status enumeration indicates the current status of the
	 * request.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Status {
		/**
		 * Indicates that the status is unknown. Usually this means
		 * that the a response has yet to come from the
		 * {@link ServerChannel}.
		 */
		UNKNOWN,
		/**
		 * Indicates that the request completed successfully.
		 */
		SUCCESSFULL,
		/**
		 * Indicates that the request failed to complete.
		 */
		FAILED;
	}
	
	/**
	 * The steam to which the channel is going to write.
	 */
	protected final ObjectOutputStream out = this.getOutputStream();
	/**
	 * The stream from which the channel is going to read.
	 */
	protected final ObjectInputStream  in  = this.getInputStream();
	
	/**
	 * Holds the current status of the request. Should be updated by
	 * the {@link Channel#communicate communicate()} method.
	 */
	protected Status status;
	
	/**
	 * Allocates a new ClientChannel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket_address
	 *        The description of the {@link Socket} object associated
	 *        with this channel.
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object or its streams.
	 */
	public ClientChannel(ThreadGroup group, String name, InetSocketAddress socket_address) throws IOException {
		super(group, name, socket_address);

		this.status = Status.UNKNOWN;
	}
	
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
	 * @throws IOException
	 *         If an error occurs during the allocation of the
	 *         {@link Socket} object's streams.
	 */
	public ClientChannel(ThreadGroup group, String name, Socket socket) throws IOException {
		super(group, name, socket);
		
		this.status = Status.UNKNOWN;
	}
	
	/**
	 * @return The request's current status.
	 */
	public Status getStatus() {
		
		return this.status;
	}
	
}
