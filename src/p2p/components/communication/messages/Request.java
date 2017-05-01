package p2p.components.communication.messages;

import java.io.Serializable;

/**
 * A Request is a {@link Message} object that indicates the operation
 * that the client requests from the server. Every request has a type
 * in order for the server to identify the requested operation.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <D>
 *        The type of the data the request message contains.
 */
public final class Request<D extends Serializable> extends Message<D> {

	/**
	 * A Request.Type enumeration indicates the type of the request.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Type {
		/**
		 * Indicates a registration request.
		 */
		REGISTER,
		/**
		 * Indicates a login request.
		 */
		LOGIN,
		/**
		 * Indicates a logout request.
		 */
		LOGOUT;
	}

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 2756295932040700638L;

	private final Type type;

	/**
	 * Allocates a new Reply object.
	 *
	 * @param type
	 *        The type of the request.
	 * @param data
	 *        The data of the request.
	 */
	public Request(Type type, D data) {

		super(data);

		this.type = type;
	}

	/**
	 * @return The request's type.
	 */
	public Type getType() {

		return this.type;
	}

}
