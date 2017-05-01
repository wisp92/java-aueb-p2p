package p2p.common.utilities;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
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
	 * The default logging level. Applies to both the loggers and the
	 * handler.
	 */
	public static Level	  default_logging_level	= Level.OFF;
	/**
	 * Specifies if the output is going to propagate to the logger's
	 * parent. The parent logger by default is handled by a
	 * {@link ConsoleHandler}.
	 */
	public static boolean default_propagate		= false;
	
	private static LoggerManager default_logger_manager;
	
	/**
	 * @return The default logger manager.
	 */
	public static LoggerManager getDefault() {
		
		return LoggerManager.default_logger_manager;
	}
	
	/**
	 * Logs the specified exception to the logger.
	 * 
	 * @param logger
	 *        The logger that is going to log the exception.
	 * @param level
	 *        The level of severity.
	 * @param ex
	 *        The exception to be logged.
	 */
	public static synchronized void logException(Logger logger, Level level, Exception ex) {
		
		logger.log(level, String.format("%s\n%s", ex.getMessage(), Arrays.asList(ex.getStackTrace()).stream() //$NON-NLS-1$
		        .map(x -> x.toString()).reduce((x, y) -> String.format("%s\n%s", x, y)).get())); //$NON-NLS-1$
		
	}
	
	/**
	 * Replaces the default logger manager of the application.
	 *
	 * @param logger_manager
	 *        The new logger manager to set as default.
	 * @return The previous logger manager.
	 */
	public static LoggerManager setAsDefault(LoggerManager logger_manager) {
		
		LoggerManager last_logger_manager = LoggerManager.default_logger_manager;
		LoggerManager.default_logger_manager = logger_manager;
		
		return last_logger_manager;
	}

	private Handler	handler;
	private Level	logging_level = LoggerManager.default_logging_level;
	
	private boolean propagate = LoggerManager.default_propagate;
	
	/**
	 * Allocates a new LoggerManager object.
	 *
	 * @param handler
	 *        The default handler of the loggers.
	 */
	public LoggerManager(Handler handler) {
		
		this.handler = handler;
	}
	
	/**
	 * Applies the current configuration and returns the requested
	 * logger.
	 *
	 * @param name
	 *        The name of the requested logger.
	 * @return The requested logger.
	 */
	public Logger getLogger(String name) {
		
		Logger logger = Logger.getLogger(name);
		if (logger.getUseParentHandlers() != this.propagate) {
			logger.setUseParentHandlers(this.propagate);
		}
		if (logger.getLevel() != this.logging_level) {
			logger.setLevel(this.logging_level);
		}
		
		Handler first_handler = null;
		ArrayDeque<Handler> handlers = new ArrayDeque<>(Arrays.asList(logger.getHandlers()));
		
		if (!handlers.isEmpty()) {
			first_handler = handlers.removeFirst();
		}
		
		for (@SuppressWarnings("hiding")
		Handler handler : handlers) {
			logger.removeHandler(handler);
		}
		
		if (!this.handler.equals(first_handler)) {
			if (first_handler != null) {
				logger.removeHandler(first_handler);
			}
			logger.addHandler(this.handler);
		}
		
		return logger;
		
	}
	
	/**
	 * Replaces the current handler. The loggers are going to be
	 * updated the next time that are requested.
	 *
	 * @param handler
	 *        The new handler.
	 */
	public void setHandler(Handler handler) {
		
		if (this.logging_level != handler.getLevel()) {
			this.logging_level = handler.getLevel();
		}
		this.handler = handler;
		
	}
	
	/**
	 * Updates the current logging level. The handler is updated
	 * immediately while the loggers are updated the next time that
	 * are requested.
	 *
	 * @param logging_level
	 *        The new logging level.
	 */
	public void setLoggingLevel(Level logging_level) {
		
		if (this.handler.getLevel() != logging_level) {
			this.handler.setLevel(logging_level);
		}
		this.logging_level = logging_level;
		
	}
	
	/**
	 * Updates the current propagate value. The loggers are going to
	 * be updated the next time that are requested.
	 *
	 * @param propagate
	 *        A boolean value that indicates if propagation to the
	 *        parent logger is allowed.
	 */
	public void setPropagate(boolean propagate) {
		
		this.propagate = propagate;
	}
	
}
