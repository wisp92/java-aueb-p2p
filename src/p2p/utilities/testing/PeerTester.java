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

import p2p.components.Configuration;
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
	 * A PeerTesterBehavior enumeration indicates the available behaviors of the
	 * {@link PeerTester.TestablePeer TestablePeer} objects
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Behavior implements Instructable {
		
		/**
		 * Indicate that the peer is going to wait some time to give a chance to
		 * other peers to communicate with him and ask for some file and also
		 * waits for them to finish before closing. More peers could connect
		 * while waiting for the transfers to finish. professional
		 */
		GENTLE("gentle"),
		/**
		 * Indicates a behavior similar to gentle but waits some more time after
		 * all transactions finish. It is not accepting connection during that
		 * time.
		 */
		PATIENT("patient"),
		/**
		 * Indicates a behavior similar to gentle but does not accept peers
		 * after the initial waiting time.
		 */
		PROFESSIONAL("professional"),
		/**
		 * Indicates that the peer is not going to wait at all just look for
		 * some files, download them and close.
		 */
		FAST("fast");
		
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
		public static Behavior find(final String text) throws NoSuchElementException {
			
			return Instructable.find(Behavior.class, text);
		}
		
		/**
		 * @return A random behavior of the available.
		 */
		public static Behavior getRandomBehavior() {
			
			final List<Behavior> list = Arrays.asList(Behavior.values());
			
			if (list.size() > 0) {
				Collections.shuffle(list);
				return list.get(0);
			}
			
			return null;
			
		}
		
		private final String text;
		
		private Behavior(final String text) {
			
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
		private ValueRange	peer_waiting_interval;
		private Behavior	behavior;
		
		private int	 no_successful_logins		 = 0;
		private int	 no_successful_registrations = 0;
		private int	 no_successful_logouts		 = 0;
		private long no_requested_downloads		 = 0;
		
		/**
		 * Allocates a new TestablePeer object.
		 *
		 * @param group
		 *            The {@link ThreadGroup} object that this peer belongs to.
		 * @param name
		 *            The name of this peer.
		 */
		public TestablePeer(final ThreadGroup group, final String name) {
			super(group, name);
		}
		
		public List<File> getAvailableFiles() {
			
			return this.available_files == null ? Collections.emptyList() : this.available_files;
			
		}
		
		public long getRequestedDownloads() {
			
			return this.no_requested_downloads;
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
		
		/*
		 * (non-Javadoc)
		 * @see java.lang.Thread#run()
		 */
		@Override
		public void run() {
			
			if (!this.isUserCredentialsSet()) {
				LoggerManager.tracedLog(this, Level.WARNING, "The user's credentials have not been set.");
			}
			
			if (!this.isSharedDirecorySet()) {
				LoggerManager.tracedLog(this, Level.WARNING, "The user's shared directory has not been set.");
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
			
			/*
			 * Select a list of available files to download.
			 */
			List<String> filenames_to_download = Collections.emptyList();
			final List<String> shared_filenames = this.getSharedFiles().parallelStream().map(x -> x.getName())
			        .distinct().collect(Collectors.toList());
			final List<String> available_filenames = this.getAvailableFiles().parallelStream().map(x -> x.getName())
			        .distinct().collect(Collectors.toList());
			
			/*
			 * Keep only files that the peer doesn't have to the list.
			 */
			available_filenames.removeAll(shared_filenames);
			
			/*
			 * Pick a random number of them to search and download.
			 */
			if (available_filenames.size() > 0) {
				
				Collections.shuffle(available_filenames);
				filenames_to_download = available_filenames.stream()
				        .limit((new Random()).nextInt(available_filenames.size())).collect(Collectors.toList());
				
			}
			
			/*
			 * Select a random waiting time.
			 */
			final long peer_waiting_time = (new Random())
			        .nextInt((int) (this.peer_waiting_interval.getMaximum() - this.peer_waiting_interval.getMinimum()))
			        + this.peer_waiting_interval.getMinimum();
			
			/*
			 * Develop a peers behavior at random.
			 */
			
			LoggerManager.tracedLog(Level.INFO,
			        String.format("The peer adapted the %s behavior", this.behavior.toString()));
			
			try {
				
				/*
				 * Wait some time for other peers to initialize and connect.
				 */
				if (this.behavior != Behavior.FAST) {
					Thread.sleep(peer_waiting_time);
				}
				
			} catch (final InterruptedException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The peer's test was interrupted.", ex);
			}
			
			/*
			 * Try to download the selected files.
			 */
			for (final String filename : filenames_to_download) {
				this.addDownload(filename);
				this.no_requested_downloads++;
			}
			
			if (this.behavior == Behavior.PROFESSIONAL) {
				this.stopManager(true);
			}
			
			while (true) {
				if ((CloseableThread.countActive(this.clients_group) == 0)
				        && ((this.behavior == Behavior.FAST) || (this.numberOfActiveServers() == 0))) {
					break;
				}
			}
			
			try {
				
				/*
				 * Wait some time, not accepting connections, for the
				 * acknowledge requests to complete.
				 */
				if (this.behavior == Behavior.PATIENT) {
					
					this.stopManager(true);
					
					Thread.sleep(peer_waiting_time);
					
				}
				
			} catch (final InterruptedException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The peer's test was interrupted.", ex);
			}
			
			if (this.logout()) {
				this.no_successful_logouts++;
			}
			
			try {
				
				this.close();
				
			} catch (final IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The peer could not be terminated properly.", ex);
			}
			
		}
		
		public boolean setAvailableFiles(final List<File> available_files) {
			
			if (this.isAlive()) return false;
			
			this.available_files = available_files;
			
			return true;
		}
		
		public boolean setBehavior(final String text) {
			
			if (this.isAlive()) return false;
			
			try {
				
				this.behavior = Behavior.find(text);
				
			} catch (final NoSuchElementException ex) {
				
				this.behavior = Behavior.getRandomBehavior();
				
			}
			
			return true;
		}
		
		public boolean setPeerWaitingInterval(final ValueRange peer_waiting_interval) {
			
			if (this.isAlive()) return false;
			
			this.peer_waiting_interval = peer_waiting_interval;
			
			return true;
			
		}
		
		/**
		 * Updated the user's credentials for this peer.
		 *
		 * @param user_credentials
		 *            The credentials that are going to be associated.
		 * @return True If the update was successful.
		 */
		public boolean setUserCredentials(final Credentials user_credentials) {
			
			if (this.isAlive()) return false;
			
			this.user_credentials = user_credentials;
			
			return true;
		}
		
	}
	
	/**
	 * Starts the execution of the tracker.
	 *
	 * @param args
	 *            The console arguments.
	 */
	public static void main(final String[] args) {
		
		Configuration.setAsDefault(new Configuration("configuration.properties"));
		
		/*
		 * Create a new ThreadGroup to add tests and execute them.
		 */
		
		final ThreadGroup testers = CloseableThread.newThreadGroup("Testers");
		
		try (PeerTester tester = new PeerTester(testers, "Tester");) {
			
			tester.start();
			tester.join();
			
		} catch (final IOException ex) {
			LoggerManager.tracedLog(Level.SEVERE, "An IOException occurred during the test.", ex);
		} catch (final InterruptedException ex) {
			LoggerManager.tracedLog(Level.WARNING, "The test was interrupted.", ex);
		}
		
	}
	
	private final ThreadGroup trackers_group = CloseableThread.newThreadGroup(this, "Trackers");
	private final ThreadGroup peers_group	 = CloseableThread.newThreadGroup(this, "Peers");
	
	/**
	 * The default configuration file of the tester.
	 */
	private final Configuration	configuration;
	/**
	 * The default no of peers to run in parallel.
	 */
	private final int			no_peers;
	/**
	 * The default number milliseconds to sleep when requested.
	 */
	private final int			sleep_time;
	/**
	 * The delay in milliseconds between time delicate Processes. If it set to 0
	 * then no delay between any process is going to be added.
	 */
	private final int			tempo;
	/**
	 * Indicates the minimum number of sample files that are going to be copied
	 * to the peer's shared directory before the execution.
	 */
	private final int			min_sample_files;
	
	/**
	 * Indicates the range of time interval a peer is going to wait for incoming
	 * connections.
	 */
	private final ValueRange peer_waiting_interval;
	
	/**
	 * Indicates the default directory of the databases.
	 */
	private final String databases_directory;
	
	/**
	 * The default directory where the shared directories of the peers are going
	 * to be created.
	 */
	private final String shared_directory;
	
	/**
	 * Indicates the default mode for selecting behaviors of the peers. Can be
	 * either one of <all, gentle, professional, patient, fast>
	 */
	private final String behavior_mode;
	
	/**
	 * Indicate if the shared files are going to be deleted automatically after
	 * the test.
	 */
	private final boolean delete_shared_files;
	
	private final String database_path;
	
	/**
	 * Allocates a new PeerTester object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this tester belongs to.
	 * @param name
	 *            The name of this channel.
	 */
	public PeerTester(final ThreadGroup group, final String name) {
		super(group, name);
		
		this.configuration = new Configuration(Configuration.getDefault().getString("test_configuration"));
		
		this.no_peers = this.configuration.getInteger("no_peers", 1);
		this.tempo = this.configuration.getInteger("tempo", 1);
		this.sleep_time = this.configuration.getInteger("sleep_time", 1000);
		this.min_sample_files = this.configuration.getInteger("min_sample_files", 2);
		this.peer_waiting_interval = ValueRange.of(this.configuration.getInteger("min_peer_waiting_time", 3000),
		        this.configuration.getInteger("max_peer_waiting_time", 6000));
		this.databases_directory = this.configuration.getString("database_directory", "databases");
		this.shared_directory = this.configuration.getString("shared_directory_path", "shared/peers");
		this.behavior_mode = this.configuration.getString("behavior_mode", "all");
		this.delete_shared_files = this.configuration.getBoolean("delete_shared_files", false);
		
		final File database_directory_file = new File(this.databases_directory);
		if (database_directory_file.exists()) {
			if (!database_directory_file.isDirectory()) {
				LoggerManager.tracedLog(Level.SEVERE,
				        String.format("The database directory <%s> corresponds to a file.", this.databases_directory));
			}
		}
		else {
			database_directory_file.mkdirs();
		}
		
		this.database_path = (new File(database_directory_file, "default_tracker.sqlite")).getAbsolutePath();
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		
		CloseableThread.interrupt(this.trackers_group);
		CloseableThread.interrupt(this.peers_group);
		
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {
		
		try (Tracker tracker = new Tracker(this.trackers_group, String.format("%s.Tracker", this.getName()))) {
			
			if (tracker.startManager(0, this.database_path)) {
				
				final InetSocketAddress tracker_socket_address = tracker.getServerAddress();
				if (tracker_socket_address != null) {
					
					final ArrayDeque<TestablePeer> peers = new ArrayDeque<>();
					
					for (int i = 0; i < this.no_peers; i++) {
						
						final Credentials user_credentials = new Credentials(String.format("user-%d", new Integer(i)),
						        null);
						
						/*
						 * The close() method of the PeerTester makes sure to
						 * prevent any memory leaks from the Peer objects.
						 */
						@SuppressWarnings("resource")
						final TestablePeer peer = new TestablePeer(this.peers_group,
						        String.format("%s.Peer-%d", this.getName(), new Integer(i)));
						
						/*
						 * The tracker's description, user credentials and the
						 * shared directory's structure are determined before
						 * the actual test.
						 */
						if (peer.setTracker(tracker_socket_address)
						        && TestHelper.newSharedDirectory(peer,
						                String.format("%s/%s_shared", this.shared_directory, peer.getName()),
						                this.min_sample_files)
						        && peer.setUserCredentials(user_credentials)
						        && peer.setAvailableFiles(
						                TestHelper.getDefaultSharedFiles(TestHelper.default_sample_list_path))
						        && peer.setBehavior(this.behavior_mode)
						        && peer.setPeerWaitingInterval(this.peer_waiting_interval)) {
							
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
						
						LoggerManager.tracedLog(this, Level.INFO,
						        String.format("%d from %d active peers",
						                new Integer(CloseableThread.countActive(this.peers_group)),
						                new Integer(this.no_peers)));
						
						Thread.sleep(this.sleep_time);
					}
					
					final Set<File> finished_shared_files = peers.parallelStream().filter(x -> x.isSharedDirecorySet())
					        .flatMap(x -> Arrays.asList(new File(x.getSharedDirectory()).listFiles()).parallelStream())
					        .distinct().collect(Collectors.toSet());
					final Long count_successful_peer_logins = new Long(
					        peers.parallelStream().filter(x -> x.getSuccessfulLogins() > 0).count());
					final Long count_successful_peer_registrations = new Long(
					        peers.parallelStream().filter(x -> x.getSuccessfulRegistrations() > 0).count());
					final Long count_successful_peer_logouts = new Long(
					        peers.parallelStream().filter(x -> x.getSuccessfulLogouts() > 0).count());
					final Long count_requested_downloads = peers.parallelStream()
					        .map(x -> new Long(x.getRequestedDownloads()))
					        .reduce(new Long(0), (x, y) -> new Long(x.longValue() + y.longValue()));
					final Long count_successful_downloads = peers.parallelStream()
					        .map(x -> new Long(x.getCompletedDownloads()))
					        .reduce(new Long(0), (x, y) -> new Long(x.longValue() + y.longValue()));
					final Long count_acknowledged_downloads = peers.parallelStream()
					        .map(x -> new Long(x.getAcknowledgedDownloads()))
					        .reduce(new Long(0), (x, y) -> new Long(x.longValue() + y.longValue()));
					final Set<File> downloaded_files = peers.parallelStream()
					        .flatMap(x -> x.getDownloadedFiles().stream()).collect(Collectors.toSet());
					
					LoggerManager.tracedLog(this, Level.INFO, String.format(
					        "%d peer(s) were able to register successfully.", count_successful_peer_registrations));
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("%d peer(s) were able to login successfully.", count_successful_peer_logins));
					LoggerManager.tracedLog(this, Level.INFO, String
					        .format("%d peer(s) were able to logout successfully.", count_successful_peer_logouts));
					
					final Set<File> initial_shared_files = finished_shared_files.stream().collect(Collectors.toSet());
					initial_shared_files.removeAll(downloaded_files);
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("The initial sample of shared files contained %d files.",
					                new Integer(initial_shared_files.size())));
					
					LoggerManager.tracedLog(this, Level.INFO, String.format(
					        "%d from %d downloads completed successfully (Validity Check: %b).",
					        count_successful_downloads, count_requested_downloads,
					        new Boolean(count_successful_downloads
					                .longValue() == (finished_shared_files.size() - initial_shared_files.size()))));
					
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("%d from %d successful downloads were acknowledged by the tracker.",
					                count_acknowledged_downloads, count_successful_downloads));
					
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("The following files was the initial sample: %s", initial_shared_files
					                .stream().map(x -> x.getAbsolutePath()).collect(Collectors.toList()).toString()));
					LoggerManager.tracedLog(this, Level.INFO,
					        String.format("The following files have been downloaded: %s", downloaded_files.stream()
					                .map(x -> x.getAbsolutePath()).collect(Collectors.toList()).toString()));
					
					/*
					 * Delete all shared files.
					 */
					if (this.delete_shared_files) {
						finished_shared_files.parallelStream().forEach(x -> x.delete());
					}
					
				}
				
			}
			
		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.SEVERE, "An IOException occurred during the test.", ex);
		} catch (final InterruptedException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The test was interrupted.", ex);
		} finally {
			
			try {
				
				this.close();
				
			} catch (final IOException ex) {
				LoggerManager.tracedLog(this, Level.WARNING, "The tracker could not be terminated properly.", ex);
			}
			
		}
		
	}
	
}
