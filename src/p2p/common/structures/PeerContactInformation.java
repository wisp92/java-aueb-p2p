package p2p.common.structures;

import java.io.Serializable;
import java.net.InetSocketAddress;

/**
 * A PeerContactInformation object is used to transfer the required
 * information to contact the peer's server from another peer.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerContactInformation implements Serializable {
	
	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 4698007606082236864L;
	
	private final InetSocketAddress	socket_address;
	private final String			username;
	
	/**
	 * Allocates a new PeerContactInformation object.
	 *
	 * @param socket_address
	 *        The {@link InetSocketAddress} object that describes the
	 *        server socket of the peer.
	 * @param username
	 *        The username of the peer.
	 */
	public PeerContactInformation(InetSocketAddress socket_address, String username) {
		
		this.socket_address = socket_address;
		this.username = username;
		
	}
	
	/**
	 * Copy constructor of the PeerContactInformation object.
	 *
	 * @param object
	 *        The object to be copied.
	 */
	public PeerContactInformation(PeerContactInformation object) {
		this(object.getSocketAddress(), object.getUsername());
	}
	
	/**
	 * Allocates a new PeerContactInformation object.
	 *
	 * @param host
	 *        The peer's host address.
	 * @param port
	 *        The peer's port number that the server is listening to.
	 * @param username
	 *        The username of the peer.
	 */
	public PeerContactInformation(String host, int port, String username) {
		this(new InetSocketAddress(host, port), username);
	}
	
	/**
	 * @return The host address of the socket.
	 */
	public String getHostAddress() {
		
		return this.socket_address.getAddress().getHostAddress();
	}
	
	/**
	 * @return The port of the socket.
	 */
	public int getPort() {
		
		return this.socket_address.getPort();
	}
	
	/**
	 * @return A copy of the socket's address.
	 */
	public InetSocketAddress getSocketAddress() {
		
		return new InetSocketAddress(this.getHostAddress(), this.getPort());
	}
	
	/**
	 * @return The username of the peer.
	 */
	public String getUsername() {
		
		return this.username;
	}
	
}
