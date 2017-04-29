package p2p.peer;

import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.Scanner;

import p2p.common.structures.Credentials;
import p2p.common.structures.SocketDescription;
import p2p.common.stubs.Instructable;
import p2p.common.stubs.StartX;

/**
 * A PeerStartX object acts as an interface that provides input to and
 * reads output from {@link Peer} object.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class PeerStartX extends StartX {

	/**
	 * The Command enumeration indicates the commands that the
	 * PeerStartX object can handle.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Command implements Instructable {
		/**
		 * Indicates a registration command.
		 */
		REGISTER("register"), //$NON-NLS-1$
		/**
		 * Indicates a command to update the tracker's information.
		 */
		SET_TRACKER("set tracker"), //$NON-NLS-1$
		/**
		 * Indicates a command to exit the interface.
		 */
		EXIT("exit"); //$NON-NLS-1$

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

	private final Peer peer;

	/**
	 * Allocates a new PeerStartX object.
	 *
	 * @param peer
	 *        The {@link Peer} object that is going to be handled by
	 *        this interface.
	 * @param in
	 *        The {@link Scanner} object that is used when input is
	 *        required
	 * @param out
	 *        The {@link PrintWriter} object that is used to print the
	 *        prompts and the program's messages.
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

					this.peer.setTracker(new SocketDescription(this.getInput("host"), //$NON-NLS-1$
					        Integer.parseInt(this.getInput("port")))); //$NON-NLS-1$

					break;

				case REGISTER:

					this.out.println(String.format("Tracker> peer_registered: %b", //$NON-NLS-1$
					        this.peer.register(new Credentials(this.getInput("username"), //$NON-NLS-1$
					                this.getInput("password"))))); //$NON-NLS-1$
					break;

				case EXIT:
				default:
					break;
				}

			} catch (@SuppressWarnings("unused") NoSuchElementException ex) {
				this.out.println(String.format("Unrecognided command")); //$NON-NLS-1$
			}

		} while (last_command != Command.EXIT);

	}

}
