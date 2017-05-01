package p2p.common.structures;

import java.io.Serializable;

/**
 * A Credentials object is a structure that keeps information
 * necessary for a peer to authenticate to a tracker.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Credentials implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable}
	 * interface to ensure the integrity of the object during a
	 * serialization and deserialization process.
	 */
	private static final long serialVersionUID = -7183891689483965682L;

	private final String username;
	private final String password;

	/**
	 * Copy constructor of the Credentials object.
	 * 
	 * @param object
	 *        The object to be copied.
	 */
	public Credentials(Credentials object) {
		this(object.getUsername(), object.getPassword());

	}

	/**
	 * Allocates a new Credentials object.
	 *
	 * @param username
	 *        The user's username.
	 * @param password
	 *        The user's password.
	 */
	public Credentials(String username, String password) {

		this.username = username;
		this.password = password != null ? password : ""; //$NON-NLS-1$

	}

	/**
	 * @return The user's password.
	 */
	public String getPassword() {

		return this.password;
	}

	/**
	 * @return The user's username.
	 */
	public String getUsername() {

		return this.username;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return String.format("%s:%s", this.getUsername(), this.getPassword()); //$NON-NLS-1$
	}

}
