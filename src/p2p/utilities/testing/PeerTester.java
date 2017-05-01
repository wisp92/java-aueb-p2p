package p2p.utilities.testing;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.common.Credentials;
import p2p.components.communication.CloseableThread;
import p2p.components.peers.Peer;
import p2p.components.trackers.Tracker;
import p2p.utilities.LoggerManager;
import p2p.utilities.common.Instructable;

/**
 * A PeerTester is responsible for testing the correct registration of
 * some peers to a tracker.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerTester extends CloseableThread {

	/**
	 * The PeerTester.Command indicates the commands that can be
	 * passed from the command line as arguments.
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
		 * Searches the enumeration for a Command object that can be
		 * associated with the given text.
		 *
		 * @param text
		 *        The text associated with the requested command.
		 * @return The Command object that can be associated with the
		 *         given text.
		 * @throws NoSuchElementException
		 *         If no Command object can be associated with the
		 *         given text.
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

	class TestablePeer extends Peer {

		private Credentials user_credentials;

		private int no_successful_logins = 0;

		private int	no_successful_registrations	= 0;
		private int	no_successful_logouts		= 0;

		public TestablePeer(ThreadGroup group, String name) {
			super(group, name);
		}

		public int getSuccessfulLogins() {

			return this.no_successful_logins;
		}

		public int getSuccessfulLogouts() {

			return this.no_successful_logouts;
		}

		public int getSuccessfulRegistrations() {

			return this.no_successful_registrations;
		}

		public Credentials getUserCredentials() {

			return new Credentials(this.user_credentials);
		}

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
				LoggerManager.logMessage(this, Level.WARNING,
				        String.format("User credentials are not set for <%s>", this.getName())); //$NON-NLS-1$
			}

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

			try {

				Thread.sleep(PeerTester.default_sleep_time);

			} catch (InterruptedException ex) {
				LoggerManager.logException(this, Level.WARNING, ex);
			}

			if (this.logout()) {
				this.no_successful_logouts++;
			}

			try {

				this.close();

			} catch (IOException ex) {
				LoggerManager.logException(this, Level.SEVERE, ex);
			}

		}

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
	 * The delay in milliseconds between time delicate Processes. If
	 * it set to 0 then no delay between any process is going to be
	 * added.
	 */
	public static final int	default_tempo		  = 1;
	/**
	 * Indicates the minimum number of sample files that are going to
	 * be copied to the peer's shared directory before the execution.
	 */
	public static final int	min_files_sample_size = 2;

	/**
	 * Indicates the default directory of the databases.
	 */
	public static final String default_databases_directory = "databases"; //$NON-NLS-1$

	/**
	 * The default directory where the shared directories of the peers
	 * are going to be created.
	 */
	public static final String default_shared_directory = "shared/peers"; //$NON-NLS-1$

	/**
	 * The default path to the tracker's database file.
	 */
	public static final String default_database_path = String.format("%s/%s", //$NON-NLS-1$
	        PeerTester.default_databases_directory, "default_tracker.sqlite"); //$NON-NLS-1$

	/**
	 * Starts the execution of the tracker.
	 *
	 * @param args
	 *        The console arguments.
	 */
	public static void main(String[] args) {

		int no_peers = PeerTester.default_no_peers;
		int tempo = PeerTester.default_tempo;
		String database_path = PeerTester.default_database_path;

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

			if (!databases_directory.isDirectory()) {
				LoggerManager.logMessage(PeerTester.class, Level.SEVERE,
				        String.format("The specified database's directory <%s> does not exist.", //$NON-NLS-1$
				                databases_directory));
			}
			else {

				ThreadGroup testers = new ThreadGroup("Testers"); //$NON-NLS-1$

				try (PeerTester tester = new PeerTester(testers, "Tester", database_path, no_peers, tempo);) { //$NON-NLS-1$

					tester.start();
					tester.join();

				} catch (InterruptedException | IOException ex) {
					LoggerManager.logException(PeerTester.class, Level.WARNING, ex);
				}

			}

		} catch (@SuppressWarnings("unused") NoSuchElementException ex) {
			LoggerManager.logMessage(PeerTester.class, Level.SEVERE, "Not a supported command line argument."); //$NON-NLS-1$
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
	 *        The {@link ThreadGroup} object that this tester belongs
	 *        to.
	 * @param name
	 *        The name of this channel.
	 * @param no_peers
	 *        The number of peers to run in parallel.
	 * @param database_path
	 *        The path to the tracker's database file.
	 * @param tempo
	 *        The delay in milliseconds between time delicate
	 *        Processes.
	 */
	public PeerTester(ThreadGroup group, String name, String database_path, int no_peers, int tempo) {
		super(group, name);

		this.no_peers = no_peers > 0 ? no_peers : PeerTester.default_no_peers;
		this.tempo = tempo >= 0 ? tempo : PeerTester.default_tempo;

		File database_file = new File(database_path);
		this.database_path
		        = database_file.exists() && database_file.isFile() ? database_path : PeerTester.default_database_path;

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
						 * The close() method of the PeerTester makes
						 * sure to prevent any memory leaks from the
						 * Peer object's.
						 */
						@SuppressWarnings("resource")
						TestablePeer peer
						        = new TestablePeer(this.peers_group, String.format("%s.Peer-%d", this.getName(), i)); //$NON-NLS-1$

						/*
						 * The tracker's description, user credentials
						 * and the shared directory's structure are
						 * determined before the actual test.
						 */
						if (peer.setTracker(tracker_socket_address)
						        && TestHelper.newSharedDirectory(peer,
						                String.format("%s/%s_shared", PeerTester.default_shared_directory, //$NON-NLS-1$
						                        peer.getName()),
						                PeerTester.min_files_sample_size)
						        && peer.setUserCredentials(user_credentials)) {

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

						LoggerManager.logMessage(this, Level.INFO, String.format("%d from %d active peers", //$NON-NLS-1$
						        CloseableThread.countActive(this.peers_group), this.no_peers));

						Thread.sleep(PeerTester.default_sleep_time);
					}

					List<File> shared_files = peers.parallelStream().filter(x -> x.isSharedDirecorySet())
					        .flatMap(x -> Arrays.asList(new File(x.getSharedDirectory()).listFiles()).parallelStream())
					        .distinct().collect(Collectors.toList());
					long count_successful_peer_logins
					        = peers.parallelStream().filter(x -> x.getSuccessfulLogins() > 0).count();
					long count_successful_peer_registrations
					        = peers.parallelStream().filter(x -> x.getSuccessfulRegistrations() > 0).count();
					long count_successful_peer_logouts
					        = peers.parallelStream().filter(x -> x.getSuccessfulLogouts() > 0).count();

					LoggerManager.logMessage(this, Level.INFO,
					        String.format("%d peer(s) were able to login sucessfully.", //$NON-NLS-1$
					                count_successful_peer_logins));
					LoggerManager.logMessage(this, Level.INFO,
					        String.format("%d peer(s) were able to register sucessfully.", //$NON-NLS-1$
					                count_successful_peer_registrations));
					LoggerManager.logMessage(this, Level.INFO,
					        String.format("%d peer(s) were able to logout sucessfully.", //$NON-NLS-1$
					                count_successful_peer_logouts));
					LoggerManager.logMessage(this, Level.INFO,
					        String.format("The intialial sample of shared files contained %d files.", //$NON-NLS-1$
					                shared_files.size()));

					/*
					 * Delete all shared files.
					 */
					shared_files.parallelStream().forEach(x -> x.delete());

				}

			}

		} catch (IOException | InterruptedException ex) {
			LoggerManager.logException(this, Level.SEVERE, ex);
		} finally {

			try {

				this.close();

			} catch (IOException ex) {
				LoggerManager.logException(this, Level.SEVERE, ex);
			}

		}

	}

}
