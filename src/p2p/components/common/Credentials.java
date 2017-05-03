package p2p.components.common;

import java.io.Serializable;

/**
 * A Credentials object is a structure that keeps information necessary for a
 * peer to authenticate to a tracker.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Credentials extends Pair<String, String> {

	/**
	 * The serialVersionID required by the {@link Serializable} interface to
	 * ensure the integrity of the object during a serialization and
	 * deserialization process.
	 */
	private static final long serialVersionUID = -7183891689483965682L;

	/**
	 * Copy constructor of the Credentials object.
	 *
	 * @param object
	 *            The object to be copied.
	 */
	public Credentials(final Credentials object) {
		this(object.getUsername(), object.getPassword());

	}

	/**
	 * Allocates a new Credentials object.
	 *
	 * @param username
	 *            The user's username.
	 * @param password
	 *            The user's password. An empty password can be used, but
	 *            recommended.
	 */
	public Credentials(final String username, final String password) {
		super(username, password != null ? password : ""); //$NON-NLS-1$
	}

	/**
	 * @return The user's password.
	 */
	public String getPassword() {

		return this.getSecond();
	}

	/**
	 * @return The user's username.
	 */
	public String getUsername() {

		return this.getFirst();
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
