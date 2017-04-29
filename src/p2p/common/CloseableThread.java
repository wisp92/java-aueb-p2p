package p2p.common;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.stream.Collectors;

/**
 * A CloseableThread object can be used instead of a simple
 * {@link Thread} object when the thread should implement a close
 * method after its execution.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public abstract class CloseableThread extends Thread implements Closeable {

	/**
	 * Returns the number of active threads of a {@link ThreadGroup}
	 * object.
	 *
	 * @param group
	 *        The group that contains the threads.
	 * @return The number of active thread in the group.
	 */
	public static final int countActive(ThreadGroup group) {

		return CloseableThread.getActive(group).size();

	}

	/**
	 * Returns the active threads of a {@link ThreadGroup} object.
	 *
	 * @param group
	 *        The group that contains the threads.
	 * @return An {@link ArrayDeque} object of the threads.
	 */
	public static final ArrayDeque<Thread> getActive(ThreadGroup group) {

		return new ArrayDeque<>(Thread.getAllStackTraces().keySet().parallelStream()
		        .filter(x -> x.getThreadGroup() == group).collect(Collectors.toList()));

	}

	/**
	 * Interrupts the active threads of a {@link ThreadGroup} object.
	 *
	 * @param group
	 *        The group that contains the threads to be interrupted.
	 */
	public static final void interrupt(ThreadGroup group) {

		ArrayDeque<Thread> active_threads = CloseableThread.getActive(group);

		if (active_threads.size() > 0) {
			for (Thread thread : active_threads) {
				thread.interrupt();
			}
		}

	}

	/**
	 * Allocates a new CloseableThread object.
	 *
	 * @param group
	 *        The {@link ThreadGroup} object that this thread belongs
	 *        to.
	 * @param name
	 *        The name of this Thread.
	 */
	public CloseableThread(ThreadGroup group, String name) {
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

		} catch (IOException ex) {
			LoggerManager.getDefault().getLogger(this.getName()).severe(ex.toString());
		}

	}

}
