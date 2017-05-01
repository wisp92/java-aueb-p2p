package p2p.components.communication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Level;

import p2p.components.communication.messages.Message;
import p2p.utilities.LoggerManager;

/*
 * The Channel object is accessible only by the other classes in the
 * package p2p.components.communication.
 */

/**
 * A Channel object represents is a single two-way connection. Every
 * channel is associated with a local socket that is binded to a
 * remote one. Through the use of the socket the channel is able send
 * and receive network traffic. A channel is implemented through the
 * use of a thread for its end-points to be able to communicate
 * asynchronously.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
abstract class Channel extends CloseableThread {
	
	/**
	 * The local socket associated with channel.
	 */
	protected final Socket socket;
	
	/**
	 * Allocates a new Channel object by binding a {@link Socket}
	 * object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket_address
	 *        A {@link InetSocketAddress} to which the socket is going
	 *        to be bind.
	 * @throws IOException
	 *         If an error occurs during the allocation of the local
	 *         {@link Socket} object.
	 */
	public Channel(ThreadGroup group, String name, InetSocketAddress socket_address) throws IOException {
		super(group, name);
		
		this.socket = new Socket(socket_address.getAddress(), socket_address.getPort());
	}
	
	/**
	 * Allocates a new Channel object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this channel belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param socket
	 *        The local {@link Socket} object associated with this
	 *        channel.
	 */
	public Channel(ThreadGroup group, String name, Socket socket) {
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
	 * Implements a communication with the other end of the
	 * connection.
	 *
	 * @throws IOException
	 *         If an error occurs while reading from or writing to the
	 *         streams.
	 * @throws InterruptedException
	 *         If the execution of the thread is interrupted.
	 */
	protected abstract void communicate() throws IOException, InterruptedException;
	
	/**
	 * @return An {@link ObjectInputStream} object based on the local
	 *         socket's input stream.
	 * @throws IOException
	 *         If an error occurs during the allocation of the stream.
	 */
	protected final ObjectInputStream getInputStream() throws IOException {
		
		return new ObjectInputStream(this.socket.getInputStream());
	}
	
	/**
	 * @return An {@link ObjectOutputStream} object based on the
	 *         socket's output stream. The
	 *         {@link ObjectOutputStream#writeObject writeObject()}
	 *         method of the stream has been overrided to flush
	 *         immediately since its going to be used to send only
	 *         {@link Message} objects.
	 * @throws IOException
	 *         If an error occurs during the allocation of the stream.
	 */
	protected final ObjectOutputStream getOutputStream() throws IOException {
		
		return new ObjectOutputStream(this.socket.getOutputStream()) {
			
			/*
			 * (non-Javadoc)
			 * @see
			 * java.io.ObjectOutputStream#writeObjectOverride(java.
			 * lang.Object)
			 */
			@Override
			protected void writeObjectOverride(Object obj) throws IOException {
				
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
				
				LoggerManager.tracedLog(Level.FINE,
				        String.format("A new communication started (%d active in group <%s>).", //$NON-NLS-1$
				                CloseableThread.countActive(this.getThreadGroup()), this.getThreadGroup().getName()));
				
				this.communicate();
			}
			
		} catch (IOException ex) {
			LoggerManager.tracedLog(Level.SEVERE, "An IOException occurred during the communication.", ex); //$NON-NLS-1$
		} catch (InterruptedException ex) {
			LoggerManager.tracedLog(Level.WARNING, "The communication was interrupted.", ex); //$NON-NLS-1$
		} finally {
			
			try {
				
				this.close();
				
				LoggerManager.tracedLog(Level.FINE,
				        String.format("The communication ended (%d remaining in group <%s>).", //$NON-NLS-1$
				                CloseableThread.countActive(this.getThreadGroup()) - 1, this.getThreadGroup().getName()));
				
			} catch (IOException ex) {
				LoggerManager.tracedLog(Level.WARNING, "The channel could not be closed properly.", ex); //$NON-NLS-1$
			}
			
		}
		
	}
	
}
