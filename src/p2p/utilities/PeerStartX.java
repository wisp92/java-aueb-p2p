package p2p.utilities;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.stream.Stream;

import p2p.components.Configuration;
import p2p.components.common.Credentials;
import p2p.components.peers.Peer;
import p2p.utilities.common.Instructable;
import p2p.utilities.testing.TestHelper;

/**
 * A PeerStartX object acts as an interface that provides input to and reads
 * output from {@link Peer} object.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerStartX extends StartX {

	/**
	 * The Command enumeration indicates the commands that the PeerStartX object
	 * can handle.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Command implements Instructable {
		/**
		 * Indicates a registration command.
		 */
		REGISTER("register"),
		/**
		 * Indicates a command to login to the tracker.
		 */
		LOGIN("login"),
		/**
		 * Indicates a command to logout from the tracker.
		 */
		LOGOUT("logout"),
		/**
		 * Indicates a command to update the tracker's information.
		 */
		SET_TRACKER("set tracker"),
		/**
		 * Indicates a command to change the shared directory's location.
		 */
		SET_SHARED_DIRECTORY("set directory"),
		/**
		 * Indicates a command to print all the available shared files from the
		 * default list.
		 */
		PRINT_FILES_LIST("print list"),
		/**
		 * Indicates a command to print all the files in the shared directory.
		 */
		PRINT_SHARED_DIRECTORY("print directory"),
		/**
		 * Indicates a command to download the specified file.
		 */
		DOWNLOAD("download"),
		/**
		 * Indicates a command to print current progress report,
		 */
		PROGRESS("progress"),
		/**
		 * Indicates a command to exit the interface.
		 */
		HELP("help"),
		/**
		 * Indicates a command to exit the interface.
		 */
		EXIT("exit"); // $NON-NLS-4$

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
		public static Command find(final String text) throws NoSuchElementException {

			return Instructable.find(Command.class, text);
		}

		private final String text;

		private Command(final String text) {

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
	 * Starts the execution of the peer.
	 *
	 * @param args
	 *            The console arguments.
	 */
	public static void main(final String[] args) {

		Configuration.setAsDefault(new Configuration("configuration.properties"));

		final ThreadGroup peers = new ThreadGroup("Peers");

		try (Scanner in_scanner = new Scanner(System.in); PrintWriter out_writer = new PrintWriter(System.out)) {

			try (Peer peer = new Peer(peers, Peer.class.getSimpleName())) {

				new PeerStartX(peer, in_scanner, out_writer).start();

			} catch (final IOException ex) {
				LoggerManager.tracedLog(Level.WARNING, "The peer could not be terminated properly.", ex);
			}

		}

	}

	private final Peer peer;

	/**
	 * Allocates a new PeerStartX object.
	 *
	 * @param peer
	 *            The {@link Peer} object that is going to be handled by this
	 *            interface.
	 * @param in
	 *            The {@link Scanner} object that is used when input is required
	 * @param out
	 *            The {@link PrintWriter} object that is used to print the
	 *            prompts and the program's messages.
	 */
	public PeerStartX(final Peer peer, final Scanner in, final PrintWriter out) {

		super(in, out);

		this.peer = peer;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.StartX#getInput(java.lang.String)
	 */
	@Override
	public String getInput(final String prompt) {

		this.out.print("Peer> ");

		return super.getInput(prompt);
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.StartX#start()
	 */
	@Override
	public void start() {

		Command last_command = null;

		do {

			try {

				last_command = Command.find(this.getInput(null));

				switch (last_command) {
				case SET_TRACKER:

					if (!this.peer.setTracker(
					        new InetSocketAddress(this.getInput("host"), Integer.parseInt(this.getInput("port"))))) {
						System.out.println("Could not update the tracker's information.");
					}

					break;

				case SET_SHARED_DIRECTORY:

					if (!this.peer.setSharedDirectory(this.getInput("path to shared directory"))) {
						System.out.println("Could not update the shared didrectory's location.");
					}

					break;

				case PRINT_FILES_LIST:

					TestHelper
					        .getDefaultSharedFiles(Paths.get(Configuration.getDefault().getString("sample_list_path",
					                TestHelper.default_sample_list_path)))
					        .forEach(x -> System.out.println(x.getName()));
					break;

				case PRINT_SHARED_DIRECTORY:

					this.peer.getSharedFiles().forEach(x -> System.out.println(x.getName()));
					break;

				case DOWNLOAD:

					this.peer.addDownload(this.getInput("filename"));
					System.out.println("The file was added to the schedule.");
					break;

				case PROGRESS:

					System.out.println("Active Downloads:");
					this.peer.getDownloadedFiles().forEach(x -> System.out.println(x));
					System.out.println("Failed Downloads:");
					this.peer.getFailedDownloadFiles().forEach(x -> System.out.println(x));
					System.out.println("Completed Downloads:");
					this.peer.getCompletedDownloadFiles().forEach(x -> System.out.println(x));
					System.out.println(String.format("Tracker acknowledged %d of %d completed downloads",
					        new Long(this.peer.getAcknowledgedDownloads()),
					        new Long(this.peer.getCompletedDownloads())));
					break;

				case REGISTER:

					if (this.peer.register(new Credentials(this.getInput("username"), this.getInput("password")))) {//$NON-NLS-2$
						System.out.println("Registration was successful.");
					}
					else {
						System.out.println("Registration failed.");
					}

					break;

				case LOGIN:

					if (this.peer.login(new Credentials(this.getInput("username"), this.getInput("password")))) {//$NON-NLS-2$
						System.out.println("Login was successful.");
					}
					else {
						System.out.println("Login failed.");
					}

					break;

				case LOGOUT:

					if (this.peer.logout()) {// $NON-NLS-1$
						System.out.println("Logout was successful.");
					}
					else {
						System.out.println("Logout failed.");
					}

					break;

				case HELP:

					Stream.of(Command.values()).forEach(x -> System.out.println(x.getText()));
					break;

				case EXIT:
				default:
					break;
				}

			} catch (@SuppressWarnings("unused") final NoSuchElementException ex) {
				this.out.println("Unrecognized command");
			}

		} while (last_command != Command.EXIT);			
		
	}

}
