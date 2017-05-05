package p2p.utilities;

import java.io.PrintWriter;
import java.util.Scanner;

/**
 * A StartX object acts as an interface that provides input to and reads output
 * from another object.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class StartX {

	/**
	 * The {@link Scanner} object that is used when input is required.
	 */
	protected final Scanner		in;
	/**
	 * The {@link PrintWriter} object that is used to print the prompts and the
	 * program's messages.
	 */
	protected final PrintWriter	out;

	/**
	 * Allocates a new StartX object.
	 *
	 * @param in
	 *            The {@link Scanner} object that is used when input is
	 *            required.
	 * @param out
	 *            The {@link PrintWriter} object that is used to print the
	 *            prompts and the program's messages.
	 */
	public StartX(final Scanner in, final PrintWriter out) {

		this.in = in;
		this.out = out;

	}

	/**
	 * Prompts for input and reads the next line.
	 *
	 * @param prompt
	 *            The prompt that is going to be printed.
	 * @return The requested input in string format.
	 */
	public String getInput(final String prompt) {

		if (prompt != null) {
			this.out.print(String.format("%s: ", prompt));
		}
		this.out.flush();

		return this.in.nextLine();
	}

	/**
	 * Starts the interface.
	 */
	public abstract void start();

}
