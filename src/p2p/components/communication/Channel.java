package p2p.components.communication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import p2p.components.Configuration;
import p2p.components.common.Pair;
import p2p.components.communication.messages.Message;
import p2p.utilities.LoggerManager;

/**
 * A Channel object represents a direction of single two-way connection. Every
 * channel is associated with a local socket that is binded to a remote one.
 * Through the use of the socket the channel is able send and receive network
 * traffic. A channel is implemented through the use of a thread for its
 * end-points to be able to communicate asynchronously.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class Channel extends CloseableThread {

	/**
	 * The default amount of milliseconds to wait for a reply to the check alive
	 * request before considering the peer inactive.
	 */
	public static final long default_check_alive_threshold = Configuration.getDefault()
	        .getInteger("check_alive_threshold", 1000);

	/**
	 * Sends a check alive request to the socket and return its response time.
	 *
	 * @param socket_address
	 *            The socket address to be checked.
	 * @return The response time of the socket address.
	 */
	public static final long getResponseTime(final InetSocketAddress socket_address) {

		return Channel.getResponseTime(socket_address, Channel.default_check_alive_threshold);
	}

	/**
	 * Sends a check alive to the socket and return its response time.
	 *
	 * @param socket_address
	 *            The socket address to be checked.
	 * @param check_alive_threshold
	 *            The maximum amount of milliseconds to wait for a reply before
	 *            failing.
	 * @return The response time of the socket address.
	 */
	public static final long getResponseTime(final InetSocketAddress socket_address, final long check_alive_threshold) {

		final Thread current_thread = Thread.currentThread();
		final ThreadGroup group = new ThreadGroup(String.format("%s.CheckAliveClients", current_thread.getName()));

		return Channel.getResponseTime(group, String.format("%s.CheckAlive", current_thread.getName()), socket_address,
		        check_alive_threshold);
	}

	/**
	 * Sends parallel check alive requests to all socket addresses in the batch
	 * and return their response times in ascending order.
	 *
	 * @param socket_address_batch
	 *            A set of {@link InetSocketAddress} objects to be checked.
	 * @return A list of pairs consisting of the socket address and their
	 *         corresponding response times.
	 */
	public static final List<Pair<InetSocketAddress, Long>> getResponseTime(
	        final Set<InetSocketAddress> socket_address_batch) {

		return Channel.getResponseTime(socket_address_batch, Channel.default_check_alive_threshold);
	}

	/**
	 * Sends parallel check alive requests to all socket addresses in the batch
	 * and return their response times in ascending order.
	 *
	 * @param socket_address_batch
	 *            A set of {@link InetSocketAddress} objects to be checked.
	 * @param check_alive_threshold
	 *            The maximum amount of milliseconds to wait for a reply before
	 *            failing.
	 * @return A list of pairs consisting of the socket address and their
	 *         corresponding response times.
	 */
	public static final List<Pair<InetSocketAddress, Long>> getResponseTime(
	        final Set<InetSocketAddress> socket_address_batch, final long check_alive_threshold) {

		final Thread current_thread = Thread.currentThread();
		final ThreadGroup group = new ThreadGroup(String.format("%s.CheckAliveClients", current_thread.getName()));

		/*
		 * Create an integer iterator to enumerate the socket addresses.
		 */
		final PrimitiveIterator.OfInt it = IntStream.range(0, socket_address_batch.size()).iterator();

		try {

			/*
			 * Enumerate the socket address, send check alive requests to each
			 * one in parallel and calculate their response times and finally
			 * return the results in ascending order.
			 */

			return socket_address_batch.stream().collect(Collectors.toMap(x -> x, x -> it.next())).entrySet()
			        .parallelStream()
			        .collect(
			                Collectors
			                        .toMap(x -> x.getKey(),
			                                x -> new Long(Channel.getResponseTime(group,
			                                        String.format("%s.CheckAlive-%d", current_thread.getName(),
			                                                x.getValue()),
			                                        x.getKey(), check_alive_threshold))))
			        .entrySet().parallelStream().filter(x -> x.getValue().longValue() <= check_alive_threshold)
			        .sorted((x, y) -> x.getValue().compareTo(y.getValue()))
			        .map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());

		} finally {

			/*
			 * Interrupt any loose requests before returning.
			 */
			CloseableThread.interrupt(group);

		}

	}

	private static final long getResponseTime(final ThreadGroup group, final String name,
	        final InetSocketAddress socket_address, final long check_alive_threshold) {

		try (CheckAliveClient client_channel = new CheckAliveClient(group, name, socket_address)) { // $NON-NLS-1$

			/*
			 * Calculate the response time of the request.
			 */
			long current_time = System.currentTimeMillis();
			client_channel.start();
			client_channel.join(check_alive_threshold);
			current_time = System.currentTimeMillis() - current_time;
			final long response_time = current_time;

			if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {

				LoggerManager.tracedLog(Level.FINE, String.format("The server <%s> responded in %d milliseconds.",
				        socket_address.toString(), new Long(response_time)));

				return response_time;

			}
			else {

				LoggerManager.tracedLog(Level.FINE,
				        String.format("The server <%s> failed to respond in %d milliseconds.",
				                socket_address.toString(), new Long(check_alive_threshold)));

				/*
				 * In a time the response time at the best case just greater
				 * than the threshold.
				 */
				return check_alive_threshold + 1;

			}

		} catch (@SuppressWarnings("unused") final IOException ex) {

			LoggerManager.tracedLog(Level.FINE, String.format("The server <%s> is probably down.",
			        socket_address.toString(), new Long(check_alive_threshold)));

		} catch (@SuppressWarnings("unused") final InterruptedException ex) {

			LoggerManager.tracedLog(Level.WARNING, "The check alive request was interrupted.");

		}

		/*
		 * On error the response time is infinite.
		 */
		return Long.MAX_VALUE;

	}

	/**
	 * The local socket associated with channel.
	 */
	protected final Socket socket;

	private long last_active_time;

	/**
	 * Allocates a new Channel object by binding a remote {@link Socket} object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            A {@link InetSocketAddress} to which the socket is going to be
	 *            binded.
	 * @throws IOException
	 *             If an error occurs during the allocation of the local
	 *             {@link Socket} object.
	 */
	public Channel(final ThreadGroup group, final String name, final InetSocketAddress socket_address)
	        throws IOException {
		super(group, name);

		this.socket = new Socket(socket_address.getAddress(), socket_address.getPort());

		this.heartbit();

	}

	/**
	 * Allocates a new Channel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket
	 *            The local {@link Socket} object associated with this channel.
	 */
	public Channel(final ThreadGroup group, final String name, final Socket socket) {
		super(group, name);

		this.socket = socket;

		this.heartbit();

	}

	/**
	 * Interrupt the server if the maximum inactivity time has passed.
	 *
	 * @param max_inactivity_time
	 *            The maximum allowed inactivity time.
	 */
	public void clean(final int max_inactivity_time) {

		if (this.isAlive() && !this.isInterrupted()
		        && ((System.currentTimeMillis() - this.last_active_time) > max_inactivity_time)) {

			this.interrupt();

			LoggerManager.tracedLog(Level.WARNING,
			        String.format("The channel <%s> was stopped by a cleaner due to inactivity.", this.getName()));
		}

	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		if (!this.socket.isClosed()) {

			/*
			 * Close the socket resulting in closing both streams of the
			 * connection and so interrupting the client and the server with an
			 * InterruptedException and a SocketException respectively.
			 */
			this.socket.close();

		}

	}

	/**
	 * Updates the last active time of the server. Should be updated regularly
	 * avoid interruption by the cleaner.
	 */
	public final void heartbit() {

		this.last_active_time = System.currentTimeMillis();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		try {

			if (!this.socket.isClosed()) {

				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("A new communication started (%d active in group <%s>).",
				                new Integer(CloseableThread.countActive(this.getThreadGroup())),
				                this.getThreadGroup().getName()));

				this.communicate();

			}

		} catch (final SocketException | InterruptedException ex) {

			/*
			 * This exceptions indicate that the communication no either end was
			 * interrupted.
			 */

			LoggerManager.tracedLog(this, Level.WARNING, "The communication was interrupted.");

		} catch (final IOException ex) {

			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the communication.", ex);

		} finally {

			try {

				this.close();

				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The communication ended (approximately %d remaining in group <%s>).",
				                new Integer(CloseableThread.countActive(this.getThreadGroup())),
				                this.getThreadGroup().getName()));

			} catch (final IOException ex) {

				LoggerManager.tracedLog(this, Level.WARNING, "The channel could not be closed properly.", ex);

			}

		}

	}

	/**
	 * Implements a communication with the other end of the connection.
	 *
	 * @throws IOException
	 *             If an error occurs while reading from or writing to the
	 *             streams.
	 * @throws InterruptedException
	 *             If the execution of the thread is interrupted.
	 */
	protected abstract void communicate() throws IOException, InterruptedException;

	/**
	 * @return An {@link ObjectInputStream} object based on the local socket's
	 *         input stream.
	 * @throws IOException
	 *             If an error occurs during the allocation of the stream.
	 */
	protected final ObjectInputStream getInputStream() throws IOException {

		return new ObjectInputStream(this.socket.getInputStream()) {

			/*
			 * (non-Javadoc)
			 * @see java.io.ObjectOInputStream#readObjectOverride(java.
			 * lang.Object)
			 */
			@Override
			protected final Object readObjectOverride() throws IOException, ClassNotFoundException {

				final Object object = super.readObjectOverride();
				Channel.this.heartbit();

				return object;

			}

		};
	}

	/**
	 * @return An {@link ObjectOutputStream} object based on the socket's output
	 *         stream. The {@link ObjectOutputStream#writeObject writeObject()}
	 *         method of the stream has been overrided to flush immediately
	 *         since its going to be used to send only {@link Message} objects.
	 * @throws IOException
	 *             If an error occurs during the allocation of the stream.
	 */
	protected final ObjectOutputStream getOutputStream() throws IOException {

		return new ObjectOutputStream(this.socket.getOutputStream()) {

			/*
			 * (non-Javadoc)
			 * @see java.io.ObjectOutputStream#writeObjectOverride(java.
			 * lang.Object)
			 */
			@Override
			protected void writeObjectOverride(final Object obj) throws IOException {

				super.writeObjectOverride(obj);
				Channel.this.heartbit();

				super.flush();
			}

		};

	}

}
