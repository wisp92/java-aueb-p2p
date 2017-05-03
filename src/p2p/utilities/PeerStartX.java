package p2p.utilities;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;

import p2p.components.common.Credentials;
import p2p.components.peers.Peer;
import p2p.utilities.common.Instructable;

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
		REGISTER("register"), //$NON-NLS-1$
		/**
		 * Indicates a command to login to the tracker.
		 */
		LOGIN("login"), //$NON-NLS-1$
		/**
		 * Indicates a command to logout from the tracker.
		 */
		LOGOUT("logout"), //$NON-NLS-1$
		/**
		 * Indicates a command to update the tracker's information.
		 */
		SET_TRACKER("set tracker"), //$NON-NLS-1$
		/**
		 * Indicates a command to change the shared directory's location.
		 */
		SET_SHARED_DIRECTORY("set shared_directory"), //$NON-NLS-1$
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
	 * Starts the execution of the peer.
	 *
	 * @param args
	 *            The console arguments.
	 */
	public static void main(String[] args) {

		ThreadGroup peers = new ThreadGroup("Peers"); //$NON-NLS-1$

		try (Scanner in_scanner = new Scanner(System.in); PrintWriter out_writer = new PrintWriter(System.out)) {

			try (Peer peer = new Peer(peers, Peer.class.getSimpleName())) {

				new PeerStartX(peer, in_scanner, out_writer).start();

			} catch (IOException ex) {
				LoggerManager.tracedLog(Level.WARNING, "The peer could not be terminated properly.", ex); //$NON-NLS-1$
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
	public PeerStartX(Peer peer, Scanner in, PrintWriter out) {

		super(in, out);

		this.peer = peer;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.StartX#getInput(java.lang.String)
	 */
	@Override
	public String getInput(String prompt) {

		this.out.print("Peer> "); //$NON-NLS-1$

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

					if (!this.peer.setTracker(new InetSocketAddress(this.getInput("host"), //$NON-NLS-1$
					        Integer.parseInt(this.getInput("port"))))) { //$NON-NLS-1$
						System.out.println("Could not update the tracker's information.");
					}

					break;

				case SET_SHARED_DIRECTORY:

					if (!this.peer.setSharedDirectory(this.getInput("path to shared directory"))) { //$NON-NLS-1$
						System.out.println("Could not update the shared didrectory's location.");
					}

					break;

				case REGISTER:

					if (this.peer.register(new Credentials(this.getInput("username"), //$NON-NLS-1$
					        this.getInput("password")))) {//$NON-NLS-1$
						System.out.println("Registration was successful.");
					}
					else {
						System.out.println("Registration failed.");
					}

					break;

				case LOGIN:

					if (this.peer.login(new Credentials(this.getInput("username"), //$NON-NLS-1$
					        this.getInput("password")))) {//$NON-NLS-1$
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

				case EXIT:
				default:
					break;
				}

			} catch (@SuppressWarnings("unused") NoSuchElementException ex) {
				this.out.println("Unrecognized command"); //$NON-NLS-1$
			}

		} while (last_command != Command.EXIT);

	}

}
