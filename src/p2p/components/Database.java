package p2p.components;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import p2p.utilities.LoggerManager;

/**
 * A Database object acts as an interface through which transactions can be made
 * with an SQLite database, using the SQLite JDBC driver.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @see <a href="https://github.com/xerial/sqlite-jdbc">SQLite JDBC</a>
 */
public abstract class Database implements Closeable {

	/*
	 * A Database#SchemaOperation enumeration indicates the operation that
	 * should take place to the associated table in order to fix an
	 * inconsistency. Used by the {@link Database#fix fix()} method.
	 */
	private enum SchemaOperation {
		UNKNOWN, DROP, UPDATE;
	}

	/**
	 * A Database#SchemaSpeciaName enumeration indicates special column names
	 * that are used to describe metadata of a table.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	protected enum SchemaSpecialName {
		/**
		 * Indicates the primary keys of the table.
		 */
		PRIMARY_KEY("<PRIMARY_KEY>"); //$NON-NLS-1$

		private final String name;

		private SchemaSpecialName(final String name) {
			this.name = name;
		}

		/**
		 * @return Then column name.
		 */
		public final String getName() {

			return this.name;
		}

	}

	private final String path;

	private Connection connection	= null;
	private boolean	   is_corrupted	= false;

	/**
	 * Allocates a new Database object binded to the path's location. If the
	 * database file does not exist it is created automatically.
	 *
	 * @param path
	 *            The path to the database file.
	 */
	public Database(final String path) {
		this.path = path;
	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		/*
		 * Make sure that the connection is closed.
		 */
		this.disconnect();
	}

	/**
	 * Tries to establish a connection with the database. The location of the
	 * database's file is specified during the the database's allocation.
	 *
	 * @return True If a connection with database was established successfully.
	 */
	public final boolean connect() {

		try {

			if (!this.isConnected()) {

				/*
				 * Connect to the database using the SQLite JDBC driver. The
				 * corresponding JAR file should be referenced in the
				 * compilation classpath.
				 */
				this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", this.path)); //$NON-NLS-1$

				return true;

			}

		} catch (final SQLException ex) {
			LoggerManager.tracedLog(Level.SEVERE,
			        String.format("A connection to the database <%s> could not be established.", this.path), ex); //$NON-NLS-1$
		}

		return false;

	}

	/**
	 * Tries to close the currently open connection to the database if one is
	 * established.
	 *
	 * @return True If the connection was closed successfully.
	 */
	public final boolean disconnect() {

		try {

			if (this.isConnected()) {

				this.connection.close();

				return true;

			}

		} catch (final SQLException ex) {
			LoggerManager.tracedLog(Level.WARNING, "The connection to the database could not be closed properly.", ex); //$NON-NLS-1$
		}

		return false;

	}

	/**
	 * Fixes any inconsistencies with the schema of the database.
	 *
	 * @param schema
	 *            The schema as described by the getSchema() method.
	 * @return True If any inconsistencies were fixed.
	 */
	public boolean fix(final HashMap<String, HashMap<String, String>> schema) {

		final HashMap<String, SchemaOperation> is_ok_tables = new HashMap<>(
		        schema.keySet().stream().parallel().collect(Collectors.toMap(x -> x, x -> SchemaOperation.UNKNOWN)));

		try (@SuppressWarnings("hiding")
		final Connection connection = this.getConnection()) {

			this.is_corrupted = true;

			final DatabaseMetaData metadata = connection.getMetaData();

			/*
			 * Retrieve metadata about the database's tables.
			 */

			try (ResultSet tables = metadata.getTables(null, null, "%", null)) { //$NON-NLS-1$

				while (tables.next()) {

					/*
					 * Check table schema.
					 */

					final String table_name = tables.getString("TABLE_NAME"); //$NON-NLS-1$

					if (is_ok_tables.containsKey(table_name)) {

						/*
						 * Create a map from column names to the status of the
						 * column. Start by assuming that all columns are
						 * inconsistent.
						 */

						final HashMap<String, Boolean> is_ok_columns = new HashMap<>(schema.get(table_name).keySet()
						        .parallelStream().filter(x -> !x.equals(SchemaSpecialName.PRIMARY_KEY.getName()))
						        .collect(Collectors.toMap(x -> x, x -> false)));

						/*
						 * Retrieve metadata about the table's columns.
						 */

						try (ResultSet columns = metadata.getColumns(null, null, table_name, "%")) { //$NON-NLS-1$

							/*
							 * This loop will stop either when all columns where
							 * checked for inconsistencies or an inconsistency
							 * was found and the operation to fix it was
							 * determined.
							 */

							while ((is_ok_tables.get(table_name) == SchemaOperation.UNKNOWN) && columns.next()) {

								final String column_name = columns.getString("COLUMN_NAME"); //$NON-NLS-1$

								if (is_ok_columns.containsKey(column_name)) {

									final String expected_data_type = schema.get(table_name).get(column_name);

									/*
									 * Check IF the type of that column is
									 * consistent with the schema's description
									 * and update the table otherwise.
									 */

									if (columns.getString("TYPE_NAME") //$NON-NLS-1$
									        .equals(expected_data_type)) {
										is_ok_columns.put(column_name, true);
									}
									else {
										is_ok_tables.put(table_name, SchemaOperation.UPDATE);
									}

								}
								else {

									/*
									 * If the column's name is not described in
									 * the original schema update the table.
									 */

									is_ok_tables.put(table_name, SchemaOperation.UPDATE);

								}

							}

						}

						if (is_ok_columns.values().stream().parallel().filter(x -> x).count() < is_ok_columns.size()) {

							/*
							 * If one ore more columns described in the original
							 * schema are absent update the table.
							 */

							is_ok_tables.put(table_name, SchemaOperation.UPDATE);

						}

					}
					else {

						/*
						 * If the table's name is not described in the original
						 * schema drop the table.
						 */

						is_ok_tables.put(table_name, SchemaOperation.DROP);

					}

				}

			}

			for (final String table_name : is_ok_tables.keySet()) {

				if (is_ok_tables.get(table_name) != SchemaOperation.UNKNOWN) {

					/*
					 * Perform a drop of the table if the determined operation
					 * is either Update or Drop.
					 */

					try (PreparedStatement statement = connection
					        .prepareStatement(String.format("DROP TABLE IF EXISTS `%s`;", //$NON-NLS-1$
					                table_name))) {

						statement.executeUpdate();

					}

				}

				if (is_ok_tables.get(table_name) != SchemaOperation.DROP) {

					/*
					 * Create table If not exists and the determined operation
					 * is either Unknown or Update.
					 */

					final String primary_key = schema.get(table_name)
					        .containsKey(SchemaSpecialName.PRIMARY_KEY.getName())
					                ? String.format(", PRIMARY KEY (%s)", //$NON-NLS-1$
					                        schema.get(table_name).get(SchemaSpecialName.PRIMARY_KEY.getName()))
					                : ""; //$NON-NLS-1$

					final String sql_query = String.format("CREATE TABLE IF NOT EXISTS `%s` (%s);", //$NON-NLS-1$
					        table_name,
					        schema.get(table_name).entrySet().stream()
					                .filter(x -> !x.getKey().equals(SchemaSpecialName.PRIMARY_KEY.getName()))
					                .map(x -> String.format("`%s` %s", //$NON-NLS-1$
					                        x.getKey(), x.getValue()))
					                .collect(Collectors.joining(", ")) //$NON-NLS-1$
					                + primary_key);

					try (PreparedStatement statement = connection.prepareStatement(sql_query)) {

						statement.executeUpdate();

					}

				}

			}

			this.is_corrupted = false;

		} catch (final SQLException ex) {
			LoggerManager.tracedLog(Level.SEVERE,
			        "An exception occurred while trying to fix inconsistencies in the database.", //$NON-NLS-1$
			        ex);
		}

		return !this.is_corrupted;

	}

	/**
	 * Establishes a connection with the database and gives direct access to the
	 * caller. Should be used with caution or overrided by subclasses.
	 *
	 * @return An existing or new connection to the database.
	 */
	public Connection getConnection() {

		/*
		 * Make sure that a connection exists.
		 */
		this.connect();

		return this.connection;

	}

	/**
	 * Returns the schema of the database as a Map with keys the table names and
	 * values the table descriptions. A table description is itself a Map object
	 * with keys the column names and values the data types of the columns.A
	 * special key <PRIMARY_KEY> can also be used to indicate the column (or
	 * columns, separated by <,>) that is going to be used as primary key of the
	 * table.
	 *
	 * @return The schema of database.
	 */
	public abstract HashMap<String, HashMap<String, String>> getSchema();

	/**
	 * @return True If a connection to the database is currently established.
	 */
	public final boolean isConnected() {

		try {

			return (this.connection != null) && !this.connection.isClosed();

		} catch (final SQLException ex) {
			LoggerManager.tracedLog(Level.WARNING,
			        "An exception occurred while checking if a connection with the database is established.", ex); //$NON-NLS-1$
		}

		return false;

	}

	/**
	 * @return True If the database is corrupted and should be fixed before it
	 *         is used again.
	 */
	public final boolean isCorrupted() {

		return this.is_corrupted;

	}

	/**
	 * Marks the database as corrupted. Should only be called when the state can
	 * definitely be determined.
	 */
	protected final void setAsCorrupted() {

		this.is_corrupted = true;
	}

}
