package p2p.common.structures;

import java.io.File;
import java.io.Serializable;

/**
 * A SharedFileDescription object keeps the necessary information for
 * the tracker to determine if the a specific peer holds a shared
 * file.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SharedFileDescription implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = 2094746920609208735L;

	private final String filename;
	private final long	 size;

	/**
	 * Allocates a new SharedFileDescription object.
	 *
	 * @param file
	 *        The file associated with this description.
	 */
	public SharedFileDescription(File file) {

		this.filename = file.getName();
		this.size = file.length();

	}

	/**
	 * Copy constructor of the SharedFileDescription object.
	 *
	 * @param object
	 *        The object to be copied.
	 */
	public SharedFileDescription(SharedFileDescription object) {

		this.filename = object.getFilename();
		this.size = object.getSize();

	}

	/**
	 * @return The filename of the file.
	 */
	public String getFilename() {

		return this.filename;
	}

	/**
	 * @return The size of the file.
	 */
	public long getSize() {

		return this.size;
	}

}
