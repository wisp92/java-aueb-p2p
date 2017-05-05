package p2p.utilities;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;

import p2p.components.trackers.Tracker;
import p2p.utilities.common.Instructable;

/**
 * A TrackerStartX object acts as an interface that provides input to and reads
 * output from {@link Tracker} object.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TrackerStartX extends StartX {

	/**
	 * The Command enumeration indicates the commands that the TrackerStartX
	 * object can handle.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	public enum Command implements Instructable {
		/**
		 * Indicates a command to start the server.
		 */
		START("start"),
		/**
		 * Indicates a command to stop the server.
		 */
		STOP("stop"),
		/**
		 * Indicates a command to exit the interface.
		 */
		EXIT("exit");

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
	 * Starts the execution of the tracker.
	 *
	 * @param args
	 *            The console arguments.
	 */
	public static void main(final String[] args) {

		final ThreadGroup trackers = new ThreadGroup("Trackers");

		try (Scanner in_scanner = new Scanner(System.in); PrintWriter out_writer = new PrintWriter(System.out)) {

			try (Tracker tracker = new Tracker(trackers, Tracker.class.getSimpleName())) {

				new TrackerStartX(tracker, in_scanner, out_writer).start();

			} catch (final IOException ex) {
				LoggerManager.tracedLog(Level.WARNING, "The tracker could not be terminated properly.", ex);
			}

		}

	}

	private final Tracker tracker;

	/**
	 * Allocates a new TrackerStartX object.
	 *
	 * @param tracker
	 *            The {@link Tracker} object that is going to be handled by this
	 *            interface.
	 * @param in
	 *            The {@link Scanner} object that is used when input is required
	 * @param out
	 *            The {@link PrintWriter} object that is used to print the
	 *            prompts and the program's messages.
	 */
	public TrackerStartX(final Tracker tracker, final Scanner in, final PrintWriter out) {

		super(in, out);

		this.tracker = tracker;
	}

	/*
	 * (non-Javadoc)
	 * @see p2p.common.StartX#getInput(java.lang.String)
	 */
	@Override
	public String getInput(final String prompt) {

		this.out.print("Tracker> ");

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
				case START:

					if (this.tracker.startManager(Integer.parseInt(this.getInput("port")),
					        this.getInput("database's path"))) {
						System.out.println("The server was started successfully.");
					}
					else {
						System.out.println("The server failed to start.");
					}

					break;

				case STOP:

					if (this.tracker.stopManager()) {
						System.out.println("The server terminated successfully.");
					}
					else {
						System.out.println("The server failed to terminate.");
					}

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
