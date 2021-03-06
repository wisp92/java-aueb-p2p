package p2p.components.common;

import java.io.File;
import java.io.Serializable;

/**
 * A FileDescription object keeps information about a specific shared file. As
 * information is the minimum required by the tracker to identify the file, aka.
 * the filename and metadata such as the file's size.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class FileDescription implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable} interface to
	 * ensure the integrity of the object during a serialization and
	 * deserialization process.
	 */
	private static final long serialVersionUID = 2094746920609208735L;

	private final String filename;
	/*
	 * NOTE Right now this information is not required and used only for legacy
	 * reasons.
	 */
	private final long size;

	/**
	 * Allocates a new FileDescription object.
	 *
	 * @param file
	 *            The file associated with this description.
	 */
	public FileDescription(final File file) {

		this.filename = file.getName();
		this.size = file.length();

	}

	/**
	 * Copy constructor of the FileDescription object.
	 *
	 * @param other
	 *            The File object to be copied.
	 */
	public FileDescription(final FileDescription other) {

		this.filename = other.getFilename();
		this.size = other.getSize();

	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object object) {

		if (this == object) return true;
		if (object == null) return false;

		if (!(object instanceof FileDescription)) return false;
		final FileDescription other = (FileDescription) object;

		if (this.filename == null) {
			if (other.filename != null) return false;
		}
		else if (!this.filename.equals(other.filename)) return false;

		return true;
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

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.filename == null) ? 0 : this.filename.hashCode());
		return result;
	}

}
