package p2p.components.communication.messages;

import java.io.Serializable;

/**
 * A Message is a serializable object that is used by a channel to
 * send data from the local to the remote socket.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <D>
 *        The type of the data the message contains.
 */
public class Message<D extends Serializable> implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 6253756439347867891L;

	/**
	 * Tries to treat the provided object as a Message object and
	 * retrieve its data casted in the expected type.
	 *
	 * @param <D>
	 *        The type of the expected data.
	 * @param object
	 *        The object that should be treated as the message.
	 * @param expected_type
	 *        The class of the the expected type.
	 * @return The data of the message casted in the expected type.
	 * @throws ClassCastException
	 *         If one of the casts is not possible.
	 */
	public static <D extends Serializable> D getData(Object object, Class<D> expected_type) throws ClassCastException {

		return expected_type.cast(Message.class.cast(object).getData());
	}

	private final D data;

	/**
	 * Allocates a new Message object. The data remain <b>mutable</b>
	 * after the construction. The caller is responsible to pass a
	 * copy of the data to ensure their integrity.
	 *
	 * @param data
	 *        The data this message is going to contain.
	 */
	public Message(D data) {

		this.data = data;
	}

	/**
	 * Returns the contained data as a <b>mutable</b> object. The
	 * caller is responsible for making a copy the of the data to
	 * ensure their integrity.
	 *
	 * @return The message's data.
	 */
	public D getData() {

		return this.data;
	}

}
