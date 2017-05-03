package p2p.components.trackers;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import p2p.components.common.FileDescription;
import p2p.components.common.Pair;

/**
 * A SessionManager object keeps information about the active peer sessions
 * indexed by the session id an complementary structures to support file
 * indexing.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SessionManager {
	
	private final Random random_number_generator = new Random(System.currentTimeMillis());
	
	private final HashMap<Integer, Pair<Pair<String, InetSocketAddress>, HashSet<FileDescription>>> sessions = new HashMap<>();
	
	private final HashMap<String, HashSet<Integer>>	file_sessions = new HashMap<>();
	private final HashSet<String>					users		  = new HashSet<>();
	private final HashSet<Integer>					locked		  = new HashSet<>();
	
	/**
	 * Allocates a new SessionManager object.
	 */
	public SessionManager() {
		// Empty constructor.
	}
	
	/**
	 * Adds a session with the specified session id, provided that the session
	 * id is not locked and the username of the contact information to be added
	 * is not already in use.
	 *
	 * @param session_id
	 *            The session id of the new session. Should be produced by the
	 *            {@link SessionManager#getGeneratedID getGeneratedID()} method.
	 * @param username
	 *            The username associated with the session.
	 * @param socket_address
	 *            The socket address of the peer associated with the session.
	 * @param files
	 *            Required information of the peer's shared files.
	 * @return True If the session was added successfully.
	 */
	public boolean addSession(final int session_id, final String username, final InetSocketAddress socket_address,
	        final Set<FileDescription> files) {
		
		if (this.isSessionIDLocked(session_id) || this.isUserActive(username) || this.isActiveSession(session_id))
		    return false;
		
		this.sessions.put(session_id, new Pair<>(new Pair<>(username, socket_address), new HashSet<>(files)));
		
		/*
		 * The set of sessions per file is also updated.
		 */
		files.parallelStream().map(x -> x.getFilename()).forEach(x -> {
			
			if (!this.file_sessions.containsKey(x)) {
				this.file_sessions.put(x, new HashSet<>());
			}
			
			this.file_sessions.get(x).add(session_id);
			
		});
		
		/*
		 * Finally the set of active usernames is also updated.
		 */
		this.users.add(username);
		
		return true;
		
	}
	
	/**
	 * Return a list of shared files that the peer of the specified session can
	 * provide.
	 *
	 * @param session_id
	 *            The session id of the peer.
	 * @return A list of filenames.
	 */
	public Set<String> getFilenames(final int session_id) {
		
		if (!this.isActiveSession(session_id)) return null;
		
		return this.sessions.get(session_id).getSecond().parallelStream().map(x -> x.getFilename())
		        .collect(Collectors.toSet());
		
	}
	
	/**
	 * Tries to generate a unique random id using the RNG. If the generation
	 * fails to produce a unique id then the method considers that no available
	 * exist at the moment. The caller should call this method later in such a
	 * case. Although an id might still exist, It's better to wait some time
	 * anyway in order for some ids to be released.
	 *
	 * @return An unique ID or null if a conflict was detected.
	 */
	public final Integer getGeneratedID() {
		
		/*
		 * The maximum reasonable capacity equals to half the maximum capacity
		 * of a map. Its the point where chance that a failure happens are
		 * balanced.
		 */
		final int max_reasonable_capacity = Integer.MAX_VALUE / 2;
		/*
		 * The maximum number of tries before the generator fails equals to
		 * N/ln(2) where 1/N is the probability of a failure in max reasonable
		 * capacity. 10 equals to 0.001 probability of a failure per call.
		 */
		final int max_tries_before_failure = 10;
		
		if (this.sessions.size() >= max_reasonable_capacity) return null;
		
		for (int i = 0; i < max_tries_before_failure; i++) {
			
			final int candidate = this.random_number_generator.nextInt();
			if (!this.locked.contains(candidate) && !this.sessions.containsKey(candidate)) return candidate;
			
		}
		
		return null;
		
	}
	
	/**
	 * Returns a copy of the peer's contact information associated with this
	 * session id.
	 *
	 * @param session_id
	 *            The session id of the peer.
	 * @return The contact information of the peer.
	 */
	public Pair<String, InetSocketAddress> getPeerInformation(final int session_id) {
		
		if (!this.isActiveSession(session_id)) return null;
		
		return new Pair<>(this.sessions.get(session_id).getFirst());
		
	}
	
	/**
	 * Returns the session id associated with the provided username if any
	 * exist. Since this method should only be called in rare cases that the
	 * user doesn't know his session id the complexity is O(N) over the number
	 * of sessions for existing usernames.
	 *
	 * @param username
	 *            The username of the peer.
	 * @return The session id associated with this peer.
	 */
	public Integer getSessionID(final String username) {
		
		if (!this.isUserActive(username)) return null;
		
		return this.sessions.entrySet().parallelStream()
		        .filter(x -> x.getValue().getFirst().getFirst().equals(username)).findAny().get().getKey();
		
	}
	
	/**
	 * @param session_id
	 *            The session id to be checked.
	 * @return True If a session with the specified id exists.
	 */
	public boolean isActiveSession(final int session_id) {
		
		return this.sessions.containsKey(session_id);
	}
	
	/**
	 * @param session_id
	 *            The session id to be checked.
	 * @return True if the session id locked.
	 */
	public boolean isSessionIDLocked(final int session_id) {
		
		return this.locked.contains(session_id);
	}
	
	/**
	 * @param username
	 *            The username to be checked.
	 * @return True If the user is associated with an active session.
	 */
	public boolean isUserActive(final String username) {
		
		return this.users.contains(username);
	}
	
	/**
	 * Tries to lock the specified if, provided that the id is not already
	 * active.
	 *
	 * @param session_id
	 *            The session id to be locked.
	 * @return If the session id was locked successfully.
	 */
	public boolean lockSessionID(final Integer session_id) {
		
		if (this.isActiveSession(session_id)) return false;
		
		return this.locked.add(session_id);
	}
	
	/**
	 * Removed a session with the specified id.
	 *
	 * @param session_id
	 *            The id of the session to be removed.
	 * @return True If the session was removed successfully.
	 */
	public boolean removeSession(final int session_id) {
		
		if (!this.isActiveSession(session_id)) return false;
		
		boolean removed = false;
		final Pair<Pair<String, InetSocketAddress>, HashSet<FileDescription>> removed_session = this.sessions
		        .remove(session_id);
		
		if (removed_session != null) {
			
			removed = removed_session.getSecond().parallelStream().map(x -> x.getFilename())
			        .map(x -> this.file_sessions.containsKey(x) ? (this.file_sessions.get(x).size() > 1
			                ? this.file_sessions.get(x).remove(session_id) : this.file_sessions.remove(x) != null)
			                : false)
			        .reduce(true, (x, y) -> x && y);
			removed &= this.users.remove(removed_session.getFirst().getFirst());
			
		}
		
		return removed;
		
	}
	
	/**
	 * Takes advantage of the complimentary structures and checks which peer's
	 * can provide the specified shared file.
	 *
	 * @param filename
	 *            The filename to be searched.
	 * @return A list contact information for each peer that can provide the
	 *         file.
	 */
	public List<Pair<String, InetSocketAddress>> searchFilename(final String filename) {
		
		if (!this.file_sessions.containsKey(filename)) return Collections.emptyList();
		
		return this.file_sessions.get(filename).parallelStream().map(x -> this.getPeerInformation(x))
		        .collect(Collectors.toList());
	}
	
	/**
	 * Unlocks a session id.
	 *
	 * @param session_id
	 *            The session id to be unlocked/
	 * @return True if the unlock process was successful.
	 */
	public boolean unlockSessionID(final int session_id) {
		
		return this.locked.remove(session_id);
	}
	
}
