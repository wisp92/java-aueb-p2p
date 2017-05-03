package p2p.utilities.testing;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.temporal.ValueRange;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Credentials;
import p2p.components.communication.CloseableThread;
import p2p.components.peers.Peer;
import p2p.components.trackers.Tracker;
import p2p.utilities.LoggerManager;
import p2p.utilities.common.Instructable;

/**
 * A PeerTester is responsible for testing the correct registration of some
 * peers to a tracker.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerTester extends CloseableThread {
	
	/**
	 * The PeerTester.Command indicates the commands that can be passed from the
	 * command line as arguments.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Command implements Instructable {
		/**
		 * Indicates a command to to set the tracker's port.
		 */
		SET_NO_PEERS("-p"), //$NON-NLS-1$
		/**
		 * Indicates a command to set test's tempo.
		 */
		SET_TEMPO("-t"), //$NON-NLS-1$
		/**
		 * Indicates a command to set database's path.
		 */
		SET_DATABASE_PATH("-dp"); //$NON-NLS-1$
		
		/**
		 * Searches the enumeration for a Command object that can be associated
		 * with the given text.
		 *
		 * @param text
		 *            The text associated with the requested command.
		 * @return The Command object that can be associated with the given
		 *         text.
		 * @throws NoSuchElementException
		 *             If no Command object can be associated with the given
		 *             text.
		 */
		public static Command find(String text) throws NoSuchElementException {
			
			return Instructable.find(Command.class, text);
		}
		
		private final String text;
		
		private Command(String text) {
			
			this.text = text;
		}
		
		/*
		 * (non-Javadoc)
		 * @see p2p.common.Instructable#getText()
		 */
		@Override
		public String getText() {
			
			return this.text;
		}
	}
	
	/**
	 * A TestablePeer object extends the Peer object by providing some extra
	 * methods and parameters to collect necessary statistics for the
	 * evaluation.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	class TestablePeer extends Peer {
		
		private Credentials	user_credentials;
		private List<File>	available_files;
		private int			no_successful_logins		= 0;
		private int			no_successful_registrations	= 0;
		private int			no_successful_logouts		= 0;
		
		/**
		 * Allocates a new TestablePeer object.
		 *
		 * @param group
		 *            The {@link ThreadGroup} object that this peer belongs to.
		 * @param name
		 *            The name of this peer.
		 */
		public TestablePeer(ThreadGroup group, String name) {
			super(group, name);
		}
		
		/**
		 * @return The number of successful logins that this peer performed.
		 */
		public int getSuccessfulLogins() {
			
			return this.no_successful_logins;
		}
		
		/**
		 * @return The number of successful logouts that this peer performed.
		 */
		public int getSuccessfulLogouts() {
			
			return this.no_successful_logouts;
		}
		
		/**
		 * @return The number of successful registrations that this peer
		 *         performed.
		 */
		public int getSuccessfulRegistrations() {
			
			return this.no_successful_registrations;
		}
		
		/**
		 * @return Returns the user's credentials of this peer.
		 */
		public Credentials getUserCredentials() {
			
			return new Credentials(this.user_credentials);
		}
		
		/**
		 * Checks if the user's credentials have been set.
		 *
		 * @return True If the user's have been set.
		 */
		public boolean isUserCredentialsSet() {
			
			return this.user_credentials != null;
		}
		
		public boolean setAvailableFiles(List<File> available_files) {
			
			if (this.isAlive()) return false;
			
			this.available_files = available_files;
			
			return true;
		}
		
		public List<File> getAvailableFiles() {
			
			return this.available_files == null ? Collections.emptyList() : this.available_files;
			
		}
		
		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			
			if (!this.isUserCredentialsSet()) {
				LoggerManager.tracedLog(this, Level.WARNING, "The user's credentials have not been set."); //$NON-NLS-1$
			}
			
			if (!this.isSharedDirecorySet()) {
				LoggerManager.tracedLog(this, Level.WARNING, "The user's shared directory has not been set."); //$NON-NLS-1$
			}
			
			/*
			 * Tries to login to the tracker by registering in the process if
			 * needed.
			 */
			if (!this.login(this.user_credentials)) {
				if (this.register(this.user_credentials)) {
					this.no_successful_registrations++;
					if (this.login(this.user_credentials)) {
						this.no_successful_logins++;
					}
				}
			}
			else {
				this.no_successful_logins++;
			}
			
			List<String> filenames_to_download = Collections.emptyList();
			List<String> shared_filenames = this.getSharedFiles().parallelStream().map(x -> x.getName()).distinct()
			        .collect(Collectors.toList());
			List<String> available_filenames = this.getAvailableFiles().parallelStream().map(x -> x.getName())
			        .distinct().collect(Collectors.toList());
			
			available_filenames.removeAll(shared_filenames);
			
			if (available_filenames.size() > 0) {
				
				Collections.shuffle(available_filenames);
				filenames_to_download = available_filenames.stream()
				        .limit((new Random()).nextInt(available_filenames.size())).collect(Collectors.toList());
				
			}
			
			long peer_waiting_time = (new Random()).nextInt((int) (PeerTester.default_peer_waiting_interval.getMaximum()
			        - PeerTester.default_peer_waiting_interval.getMinimum()))
			        + PeerTester.default_peer_waiting_interval.getMinimum();
			
			try {
				
				/*
				 * Waits some time for other peers to initialize.
				 */
				
				Thread.sleep(peer_waiting_time);
				
				for (String filename : filenames_to_download) {
					this.addDownload(filename);
				}
				
				while (CloseableThread.countActive(this.clients_group) > 0) {
					
					Thread.sleep(peer_waiting_time);
				}
				
			} catch (InterruptedException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The peer's test was interrupted.", ex); //$NON-NLS-1$
			}
			
			if (this.logout()) {
				this.no_successful_logouts++;
			}
			
			try {
				
				this.close();
				
			} catch (IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The peer could not be terminated properly.", ex); //$NON-NLS-1$
			}
			
		}
		
		/**
		 * Updated the user's credentials for this peer.
		 *
		 * @param user_credentials
		 *            The credentials that are going to be associated.
		 * @return True If the update was successful.
		 */
		public boolean setUserCredentials(Credentials user_credentials) {
			
			if (this.isAlive()) return false;
			
			this.user_credentials = user_credentials;
			
			return true;
		}
		
	}
	
	/**
	 * The default no of peers to run in parallel.
	 */
	public static final int	default_no_peers	  = 1;
	/**
	 * The default number milliseconds to sleep when requested.
	 */
	public static final int	default_sleep_time	  = 1000;
	/**
	 * The delay in milliseconds between time delicate Processes. If it set to 0
	 * then no delay between any process is going to be added.
	 */
	public static final int	default_tempo		  = 1;
	/**
	 * Indicates the minimum number of sample files that are going to be copied
	 * to the peer's shared directory before the execution.
	 */
	public static final int	min_files_sample_size = 0;
	
	public static final ValueRange default_peer_waiting_interval = ValueRange.of(5000, 6000);
	
	/**
	 * Indicates the default directory of the databases.
	 */
	public static final String default_databases_directory = "databases";						 //$NON-NLS-1$
	/**
	 * The default path to the tracker's database file.
	 */
	public static final String default_database_path	   = String.format("%s/%s",				 //$NON-NLS-1$
	        PeerTester.default_databases_directory, "default_tracker.sqlite");					 //$NON-NLS-1$
	/**
	 * The default directory where the shared directories of the peers are going
	 * to be created.
	 */
	public static final String default_shared_directory	   = "shared/peers";					 //$NON-NLS-1$
	
	/**
	 * Starts the execution of the tracker.
	 *
	 * @param args
	 *            The console arguments.
	 */
	public static void main(String[] args) {
		
		int no_peers = PeerTester.default_no_peers;
		int tempo = PeerTester.default_tempo;
		String database_path = PeerTester.default_database_path;
		
		/*
		 * Load some parameters from the console.
		 */
		
		try {
			
			for (int i = 0; i < args.length; i += 2) {
				
				switch (Command.find(args[i])) {
				case SET_NO_PEERS:
					
					no_peers = Integer.parseInt(args[i + 1]);
					break;
				
				case SET_TEMPO:
					
					tempo = Integer.parseInt(args[i + 1]);
					break;
				
				case SET_DATABASE_PATH:
					
					database_path = args[i + 1];
					break;
				
				}
			}
			
			File databases_directory = new File(new File(database_path).getParent());
			
			/*
			 * Check if the database's directory exist.
			 */
			
			if (!databases_directory.isDirectory()) {
				LoggerManager.tracedLog(Level.SEVERE,
				        String.format("The specified database's directory <%s> does not exist.", databases_directory)); //$NON-NLS-1$
			}
			else {
				
				/*
				 * Create a new ThreadGroup to add tests and execute them.
				 */
				
				ThreadGroup testers = new ThreadGroup("Testers"); //$NON-NLS-1$
				
				try (PeerTester tester = new PeerTester(testers, "Tester", database_path, no_peers, tempo);) { //$NON-NLS-1$
					
					tester.start();
					tester.join();
					
				} catch (IOException ex) {
					LoggerManager.tracedLog(Level.SEVERE, "An IOException occurred during the test.", ex); //$NON-NLS-1$
				} catch (InterruptedException ex) {
					LoggerManager.tracedLog(Level.WARNING, "The test was interrupted.", ex); //$NON-NLS-1$
				}
				
			}
			
		} catch (NoSuchElementException ex) {
			LoggerManager.tracedLog(Level.SEVERE, "Unrecognized command line argument", ex); //$NON-NLS-1$
		}
		
	}
	
	private final ThreadGroup trackers_group = new ThreadGroup(String.format("%s.Trackers", this.getName())); //$NON-NLS-1$
	private final ThreadGroup peers_group	 = new ThreadGroup(String.format("%s.Peers", this.getName()));	  //$NON-NLS-1$
	private final int		  no_peers;
	private final int		  tempo;
	private final String	  database_path;
	
	/**
	 * Allocates a new PeerTester object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this tester belongs to.
	 * @param name
	 *            The name of this channel.
	 * @param no_peers
	 *            The number of peers to run in parallel.
	 * @param database_path
	 *            The path to the tracker's database file.
	 * @param tempo
	 *            The delay in milliseconds between time delicate Processes.
	 */
	public PeerTester(ThreadGroup group, String name, String database_path, int no_peers, int tempo) {
		super(group, name);
		
		this.no_peers = no_peers > 0 ? no_peers : PeerTester.default_no_peers;
		this.tempo = tempo >= 0 ? tempo : PeerTester.default_tempo;
		
		File database_file = new File(database_path);
		this.database_path = database_file.exists() && database_file.isFile() ? database_path
		        : PeerTester.default_database_path;
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		synchronized (this.trackers_group) {
			
			CloseableThread.interrupt(this.trackers_group);
			
		}
		
		synchronized (this.peers_group) {
			
			CloseableThread.interrupt(this.peers_group);
			
		}
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		
		try (Tracker tracker = new Tracker(this.trackers_group, String.format("%s.Tracker", this.getName()))) { //$NON-NLS-1$
			
			if (tracker.startManager(0, this.database_path)) {
				
				final InetSocketAddress tracker_socket_address = tracker.getServerAddress();
				if (tracker_socket_address != null) {
					
					ArrayDeque<TestablePeer> peers = new ArrayDeque<>();
					
					for (int i = 0; i < this.no_peers; i++) {
						
						Credentials user_credentials = new Credentials(String.format("user-%d", i), null); //$NON-NLS-1$
						
						/*
						 * The close() method of the PeerTester makes sure to
						 * prevent any memory leaks from the Peer objects.
						 */
						@SuppressWarnings("resource")
						TestablePeer peer = new TestablePeer(this.peers_group,
						        String.format("%s.Peer-%d", this.getName(), i)); //$NON-NLS-1$
						
						/*
						 * The tracker's description, user credentials and the
						 * shared directory's structure are determined before
						 * the actual test.
						 */
						if (peer.setTracker(tracker_socket_address)
						        && TestHelper.newSharedDirectory(peer,
						                String.format("%s/%s_shared", PeerTester.default_shared_directory, //$NON-NLS-1$
						                        peer.getName()),
						                PeerTester.min_files_sample_size)
						        && peer.setUserCredentials(user_credentials) && peer.setAvailableFiles(
						                TestHelper.getDefaultSharedFiles(TestHelper.default_sample_list_path))) {
							
							peer.start();
							peers.add(peer);
							
						}
						else {
							peer.close();
						}
						
						if (this.tempo > 0) {
							Thread.sleep(this.tempo);
						}
						
					}
					
					while (CloseableThread.countActive(this.peers_group) > 0) {
						
						LoggerManager.tracedLog(this, Level.INFO, String.format("%d from %d active peers", //$NON-NLS-1$
						        CloseableThread.countActive(this.peers_group), this.no_peers));
						
						Thread.sleep(PeerTester.default_sleep_time);
					}
					
					List<File> shared_files = peers.parallelStream().filter(x -> x.isSharedDirecorySet())
					        .flatMap(x -> Arrays.asList(new File(x.getSharedDirectory()).listFiles()).parallelStream())
					        .distinct().collect(Collectors.toList());
					long count_successful_peer_logins = peers.parallelStream().filter(x -> x.getSuccessfulLogins() > 0)
					        .count();
					long count_successful_peer_registrations = peers.parallelStream()
					        .filter(x -> x.getSuccessfulRegistrations() > 0).count();
					long count_successful_peer_logouts = peers.parallelStream()
					        .filter(x -> x.getSuccessfulLogouts() > 0).count();
					
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("%d peer(s) were able to register successfully.", //$NON-NLS-1$
					                count_successful_peer_registrations));
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("%d peer(s) were able to login successfully.", //$NON-NLS-1$
					                count_successful_peer_logins));
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("%d peer(s) were able to logout successfully.", //$NON-NLS-1$
					                count_successful_peer_logouts));
					
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("The initial sample of shared files contained %d files.", //$NON-NLS-1$
					                shared_files.size()));
					
					/*
					 * Delete all shared files.
					 */
					shared_files.parallelStream().forEach(x -> x.delete());
					
				}
				
			}
			
		} catch (IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the test.", ex); //$NON-NLS-1$
		} catch (InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The test was interrupted.", ex); //$NON-NLS-1$
		} finally {
			
			try {
				
				this.close();
				
			} catch (IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The tracker could not be terminated properly.", ex); //$NON-NLS-1$
			}
			
		}
		
	}
	
}
