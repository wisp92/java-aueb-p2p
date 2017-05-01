package p2p.testing;

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

import p2p.common.utilities.LoggerManager;
import p2p.peer.Peer;

/**
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class TestUtils {

	public static final Path default_sample_list_path = Paths.get("shared/sample/files_list.txt"); //$NON-NLS-1$

	public static boolean newSharedDirectory(Peer peer, String shared_directory_path, int min_sample_size) {

		/*
		 * Check that the directory exists and is empty.
		 */

		File shared_directory = new File(shared_directory_path);

		if (!shared_directory.isDirectory()) {

			if (shared_directory.exists()) return false;

			shared_directory.mkdirs();

		}
		else if (shared_directory.listFiles().length > 0) return false;

		/*
		 * Copy random files from the files list.
		 */

		File sample_directory = TestUtils.default_sample_list_path.getParent().toFile();

		List<File> files_list;
		
		try {
			
			files_list = Files.lines(TestUtils.default_sample_list_path).parallel()
			        .map(x -> new File(sample_directory, x)).filter(x -> x.isFile()).collect(Collectors.toList());

			Collections.shuffle(files_list);

			files_list.parallelStream()
			        .limit(new Random().nextInt(files_list.size() - min_sample_size) + min_sample_size).forEach(x -> {
				        
				        try {
					        
					        Files.copy(x.toPath(), new File(shared_directory, x.getName()).toPath());
					        
				        } catch (IOException ex) {
					        LoggerManager.logException(LoggerManager.getDefault().getLogger(TestUtils.class.getName()),
					                Level.SEVERE, ex);
				        }
				        
			        });
			
		} catch (IOException ex) {
			LoggerManager.logException(LoggerManager.getDefault().getLogger(TestUtils.class.getName()), Level.SEVERE,
			        ex);
		}

		return peer.setSharedDirectory(shared_directory_path);

	}

	/**
	 *
	 */
	public TestUtils() {
		// TODO Auto-generated constructor stub
	}

}
