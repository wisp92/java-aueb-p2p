package p2p.components.common;

import java.io.Serializable;

/**
 * A Pair object implements a tuple of pairs. This class is very useful to send
 * abstract data structures through messages to the channel. Is also possible,
 * but not very efficient, to combine more than two objects by using nested
 * pairs.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @param <X>
 *            The type of the first object.
 * @param <Y>
 *            The type of the second object.
 */
public class Pair<X extends Serializable, Y extends Serializable> implements Serializable {

	/**
	 * The serialVersionID required by the {@link Serializable} interface to
	 * ensure the integrity of the object during a serialization and
	 * deserialization process.
	 */
	private static final long serialVersionUID = 1263196367258795004L;

	private final X	first;
	private final Y	second;

	/**
	 * Copy constructor of the Pair class.
	 *
	 * @param other
	 *            The Pair object to be copied.
	 */
	public Pair(final Pair<X, Y> other) {
		this(other.getFirst(), other.getSecond());
	}

	/**
	 * Allocates anew pair object.
	 *
	 * @param first
	 *            The first object.
	 * @param second
	 *            The second object.
	 */
	public Pair(final X first, final Y second) {

		this.first = first;
		this.second = second;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object object) {

		if (this == object) return true;
		if (object == null) return false;

		if (!Pair.class.isAssignableFrom(object.getClass())) return false;
		final Pair<?, ?> other = Pair.class.cast(object);
		final Object other_first = other.getFirst();
		final Object other_second = other.getSecond();

		if (this.first == null) {
			if (other_first != null) return false;
		}
		else if (!this.first.equals(other_first)) return false;

		if (this.second == null) {
			if (other_second != null) return false;
		}
		else if (!this.second.equals(other_second)) return false;

		return true;

	}

	/**
	 * Returns the first object. The element is backed by the database, so if
	 * the type X is mutable the changes to the object are going to reflect to
	 * the pair.
	 *
	 * @return The first object.
	 */
	public final X getFirst() {

		return this.first;
	}

	/**
	 * Returns the second object. The element is backed by the database, so if
	 * the type Y is mutable the changes to the object are going to reflect to
	 * the pair.
	 *
	 * @return The second object.
	 */
	public final Y getSecond() {

		return this.second;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {

		final int prime = 31;
		int result = 1;
		result = (prime * result) + ((this.first == null) ? 0 : this.first.hashCode());
		result = (prime * result) + ((this.second == null) ? 0 : this.second.hashCode());
		return result;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		return String.format("(%s, %s)", this.first.toString(), this.second.toString());
	}

}
