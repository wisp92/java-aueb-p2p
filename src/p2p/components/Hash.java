package p2p.components;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import p2p.utilities.LoggerManager;

/**
 * The Hash class provides a quick reference to some popular hash algorithms,
 * eg. SHA-1.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public final class Hash {

	/**
	 * A Hash#Algorithm enumeration indicates the algorithm that can be used for
	 * hash computation.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Algorithm {
		/**
		 * Indicates that computations are going to be made using the SHA-1
		 * algorithm.
		 */
		SHA1("SHA-1");

		private final String label;

		private Algorithm(final String label) {
			this.label = label;
		}

		/**
		 * @return The label of the enumeration. Used by a digest to determine
		 *         the type of the computation.
		 */
		public final String getLabel() {

			return this.label;
		}

	}

	/**
	 * Calculates the SHA-1 hash of the given plaintext.
	 *
	 * @param plaintext
	 *            The plaintext to be processed.
	 * @return The SHA-1 hash of the plaintext or null if the computation could
	 *         not be completed.
	 */
	public static final BigInteger getSHA1(final String plaintext) {

		return Hash.getHash(plaintext, Algorithm.SHA1);
	}

	/**
	 * Calculates the hash value of the given plaintext based on the provided
	 * algorithm.
	 *
	 * @param plaintext
	 *            The plaintext to be processed.
	 * @param algorithm
	 *            The algorithm that is going to used by the digest.
	 * @return The hash value of the plaintext or null if the computation could
	 *         not be completed.
	 */
	protected static final BigInteger getHash(final String plaintext, final Algorithm algorithm) {

		try {

			return new BigInteger(1,
			        MessageDigest.getInstance(algorithm.getLabel()).digest(plaintext.getBytes("UTF-8")));

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {

			/*
			 * An UnsupportedEncodingException should never occur because the
			 * encoding "UTF-8" is hardcoded.
			 */

			LoggerManager.tracedLog(Level.WARNING, "The hash value could not be calculated.", ex);

		}

		return null;

	}

}
