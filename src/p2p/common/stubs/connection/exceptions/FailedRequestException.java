package p2p.common.stubs.connection.exceptions;

import java.io.Serializable;

/**
 * A FailedRequestException indicates that a message of type Failure
 * was received from the channel.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class FailedRequestException extends Exception {
	
	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 8162583015988451467L;
	
	/**
	 * Allocates a new FailedRequestException object.
	 */
	public FailedRequestException() {
		super();
	}
	
	/**
	 * Allocates a new FailedRequestException object.
	 *
	 * @param message
	 *        The detail message of the exception.
	 */
	public FailedRequestException(String message) {
		super(message);
	}
	
}
