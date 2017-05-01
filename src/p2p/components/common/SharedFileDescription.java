package p2p.components.common;

import java.io.File;
import java.io.Serializable;

// TODO Add a comparison based on the filename.

/**
 * A SharedFileDescription object keeps information about a specific
 * shared file. As information consider only what is required from the
 * tracker to determine the file, aka. the filename and metadata such
 * as the file's size.
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
