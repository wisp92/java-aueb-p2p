package p2p.common.structures;

import java.io.Serializable;

/**
 * A PeerDescription object is a structure that keeps the required
 * information to describe peer's server socket and username.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerDescription extends SocketDescription {

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 4131872014857893328L;

	final private String username;

	/**
	 * Allocates a new PeerDescription object.
	 *
	 * @param host
	 *        The socket's host address.
	 * @param port
	 *        The socket's port number.
	 * @param username
	 *        The username associated currently with the logged in
	 *        peer.
	 */
	public PeerDescription(String host, int port, String username) {
		super(host, port);

		this.username = username;
	}

	/**
	 * @return The username associated with the peer's login.
	 */
	public String getUsername() {

		return this.username;
	}

}
