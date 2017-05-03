package p2p.utilities;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A LoggerManager object provides easy-accessed methods to document a methods
 * log request.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class LoggerManager {

	/**
	 * Logs the specified message by determining at runtime the caller's
	 * description and associated logger.
	 *
	 * @param level
	 *            The level of severity of the log's entry.
	 * @param message
	 *            The message body of the log's entry.
	 */
	public static void tracedLog(Level level, String message) {

		LoggerManager.tracedLog(null, level, message, null);
	}

	/**
	 * Logs the specified message and the associated exception if on exists by
	 * determining at runtime the caller's description and associated logger.
	 *
	 * @param level
	 *            The level of severity of the log's entry.
	 * @param message
	 *            The message body of the log's entry.
	 * @param exception
	 *            The associated {@link Throwable} if one exists.
	 */
	public static void tracedLog(Level level, String message, Throwable exception) {

		LoggerManager.tracedLog(null, level, message, exception);
	}

	/**
	 * Logs the specified message by determining at runtime the caller's
	 * description and associated logger.
	 *
	 * @param thread
	 *            The thread were the log happens.
	 * @param level
	 *            The level of severity of the log's entry.
	 * @param message
	 *            The message body of the log's entry.
	 */
	public static void tracedLog(Thread thread, Level level, String message) {

		LoggerManager.tracedLog(thread, level, message, null);
	}

	/**
	 * Logs the specified message and the associated exception if on exists by
	 * determining at runtime the caller's description and associated logger.
	 *
	 * @param thread
	 *            The thread were the log happens.
	 * @param level
	 *            The level of severity of the log's entry.
	 * @param message
	 *            The message body of the log's entry.
	 * @param exception
	 *            The associated {@link Throwable} if one exists.
	 */
	public static synchronized void tracedLog(Thread thread, Level level, String message, Throwable exception) {

		final int trace_index = 1;
		final StackTraceElement[] stack_trace = Thread.currentThread().getStackTrace();
		final StackTraceElement selected_element = stack_trace[stack_trace.length - 1 - trace_index];

		String caller_class_name = selected_element.getClassName();
		String caller_name = selected_element.getMethodName();

		try {

			Class<?> caller_class;

			if (thread == null) {
				caller_class = Class.forName(caller_class_name);
			}
			else {
				caller_class = thread.getClass();
				/* TODO Check this */
				//caller_class_name = caller_class.getName();
				caller_class_name = String.format("%s (%s)", caller_class_name, thread.getName()); //$NON-NLS-1$
			}

			String caller_package_name = caller_class.getPackage().getName();

			if (exception != null) {
				Logger.getLogger(caller_package_name).logp(level, caller_class_name, caller_name, message, exception);
			}
			else {
				Logger.getLogger(caller_package_name).logp(level, caller_class_name, caller_name, message);
			}

		} catch (ClassNotFoundException ex) {
			Logger.getLogger(LoggerManager.class.getPackage().getName()).log(Level.SEVERE,
			        String.format("Logging was not possible for class <%s>.", caller_class_name), ex); //$NON-NLS-1$
		}

	}

}
