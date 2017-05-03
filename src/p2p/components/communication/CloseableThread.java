package p2p.components.communication;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.utilities.LoggerManager;

/**
 * A CloseableThread object can be used instead of a simple {@link Thread}
 * object when the thread should implement a close method after its execution.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class CloseableThread extends Thread implements Closeable {

	/**
	 * Returns the number of active threads of a {@link ThreadGroup} object.
	 *
	 * @param group
	 *            The group that contains the threads.
	 * @return The number of active thread in the group.
	 */
	public static final int countActive(final ThreadGroup group) {

		return CloseableThread.getActive(group).size();

	}

	/**
	 * Returns the active threads of a {@link ThreadGroup} object.
	 *
	 * @param group
	 *            The group that contains the threads.
	 * @return An {@link ArrayDeque} object of the threads.
	 */
	public static final List<Thread> getActive(final ThreadGroup group) {

		return Thread.getAllStackTraces().keySet().parallelStream()
		        .filter(x -> (x.getThreadGroup() == group) && !x.isInterrupted()).collect(Collectors.toList());

	}

	/**
	 * Interrupts the active threads of a {@link ThreadGroup} object.
	 *
	 * @param group
	 *            The group that contains the threads to be interrupted.
	 */
	public static final void interrupt(final ThreadGroup group) {

		CloseableThread.getActive(group).parallelStream().forEach(x -> x.interrupt());

	}

	/**
	 * Allocates a new CloseableThread object.
	 *
	 * @param group
	 *            The {@link ThreadGroup} object that this thread belongs to.
	 * @param name
	 *            The name of this Thread.
	 */
	public CloseableThread(final ThreadGroup group, final String name) {
		super(group, name);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#interrupt()
	 */
	@Override
	public void interrupt() {

		super.interrupt();

		try {

			this.close();

		} catch (final IOException ex) {
			LoggerManager.tracedLog(this, Level.WARNING, "The thread could not be closed properly.", ex); //$NON-NLS-1$
		}

	}

}
