package p2p.components.communication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import p2p.components.common.Pair;
import p2p.components.communication.messages.Message;
import p2p.utilities.LoggerManager;

/**
 * A Channel object represents is a single two-way connection. Every channel is
 * associated with a local socket that is binded to a remote one. Through the
 * use of the socket the channel is able send and receive network traffic. A
 * channel is implemented through the use of a thread for its end-points to be
 * able to communicate asynchronously.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class Channel extends CloseableThread {
	
	public static final long default_waiting_time_for_response = 1000;
	
	/**
	 * Sends a check alive to the socket and return its response time.
	 *
	 * @param socket_address
	 *            The socket address to be checked.
	 * @return The response time of the socket address.
	 */
	public static final Long getResponseTime(final InetSocketAddress socket_address) {
		
		return Channel.getResponseTime(socket_address, Channel.default_waiting_time_for_response);
	}
	
	/**
	 * Sends a check alive to the socket and return its response time.
	 *
	 * @param socket_address
	 *            The socket address to be checked.
	 * @param max_waiting_time
	 *            The maximum time to wait for a reply before failing.
	 * @return The response time of the socket address.
	 */
	public static final Long getResponseTime(final InetSocketAddress socket_address, final long max_waiting_time) {
		
		final Thread current_thread = Thread.currentThread();
		final ThreadGroup group = new ThreadGroup(current_thread.getThreadGroup(),
		        String.format("%s.CheckAliveClients", current_thread.getName())); //$NON-NLS-1$
		
		return Channel.getResponseTime(group, String.format("%s.CheckAlive", current_thread.getName()), //$NON-NLS-1$
		        socket_address, max_waiting_time);
	}
	
	/**
	 * Sends check alive request to all provided address in parallel and return
	 * their response times sorted.
	 *
	 * @param address_batch
	 *            A set of InetSocketAddress objects to be checked.
	 * @return A map of InetSocketAddres, response time pairs.
	 */
	public static final List<Pair<InetSocketAddress, Long>> getResponseTime(
	        final Set<InetSocketAddress> address_batch) {
		
		return Channel.getResponseTime(address_batch, Channel.default_waiting_time_for_response);
	}
	
	/**
	 * Sends check alive request to all provided address in parallel and return
	 * their response times sorted.
	 *
	 * @param address_batch
	 *            A set of InetSocketAddress objects to be checked.
	 * @param max_waiting_time
	 *            The maximum time to wait for a reply before failing.
	 * @return A map of InetSocketAddres, response time pairs.
	 */
	public static final List<Pair<InetSocketAddress, Long>> getResponseTime(final Set<InetSocketAddress> address_batch,
	        final long max_waiting_time) {
		
		final Thread current_thread = Thread.currentThread();
		final ThreadGroup group = new ThreadGroup(current_thread.getThreadGroup(),
		        String.format("%s.CheckAliveClients", current_thread.getName())); //$NON-NLS-1$
		final PrimitiveIterator.OfInt it = IntStream.range(0, address_batch.size()).iterator();
		
		try {
			
			/*
			 * Enumerate socket addresses before calculating their response time
			 * and sort them.
			 */
			Map<InetSocketAddress, Integer> enumerated_addresses = address_batch.stream()
			        .collect(Collectors.toMap(x -> x, x -> it.next()));
			System.out.println(enumerated_addresses);
			Map<InetSocketAddress, Long> unordered_response_times = enumerated_addresses.entrySet().parallelStream()
			        .collect(Collectors.toMap(x -> x.getKey(),
			                x -> Channel.getResponseTime(group,
			                        String.format("%s.CheckAlive-%d", current_thread.getName(), //$NON-NLS-1$
			                                x.getValue()),
			                        x.getKey(), max_waiting_time)));
			System.out.println(unordered_response_times);
			List<Pair<InetSocketAddress, Long>> ordered_response_times = unordered_response_times.entrySet()
			        .parallelStream().filter(x -> x.getValue() != null)
			        .sorted((x, y) -> x.getValue().compareTo(y.getValue()))
			        .map(x -> new Pair<>(x.getKey(), x.getValue())).collect(Collectors.toList());
			System.out.println(ordered_response_times);
			
			return ordered_response_times;
			
		} finally {
			CloseableThread.interrupt(group);
		}
		
	}
	
	/**
	 * Try to send a check alive request to the specified socket.
	 *
	 * @param group
	 *            The {@link ThreadGroup ThreadGroup} object that this channel
	 *            belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            The {@link InetSocketAddress SocketDescription} of the
	 *            tracker's socket.
	 * @param max_waiting_time
	 *            The maximum time to wait for a reply before failing.
	 * @return The response time of the server or null in case of a failure. If
	 *         the return time is bigger than the maximum waiting time specified
	 *         then a timeout has occurred.
	 */
	public static final Long getResponseTime(final ThreadGroup group, final String name,
	        final InetSocketAddress socket_address, final long max_waiting_time) {
		
		try (CheckAliveClient client_channel = new CheckAliveClient(group, name, socket_address)) { // $NON-NLS-1$
			
			final long current_time = System.currentTimeMillis();
			client_channel.start();
			client_channel.join(max_waiting_time);
			final long response_time = System.currentTimeMillis() - current_time;
			
			if (client_channel.getStatus() == ClientChannel.Status.SUCCESSFULL) {
				
				LoggerManager.tracedLog(Level.FINE, String.format("The server <%s> responded in %d milliseconds.", //$NON-NLS-1$
				        socket_address.toString(), response_time));
				
				return response_time;
				
			}
			
			LoggerManager.tracedLog(Level.FINE, String.format("The server <%s> failed to respond in %d milliseconds.", //$NON-NLS-1$
			        socket_address.toString(), max_waiting_time));
			
			return max_waiting_time + 1;
			
		} catch (@SuppressWarnings("unused") final IOException ex) {
			
			LoggerManager.tracedLog(Level.FINE, String.format("The server <%s> is probably down.", //$NON-NLS-1$
			        socket_address.toString(), max_waiting_time));
			
		} catch (@SuppressWarnings("unused") final InterruptedException ex) {
			
			LoggerManager.tracedLog(Level.WARNING, "The check alive request was interrupted."); //$NON-NLS-1$
			
		}
		
		return null;
		
	}
	
	/**
	 * The local socket associated with channel.
	 */
	protected final Socket socket;
	
	/**
	 * Allocates a new Channel object by binding a {@link Socket} object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket_address
	 *            A {@link InetSocketAddress} to which the socket is going to be
	 *            bind.
	 * @throws IOException
	 *             If an error occurs during the allocation of the local
	 *             {@link Socket} object.
	 */
	public Channel(final ThreadGroup group, final String name, final InetSocketAddress socket_address)
	        throws IOException {
		super(group, name);
		
		this.socket = new Socket(socket_address.getAddress(), socket_address.getPort());
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
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		if (!this.socket.isClosed()) {
			this.socket.close();
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
		
		return new ObjectInputStream(this.socket.getInputStream());
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
				
				super.writeObject(obj);
				super.flush();
			}
			
		};
		
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
				        String.format("A new communication started (%d active in group <%s>).", //$NON-NLS-1$
				                CloseableThread.countActive(this.getThreadGroup()), this.getThreadGroup().getName()));
				
				this.communicate();
			}
			
		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the communication.", ex); //$NON-NLS-1$
		} catch (final InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The communication was interrupted.", ex); //$NON-NLS-1$
		} finally {
			
			try {
				
				this.close();
				
				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The communication ended (approximately %d remaining in group <%s>).", //$NON-NLS-1$
				                CloseableThread.countActive(this.getThreadGroup()), this.getThreadGroup().getName()));
				
			} catch (final IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The channel could not be closed properly.", ex); //$NON-NLS-1$
			}
			
		}
		
	}
	
}
