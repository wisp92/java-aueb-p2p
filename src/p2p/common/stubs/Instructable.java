package p2p.common.stubs;

import java.util.EnumSet;
import java.util.NoSuchElementException;

/**
 * The Instructable interface indicates that the class represents
 * instructables that can be looked up using their text field.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public interface Instructable {

	/**
	 * Searches the enumeration for a Instructable object that is
	 * associated with the given text.
	 *
	 * @param <T>
	 *        The type of the Instructable.
	 * @param type
	 *        The Instructable's class.
	 * @param text
	 *        The text associated with the requested Instructable.
	 * @return The Instructable object that can be associated with the
	 *         given text.
	 * @throws NoSuchElementException
	 *         If no Instructable object can be associated with the
	 *         given text.
	 */
	public static <T extends Enum<T> & Instructable> T find(Class<T> type, String text) throws NoSuchElementException {

		return EnumSet.allOf(type).stream().parallel().filter(x -> x.getText().equalsIgnoreCase(text)).findAny().get();
	}

	/**
	 * @return The text associated with the Instructable.
	 */
	public String getText();

}
