package p2p.components.trackers;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.Hash;
import p2p.components.common.Credentials;
import p2p.components.common.FileDescription;
import p2p.components.common.Pair;
import p2p.components.communication.ServerChannel;
import p2p.components.communication.messages.Message;
import p2p.components.communication.messages.Reply;
import p2p.components.communication.messages.Request;
import p2p.utilities.LoggerManager;

/**
 * A TrackerServerChannel object handles an incoming connection from one of the
 * tracker's peers. Specifies all the capabilities of the server in its
 * communication() method.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TrackerServerChannel extends ServerChannel {
	
	/**
	 * If a remote host server policy is applied then the peer can indicate a
	 * remote host as a server manager. Not recommended because a peer can take
	 * advantage of the policy and receive no Incoming traffic.
	 */
	public static final boolean PEER_SERVER_REMOTE_HOST_POLICY = false;
	
	private static <D extends Serializable> Pair<Integer, D> decodeSessionRequest(final Request<?> request,
	        final Class<D> expected_type) {
		
		final Pair<?, ?> pair = Pair.class.cast(request.getData());
		return new Pair<>(Integer.class.cast(pair.getFirst()), expected_type.cast(pair.getSecond()));
		
	}
	
	private final TrackerDatabase database;
	
	private final SessionManager session_manager;
	
	/**
	 * Allocates a new TrackerServerChannel object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this channel belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param socket
	 *            The {@link Socket} object associated with this channel.
	 * @param database
	 *            The database that the user's information are stored.
	 * @param session_manager
	 *            The SessionManager object that is going to store information
	 *            about the current session.
	 * @throws IOException
	 *             If an error occurs during the allocation of the
	 *             {@link Socket} object.
	 */
	public TrackerServerChannel(final ThreadGroup group, final String name, final Socket socket,
	        final TrackerDatabase database, final SessionManager session_manager) throws IOException {
		super(group, name, socket);
		
		this.database = database;
		this.session_manager = session_manager;
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see p2p.components.communication.Channel#communicate()
	 */
	@Override
	protected void communicate(final Request<?> request) throws IOException {
		
		try {
			
			final Request.Type request_type = request.getType();
			
			switch (request_type) {
			case REGISTER:
				
				this.register(request);
				break;
			
			case LOGIN:
				
				this.login(request);
				break;
			
			case SEARCH:
				
				this.search(request);
				break;
			
			case LOGOUT:
				
				this.logout(request);
				break;
			
			default:
				
				/*
				 * In the request's type is not supported do not reply. A valid
				 * client should counter this case with a timeout. As far as the
				 * server is concerned the communication ended.
				 */
				
				LoggerManager.tracedLog(this, Level.WARNING,
				        String.format("Detected unsupported request type with name <%s>", request_type.name())); //$NON-NLS-1$
				
			}
			
		} catch (ClassCastException | ClassNotFoundException ex) {
			throw new IOException(ex);
		}
		
	}
	
	/**
	 * Check if the session is active and corresponds to stored socket if peer
	 * remote server host policy is applied.
	 *
	 * @param session_id
	 *            The session to be checked
	 * @return True If the request is valid.
	 */
	protected final boolean isValidRequest(final int session_id) {
		
		synchronized (this.session_manager) {
			
			if (this.session_manager.isActiveSession(session_id)) {
				if (TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY
				        || this.session_manager.getPeerInformation(session_id).getSecond().getAddress().getHostAddress()
				                .equals(this.socket.getInetAddress().getHostAddress()))
				    return true;
			}
			
		}
		
		return false;
		
	}
	
	/**
	 * Process a login request. A login attempt is successful if a user with the
	 * provided username is register to the database and if the hashed value of
	 * the provided password matches the stored password of the user.
	 * Furthermore a session can not be initialized until the associated peer
	 * does not provide the required contact information and shared file
	 * descriptions.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the user
	 *            credentials.
	 * @return True If the login request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 * @throws ClassCastException
	 *             If a unexpected data type is received.
	 * @throws ClassNotFoundException
	 *             If an unknown data type is received.
	 */
	protected boolean login(final Request<?> request) throws IOException, ClassCastException, ClassNotFoundException {
		
		final Credentials user_credentials = Message.getData(request, Credentials.class);
		
		final String username = user_credentials.getUsername();
		Credentials registered_user = null;
		
		/*
		 * Check if the user is registered is a synchronized block to avoid race
		 * case with the other authentication methods and threads.
		 */
		
		synchronized (this.database) {
			
			if (this.database.fix(this.database.getSchema())) {
				registered_user = this.database.getUser(user_credentials.getUsername());
			}
			
		}
		
		if (registered_user != null) {
			
			/*
			 * Authenticate user.
			 */
			
			if (Hash.getSHA1(user_credentials.getPassword()).toString(16)
			        .equalsIgnoreCase(registered_user.getPassword())) {
				
				Integer session_id = null;
				
				try {
					
					/*
					 * Get user's id if active or generate a new one.
					 */
					
					synchronized (this.session_manager) {
						
						session_id = this.session_manager.getSessionID(username);
						
						if (session_id == null) {
							
							session_id = this.session_manager.getGeneratedID();
							
							/*
							 * Should lock the session id until peer description
							 * is received or the login process fails.
							 */
							
							if (session_id != null) {
								this.session_manager.lockSessionID(session_id);
							}
							
						}
						
					}
					
					/*
					 * Another check should be implemented here in case the
					 * generator fails.
					 */
					
					if (session_id != null) {
						
						this.out.writeObject(new Reply<>(Reply.Type.Success, session_id));
						
						/*
						 * Receive the peer's information.
						 */
						
						final Pair<?, ?> peer_description = Message.getData(this.in.readObject(), Pair.class);
						final InetSocketAddress peer_server_socket_address = InetSocketAddress.class
						        .cast(peer_description.getFirst());
						final HashSet<?> unknown_set = HashSet.class.cast(peer_description.getSecond());
						final HashSet<FileDescription> peer_shared_files = new HashSet<>(unknown_set.parallelStream()
						        .map(x -> FileDescription.class.cast(x)).collect(Collectors.toSet()));
						/*
						 * Update the peer's host information according to known
						 * data and current policy.
						 */
						
						String peer_reveived_host = peer_server_socket_address.getAddress().getHostAddress();
						final String peer_known_host = this.socket.getInetAddress().getHostAddress();
						
						if (!peer_reveived_host.equals(peer_known_host)) {
							if (!TrackerServerChannel.PEER_SERVER_REMOTE_HOST_POLICY) {
								peer_reveived_host = peer_known_host;
							}
						}
						
						boolean session_added = false;
						
						synchronized (this.session_manager) {
							
							/*
							 * Unlock the session id and add the new session to
							 * the manager.
							 */
							
							this.session_manager.unlockSessionID(session_id);
							
							session_added = this.session_manager.addSession(session_id, username,
							        new InetSocketAddress(peer_reveived_host, peer_server_socket_address.getPort()),
							        peer_shared_files);
							
						}
						
						if (session_added) {
							
							this.out.writeObject(Reply.getSimpleSuccessMessage());
							
							LoggerManager.tracedLog(this, Level.FINE,
							        String.format(
							                "A new session with id <%d> was created for the user with username <%s>.", //$NON-NLS-1$
							                session_id, user_credentials.getUsername()));
							
							return true;
							
						}
						
					}
					
				} finally {
					
					if (session_id != null) {
						
						/*
						 * Make sure to unlock the session id even if the
						 * request fails.
						 */
						
						synchronized (this.session_manager) {
							this.session_manager.unlockSessionID(session_id);
						}
						
					}
					
				}
				
			}
			
		}
		
		this.out.writeObject(Reply.getSimpleFailureMessage());
		
		LoggerManager.tracedLog(this, Level.WARNING,
		        String.format("The user with username <%s> tried to login but failed.", //$NON-NLS-1$
		                user_credentials.getUsername()));
		
		return false;
		
	}
	
	/**
	 * Process a logout request from the peer. In order for a peer to logout
	 * successfully the provided session id should be active. Also as long as a
	 * remote server host policy is not applied the request's sender is
	 * validated also through its IP address.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the
	 *            peer's session id.
	 * @return True If the logout process completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean logout(final Request<?> request) throws IOException {
		
		final Integer session_id = Message.getData(request, Integer.class);
		
		if ((session_id != null) && this.isValidRequest(session_id)) {
			
			boolean session_removed;
			
			synchronized (this.session_manager) {
				
				session_removed = this.session_manager.removeSession(session_id);
				
			}
			
			if (session_removed) {
				
				this.out.writeObject(Reply.getSimpleSuccessMessage());
				
				LoggerManager.tracedLog(this, Level.FINE,
				        String.format("The session with id <%d> was terminated by user's request.", //$NON-NLS-1$
				                session_id));
				
				return true;
				
			}
			
		}
		
		this.out.writeObject(Reply.getSimpleFailureMessage());
		
		LoggerManager.tracedLog(this, Level.WARNING,
		        String.format("The logout request for the session with id <%d> could not be completed successfully.", //$NON-NLS-1$
		                session_id));
		
		return false;
	}
	
	/**
	 * Process a registration request. As long as the user identified by its
	 * name does not exists a registration is always possible.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the new
	 *            user's credentials.
	 * @return True If the registration request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean register(final Request<?> request) throws IOException {
		
		final Credentials user_credentials = Message.getData(request, Credentials.class);
		
		/*
		 * TODO Add a salt.
		 */
		
		/*
		 * The password's are always stored to the database hashed and never in
		 * plaintext format.
		 */
		
		final BigInteger hashed_password = Hash.getSHA1(user_credentials.getPassword());
		final String username = user_credentials.getUsername();
		boolean user_registered = false;
		
		synchronized (this.database) {
			
			if (this.database.fix(this.database.getSchema()) && (this.database.getUser(username) == null)) {
				user_registered = this.database.setUser(username, hashed_password.toString(16));
			}
			
		}
		
		if (user_registered) {
			
			this.out.writeObject(Reply.getSimpleSuccessMessage());
			
			LoggerManager.tracedLog(this, Level.FINE,
			        String.format("A new user with username <%s> was registered to the tracker.", //$NON-NLS-1$
			                user_credentials.getUsername()));
			
		}
		else {
			
			this.out.writeObject(Reply.getSimpleFailureMessage());
			
			LoggerManager.tracedLog(this, Level.WARNING,
			        String.format("A registration with username <%s> could not be completed successfully.", //$NON-NLS-1$
			                user_credentials.getUsername()));
			
		}
		
		return user_registered;
		
	}
	
	/**
	 * Process a search request for the specified file. Before sending the
	 * result back to the peer the tracker checks if the corresponding servers
	 * are already running.
	 *
	 * @param request
	 *            The request that should be processed. Should contain the
	 *            requested filename.
	 * @return True If the search request completed successfully.
	 * @throws IOException
	 *             If an error occurs while sending or receiving data from the
	 *             streams
	 */
	protected boolean search(final Request<?> request) throws IOException {
		
		final Pair<Integer, String> data = TrackerServerChannel.decodeSessionRequest(request, String.class);
		final Integer session_id = data.getFirst();
		final String filename = data.getSecond();
		
		boolean is_valid_session = false;
		
		LinkedList<Pair<String, InetSocketAddress>> peers_information = new LinkedList<>();
		
		if (session_id != null) {
			
			synchronized (this.session_manager) {
				
				is_valid_session = this.isValidRequest(session_id);
				
				if (is_valid_session) {
					
					peers_information = new LinkedList<>(this.session_manager.searchFilename(filename));
					
				}
				
			}
			
			if (is_valid_session) {
				
				this.out.writeObject(new Reply<>(Reply.Type.Success, peers_information));
				
				return true;
				
			}
			
		}
		
		this.out.writeObject(Reply.getSimpleFailureMessage());
		
		return false;
		
	}
	
}
