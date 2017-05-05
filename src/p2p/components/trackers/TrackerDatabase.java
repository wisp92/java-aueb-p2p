package p2p.components.trackers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import p2p.components.Database;
import p2p.components.common.Credentials;
import p2p.components.common.Pair;
import p2p.utilities.LoggerManager;

/**
 * A TrackerDatabase object implements an interface for accessing the tracker's
 * database. It is responsible for retrieving and storing user information to
 * the database.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
class TrackerDatabase extends Database {

	/**
	 * Allocates a new TrackerDatabase object binded to the path's location. If
	 * the database file does not exist it is going to be created automatically.
	 *
	 * @param path
	 *            The path to the database's file.
	 */
	public TrackerDatabase(final String path) {

		super(path);
	}

	/*
	 * TODO Add clear database method.
	 */

	public final boolean addDownload(final String username) {

		if (!this.isCorrupted()) {

			try (Connection connection = this.getConnection();
			        PreparedStatement statement = connection.prepareStatement(
			                "UPDATE `users` SET `count_downloads` = `count_downloads` + 1 WHERE `username` = ?")) {

				statement.setString(1, username);

				return statement.executeUpdate() >= 1;

			} catch (final SQLException ex) {
				LoggerManager.tracedLog(Level.SEVERE,
				        "An exception occurred while trying to update information about a user.", ex);
			}

		}

		return false;

	}

	/*
	 * (non-Javadoc)
	 * @see p2p.database.Database#getSchema()
	 */
	@Override
	public final Map<String, Map<String, String>> getSchema() {

		final HashMap<String, String> tbl_users = new HashMap<>();
		tbl_users.put("username", "VARCHAR"); //$NON-NLS-2$
		tbl_users.put("password", "VARCHAR"); //$NON-NLS-2$
		tbl_users.put("count_downloads", "INT"); //$NON-NLS-2$
		tbl_users.put(SpecialColumn.PRIMARY_KEY.getName(), "`username`");

		final HashMap<String, Map<String, String>> schema = new HashMap<>();
		schema.put("users", tbl_users);

		return schema;

	}

	/**
	 * Searches the database for the specified user indexed by his username and
	 * returns any stored information.
	 *
	 * @param username
	 *            The username of the user.
	 * @return The credentials of the user if they exist in the database and
	 *         null otherwise.
	 */
	public final Pair<Credentials, Integer> getUser(final String username) {

		if (!this.isCorrupted()) {

			try (Connection connection = this.getConnection();
			        PreparedStatement statement = connection
			                .prepareStatement("SELECT * FROM `users` WHERE `username` = ?")) {

				/*
				 * TODO Check if setString() escapes parameters.
				 */
				statement.setString(1, username);

				try (ResultSet results = statement.executeQuery()) {

					/*
					 * Returns only the first occurrence. Should be only one if
					 * the database is fixed.
					 */

					if (results.next()) {

						final Credentials user_credentials = new Credentials(results.getString("username"),
						        results.getString("password"));
						final Integer count_downloads = new Integer(results.getInt("count_downloads"));

						if (!results.next()) return new Pair<>(user_credentials, count_downloads);

						/*
						 * If more results where found then the database should
						 * be marked as corrupted.
						 */

						this.setAsCorrupted();

					}

				}

			} catch (final SQLException ex) {
				LoggerManager.tracedLog(Level.WARNING,
				        "An exception occurred while trying to get information about a user.", ex);
			}

		}

		return null;

	}

	/**
	 * Stores the user's information in the database.
	 *
	 * @param username
	 *            The user's username.
	 * @param password
	 *            The user's hashed password.
	 * @return If the insertion was successful.
	 */
	public final boolean setUser(final String username, final String password) {

		if (!this.isCorrupted()) {

			try (Connection connection = this.getConnection();
			        PreparedStatement statement = connection.prepareStatement(
			                "INSERT OR IGNORE INTO `users` (`username`, `password`, `count_downloads`) VALUES (?, ?, 0)")) {

				statement.setString(1, username);
				statement.setString(2, password);

				return statement.executeUpdate() == 1;

			} catch (final SQLException ex) {
				LoggerManager.tracedLog(Level.SEVERE,
				        "An exception occurred while trying to store information about a user.", ex);
			}

		}

		return false;

	}

}
