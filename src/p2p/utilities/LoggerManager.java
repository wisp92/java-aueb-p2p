package p2p.utilities;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A LoggerManager object is responsible for providing {@link Logger}
 * objects when a class tries to log its behavior.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class LoggerManager {
	
	/**
	 * Logs the specified message and the associated exception if on
	 * exists by determining at runtime the caller's description and
	 * associated logger.
	 * 
	 * @param level
	 *        The level of severity of the log's entry.
	 * @param message
	 *        The message body of the log's entry.
	 * @param exception
	 *        The associated {@link Throwable} if one exists.
	 */
	public static void tracedLog(Level level, String message, Throwable exception) {
		
		final int trace_index = 2;
		final StackTraceElement[] stack_trace = Thread.currentThread().getStackTrace();
		
		String caller_class_name = stack_trace[trace_index].getClassName();
		
		try {
			
			Class<?> caller_class = Class.forName(caller_class_name);
			String caller_package_name = caller_class.getPackage().getName();
			String caller_name = stack_trace[trace_index].getMethodName();
			
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
	
	/**
	 * Logs the specified message by determining at runtime the
	 * caller's description and associated logger.
	 * 
	 * @param level
	 *        The level of severity of the log's entry.
	 * @param message
	 *        The message body of the log's entry.
	 */
	public static void tracedLog(Level level, String message) {
		
		LoggerManager.tracedLog(level, message, null);
	}
	
}
