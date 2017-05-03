package p2p.components.communication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;

/**
 * A ServerChannel is a {@link Channel} object that is going to accept the
 * connection from a remote socket. The ServerChannel and the
 * {@link ClientChannel} objects can refer to the same logical connection
 * accessed from different sockets.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class ServerChannel extends Channel {

	/*
	 * The order of declaration is important for the 'in' and 'out' variables.
	 */

	/**
	 * The stream from which the channel is going to read.
	 */
	protected final ObjectInputStream  in  = this.getInputStream();
	/**
	 * The steam to which the channel is going to write.
	 */
	protected final ObjectOutputStream out = this.getOutputStream();

	private long last_active_time;

	/**
	 * Allocates a new ClientChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket
	 *            The {@link Socket} object associated with this channel. Should
	 *            already be binded by the {@link ServerSocket#accept()
	 *            accept()} method
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object's streams.
	 */
	public ServerChannel(final ThreadGroup group, final String name, final Socket socket) throws IOException {
		super(group, name, socket);

		this.heartbit();
	}

	public void clean(final int max_inactive_time) {

		if (this.isAlive() && !this.isInterrupted()
		        && ((System.currentTimeMillis() - this.last_active_time) > max_inactive_time)) {
			this.interrupt();
		}

	}

	@Override
	protected final void communicate() throws IOException, InterruptedException {

		try {

			/*
			 * The first message should always be a request or else a
			 * communication can not be defined. By identifying the request type
			 * the server determines the correct action.
			 */
			final Request<?> request = Request.class.cast(this.in.readObject());
			final Request.Type request_type = request.getType();

			switch (request_type) {
			case CHECK_ALIVE:

				this.out.writeObject(Reply.getSimpleSuccessMessage());
				break;

			default:

				this.communicate(request);
				break;

			}

		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		}

	}

	/**
	 * Continues the communication process for higher level options.
	 *
	 * @param request
	 *            The request to be processed.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object's streams.
	 */
	protected abstract void communicate(Request<?> request) throws IOException;

	public final void heartbit() {

		this.last_active_time = System.currentTimeMillis();
	}

}
