package p2p.utilities.testing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.components.Configuration;
import p2p.components.peers.Peer;
import p2p.utilities.LoggerManager;

/**
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TestHelper {

	/*
	 * TODO Should be read from a properties file.
	 */

	/**
	 * The default list file that contains the locations of the available shared
	 * files.
	 */
	public static final String default_sample_list_path = "shared/sample/files_list.txt";

	/**
	 * Returns a list of the files contained in the default list file.
	 *
	 * @return A list of shared files.
	 */
	public static List<File> getDefaultSharedFiles() {

		return TestHelper.getDefaultSharedFiles(Paths
		        .get(Configuration.getDefault().getString("sample_list_path", TestHelper.default_sample_list_path)));

	}

	/**
	 * Returns a list of the files contained in the list file.
	 *
	 * @param sample_list_path
	 *            the path to a list file that contains the locations of the
	 *            available shared files.
	 * @return A list of shared files.
	 */
	public static List<File> getDefaultSharedFiles(final Path sample_list_path) {

		final File sample_directory = sample_list_path.getParent().toFile();

		try {

			/*
			 * Created a list of files by reading the list.
			 */

			return Files.lines(sample_list_path).parallel().map(x -> new File(sample_directory, x))
			        .filter(x -> x.isFile()).collect(Collectors.toList());

		} catch (final IOException ex) {

			LoggerManager.tracedLog(Level.SEVERE, "List of files could not be read.", ex);

		}

		return null;

	}

	/**
	 * Initialized a new shared directory with random files from the provided
	 * file list.
	 *
	 * @param peer
	 *            The peer that is going to use the shared directory.
	 * @param sample_list_path
	 *            the path to a list file that contains the locations of the
	 *            available shared files.
	 * @param shared_directory_path
	 *            The path to the shared directory.
	 * @param min_sample_size
	 *            The minimum number of files the are going to be selected at
	 *            random. More files may be selected. Less files can only be
	 *            selected if the list does not provide enough files for the
	 *            selection process.
	 * @return True If the peer's shared directory was updated successfully.
	 */
	public static boolean newSharedDirectory(final Peer peer, final Path sample_list_path,
	        final String shared_directory_path, final int min_sample_size) {

		/*
		 * Check that the directory exists and is empty.
		 */

		final File shared_directory = new File(shared_directory_path);

		if (!shared_directory.isDirectory()) {

			if (shared_directory.exists()) {

				LoggerManager.tracedLog(Level.SEVERE,
				        String.format(
				                "The path <%s> can not be a shared directory because it point to an existing file.",
				                shared_directory.getAbsolutePath()));

				return false;

			}

			shared_directory.mkdirs();

		}
		else if (shared_directory.listFiles().length > 0) {

			LoggerManager.tracedLog(Level.SEVERE,
			        String.format("The directory <%s> can not be a shared directory because it is not empty.",
			                shared_directory.getAbsolutePath()));

			return false;

		}

		/*
		 * Copy random files from the files list.
		 */

		final List<File> files_list = TestHelper.getDefaultSharedFiles(sample_list_path);

		if (files_list != null) {

			/*
			 * Shuffles the list.
			 */

			Collections.shuffle(files_list);

			/*
			 * Selects a random number of files an copies them to the shared
			 * directory's location.
			 */

			files_list.parallelStream()
			        .limit(new Random().nextInt(files_list.size() - min_sample_size) + min_sample_size).forEach(x ->
			{

				        try {

					        Files.copy(x.toPath(), new File(shared_directory, x.getName()).toPath());

				        } catch (final IOException ex) {
					        LoggerManager.tracedLog(Level.SEVERE, String.format(
					                "The file <%s> could not be copied to the shared directory.", x.getName()), ex);
				        }

			        });

		}

		return peer.setSharedDirectory(shared_directory_path);

	}

	/**
	 * Initialized a new shared directory with random files from the default
	 * file list.
	 *
	 * @param peer
	 *            The peer that is going to use the shared directory.
	 * @param shared_directory_path
	 *            The path to the shared directory.
	 * @param min_sample_size
	 *            The minimum number of files the are going to be selected at
	 *            random. More files may be selected. Less files can only be
	 *            selected if the list does not provide enough files for the
	 *            selection process.
	 * @return True If the peer's shared directory was updated successfully.
	 */
	public static boolean newSharedDirectory(final Peer peer, final String shared_directory_path,
	        final int min_sample_size) {

		return TestHelper.newSharedDirectory(peer,
		        Paths.get(
		                Configuration.getDefault().getString("sample_list_path", TestHelper.default_sample_list_path)),
		        shared_directory_path, min_sample_size);
	}	

}
