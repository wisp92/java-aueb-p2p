package p2p.common;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The Hash class provides a quick reference to some popular hash
 * algorithms, eg. SHA-1.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public final class Hash {

	/**
	 * A Hash.Algorithm enumeration indicates the algorithm to be used
	 * for computations
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Algorithm {
		/**
		 * Indicates that the SHA-1 algorithm is going to be used.
		 */
		SHA1("SHA-1"); //$NON-NLS-1$

		private final String label;

		private Algorithm(String label) {

			this.label = label;
		}

		/**
		 * @return The label of the Algorithm enumeration. Used by the
		 *         digest to determine the type of the algorithm.
		 */
		public String getLabel() {

			return this.label;
		}

	}

	/**
	 * Calculates the hash value of the given plaintext based on the
	 * provided algorithm.
	 *
	 * @param plaintext
	 *        The plaintext to be hashed.
	 * @param algorithm
	 *        The algorithm used by the digest.
	 * @return The hash value of the plaintext.
	 */
	protected static final BigInteger getHash(String plaintext, Algorithm algorithm) {

		try {

			return new BigInteger(1,
			        MessageDigest.getInstance(algorithm.getLabel()).digest(plaintext.getBytes("UTF-8"))); //$NON-NLS-1$

		} catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {

			LoggerManager.getDefault().getLogger(Hash.class.getName()).warning(ex.toString());
		}

		return null;

	}

	/**
	 * Calculates the SHA-1 hash value of the given plaintext.
	 *
	 * @param plaintext
	 *        The plaintext to be hashed.
	 * @return The SHA-1 hash value of the plaintext.
	 */
	public static final BigInteger getSHA1(String plaintext) {

		return Hash.getHash(plaintext, Algorithm.SHA1);
	}

}
