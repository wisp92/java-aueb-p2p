package p2p.common.structures;

import java.io.Serializable;

/**
 * A SocketDescription object is a structure that keeps the required
 * information to describe a socket.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SocketDescription implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = -2383384132134032490L;

	private final String host;
	private final int	 port;

	/**
	 * Allocates a new SocketDescription object.
	 *
	 * @param host
	 *        The socket's host address.
	 * @param port
	 *        The socket's port number.
	 */
	public SocketDescription(String host, int port) {

		this.host = host;
		this.port = port;

	}

	/**
	 * @return The socket's host address.
	 */
	public String getHost() {

		return this.host;
	}

	/**
	 * @return The socket's port number.
	 */
	public int getPort() {

		return this.port;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return String.format("%s:%d", this.getHost(), this.getPort()); //$NON-NLS-1$
	}

}
