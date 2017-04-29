package p2p.tracker;

import java.util.HashMap;
import java.util.Random;

import p2p.common.structures.PeerDescription;

/**
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SessionManager {

	private final Random random_number_generator = new Random(System.currentTimeMillis());

	private final HashMap<Integer, PeerDescription> sessions = new HashMap<>();

	/**
	 * Allocates a new SessionManager object.
	 */
	public SessionManager() {

	}

	/**
	 * Tries to generate a unique random id using the RNG. If the
	 * generation fails to produce a unique id then the method
	 * considers that no available exist at the moment. The caller
	 * should call this method later in such a case. Although an id
	 * might still exist, It's better to wait some time anyway in
	 * order for some ids to be released.
	 *
	 * @return An unique ID or null if a conflict was detected.
	 */
	public final Integer getGeneratedID() {

		/*
		 * The maximum reasonable capacity equals to half the maximum
		 * capacity of a map. Its the point where chance that a
		 * failure happens are balanced.
		 */
		final int max_reasonable_capacity = Integer.MAX_VALUE / 2;
		/*
		 * The maximum number of tries before the generator fails
		 * equals to N/ln(2) where 1/N is the probability of a failure
		 * in max reasonable capacity. 10 equals to 0.001 probability
		 * of a failure per call.
		 */
		final int max_tries_before_failure = 10;

		if (this.sessions.size() >= max_reasonable_capacity) return null;

		for (int i = 0; i < max_tries_before_failure; i++) {

			int candidate = this.random_number_generator.nextInt();
			if (!this.sessions.containsKey(candidate)) return candidate;

		}

		return null;

	}

}
