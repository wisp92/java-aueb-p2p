package p2p.components.communication;

import java.util.logging.Level;

import p2p.components.Configuration;
import p2p.utilities.LoggerManager;

/**
 * A ServerCleaner object is responsible periodically to check for loose threads
 * and terminate them.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class ChannelCleaner extends Thread {

	/**
	 * The default interval at which the cleaner is going to check the threads.
	 */
	public static final int default_cleaning_interval = 20000;

	private final int		  cleaning_interval;
	private final ThreadGroup group;

	/**
	 * Allocates a new ServerCleaner object with default cleaning interval.
	 *
	 * @param group
	 *            The group of threads to check.
	 */
	public ChannelCleaner(final ThreadGroup group) {
		this(group, 0);
	}

	/**
	 * Allocates a new ServerCleaner object.
	 *
	 * @param group
	 *            The group of threads to check.
	 * @param cleaning_interval
	 *            The interval at which the cleaner is going to check the
	 *            threads.
	 */
	public ChannelCleaner(final ThreadGroup group, final int cleaning_interval) {
		super(CloseableThread.newThreadGroup(group.getParent(), "Cleaners"),
		        String.format("%s.Cleaner", group.getParent().getName()));

		this.cleaning_interval = cleaning_interval > 0 ? cleaning_interval
		        : Configuration.getDefault().getInteger("cleaning_interval", ChannelCleaner.default_cleaning_interval);
		this.group = group;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		try {

			while (true) {

				/*
				 * Wait for the specified interval.
				 */
				Thread.sleep(this.cleaning_interval);

				/*
				 * For each in the group, suppose it is a Channel object, call
				 * the clean method in parallel.
				 */

				CloseableThread.getActive(this.group).parallelStream().forEach(x -> {
					if (x instanceof Channel) {
						((Channel) x).clean(this.cleaning_interval);
					}
				});

			}

		} catch (@SuppressWarnings("unused") final InterruptedException ex) {

			LoggerManager.tracedLog(Level.INFO, "The server cleaner was stopped.");

		}

	}

}
