package p2p.tracker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import p2p.common.structures.PeerContactInformation;
import p2p.common.structures.SharedFileDescription;

/**
 * A SessionManager object keeps information about the active peer
 * sessions indexed by the session id.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class SessionManager {
	
	/**
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	class SessionInformation {
		
		private final PeerContactInformation				 peer_contact_information;
		private final HashMap<String, SharedFileDescription> shared_file_descriptions;
		
		/**
		 *
		 */
		public SessionInformation(PeerContactInformation peer_contact_information,
		        HashMap<String, SharedFileDescription> shared_file_descriptions) {
			
			this.peer_contact_information = peer_contact_information;
			this.shared_file_descriptions = shared_file_descriptions;
			
		}

		public PeerContactInformation getPeerContactInformation() {
			
			return this.peer_contact_information;
		}

		public HashMap<String, SharedFileDescription> getSharedFileDescriptions() {
			
			return this.shared_file_descriptions;
		}
		
	}
	
	private final Random random_number_generator = new Random(System.currentTimeMillis());
	
	private final HashMap<Integer, SessionInformation> sessions_information	= new HashMap<>();
	private final HashMap<String, HashSet<Integer>>	   shared_file_sessions	= new HashMap<>();
	private final HashSet<String>					   users				= new HashSet<>();
	private final HashSet<Integer>					   locked				= new HashSet<>();
	
	/**
	 * Allocates a new SessionManager object.
	 */
	public SessionManager() {
		
	}
	
	public boolean addSession(int session_id, PeerContactInformation contact_information,
	        HashMap<String, SharedFileDescription> shared_file_descriptions) {
		
		if (this.locked.contains(session_id) || this.isUserActive(contact_information.getUsername())
		        || this.sessions_information.containsKey(session_id))
		    return false;
		
		this.sessions_information.put(session_id,
		        new SessionInformation(contact_information, shared_file_descriptions));
		
		shared_file_descriptions.keySet().parallelStream().forEach(x -> {
			
			if (this.shared_file_sessions.containsKey(x)) {
				this.shared_file_sessions.put(x, new HashSet<Integer>());
			}
			this.shared_file_sessions.get(x).add(session_id);
			
		});
		
		this.locked.remove(session_id);
		this.users.add(contact_information.getUsername());
		
		return true;
		
	}
	
	public Set<String> getFilenames(int session_id) {
		
		if (!this.sessions_information.containsKey(session_id)) return null;
		
		return this.sessions_information.get(session_id).getSharedFileDescriptions().keySet();
		
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
		
		if (this.sessions_information.size() >= max_reasonable_capacity) return null;
		
		for (int i = 0; i < max_tries_before_failure; i++) {
			
			int candidate = this.random_number_generator.nextInt();
			if (!this.locked.contains(candidate) && !this.sessions_information.containsKey(candidate)) return candidate;
			
		}
		
		return null;
		
	}

	public PeerContactInformation getPeerContactInformation(int session_id) {
		
		if (!this.sessions_information.containsKey(session_id)) return null;
		
		return new PeerContactInformation(this.sessions_information.get(session_id).getPeerContactInformation());
		
	}

	/**
	 * Returns the session id associated with the provided username if
	 * any exist. Since this method should only be called in rare
	 * cases that the user doesn't know his session id the complexity
	 * is O(N) over the number of sessions for existing usernames.
	 *
	 * @param username
	 *        The username of the peer.
	 * @return The session id associated with this peer.
	 */
	public Integer getSessionID(String username) {
		
		if (!this.isUserActive(username)) return null;
		
		return this.sessions_information.entrySet().parallelStream()
		        .filter(x -> x.getValue().getPeerContactInformation().getUsername().equals(username)).findAny().get()
		        .getKey();
		
	}
	
	public boolean isUserActive(String username) {
		
		return this.users.contains(username);
	}
	
	public boolean lockSessionID(Integer session_id) {

		return this.locked.add(session_id);
	}
	
	public boolean removeSession(int session_id) {
		
		if (!this.sessions_information.containsKey(session_id)) return false;
		
		SessionInformation session_information = this.sessions_information.remove(session_id);
		this.shared_file_sessions.values().parallelStream().forEach(x -> x.remove(session_id));
		this.users.remove(session_information.getPeerContactInformation().getUsername());
		
		return true;
		
	}
	
	public List<PeerContactInformation> searchFilename(String filename) {

		if (!this.shared_file_sessions.containsKey(filename)) return null;

		return this.shared_file_sessions.get(filename).parallelStream().map(x -> this.getPeerContactInformation(x))
		        .collect(Collectors.toList());
	}

	public boolean unlockSessionID(Integer session_id) {

		return this.locked.remove(session_id);
	}
	
}
