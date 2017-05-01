package p2p.common.stubs.connection.message;

import java.io.Serializable;

import p2p.common.stubs.connection.exceptions.FailedRequestException;

/**
 * A Reply is a {@link Message} object that is sent as reply to a
 * previous request. The type of the reply indicates if the sender
 * completed the request successfully.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <D>
 *        The type of the data the reply message contains.
 */
public final class Reply<D extends Serializable> extends Message<D> {
	
	/**
	 * A Reply.Type enumeration indicates the type of a previous
	 * message's reply.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Type {
		/**
		 * Indicates a successful reply.
		 */
		Success,
		/**
		 * Indicates a failure to comply with the previous request.
		 */
		Failure;
	}
	
	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = -2029604022338319450L;
	
	/**
	 * Returns a common failure reply.
	 *
	 * @return A Reply with type Failure and with no data.
	 */
	public static Reply<Boolean> getSimpleFailureMessage() {
		
		return new Reply<>(Type.Failure, null);
	}
	
	/**
	 * Returns a common success reply.
	 *
	 * @return A Reply with type Success and with no data.
	 */
	public static Reply<Boolean> getSimpleSuccessMessage() {
		
		return new Reply<>(Type.Success, null);
	}
	
	/**
	 * Tries to treat the provided object as a Reply object and
	 * retrieve the contained data. If the replie's type is Failure
	 * then a {@link FailedRequestException} is raised.
	 *
	 * @param <D>
	 *        The type of the expected data.
	 * @param object
	 *        The object that should be treated as the reply.
	 * @param expected_type
	 *        The class of the expected type.
	 * @return The data contained in the reply.
	 * @throws ClassCastException
	 *         If one of the casts is not possible.
	 * @throws FailedRequestException
	 *         If the replie's type is Failure.
	 */
	public static <D extends Serializable> D getValidatedData(Object object, Class<D> expected_type)
	        throws ClassCastException, FailedRequestException {
		
		Reply<?> reply = Reply.class.cast(object);
		if (reply.getType() == Type.Failure) throw new FailedRequestException();
		
		return expected_type.cast(reply.getData());
		
	}
	
	private final Type type;
	
	/**
	 * Allocates a new Reply object.
	 *
	 * @param type
	 *        The type of the reply.
	 * @param data
	 *        The data of the reply.
	 */
	public Reply(Type type, D data) {
		
		super(data);
		
		this.type = type;
	}
	
	/**
	 * @return The replie's type.
	 */
	public Type getType() {
		
		return this.type;
	}
	
}
