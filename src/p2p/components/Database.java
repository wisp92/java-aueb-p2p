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
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import p2p.utilities.LoggerManager;

/**
 * A Database object acts as an interface through which transactions can be made
 * with an SQLite database, using the SQLite JDBC driver.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @see <a href="https://github.com/xerial/sqlite-jdbc">SQLite JDBC</a>
 */
public abstract class Database implements Closeable {

	/**
	 * A Database#SchemaOperation enumeration indicates the operation that
	 * should take place to the associated table in order to fix an
	 * inconsistency. Used by the {@link Database#fix fix()} method.
	 */
	private enum SchemaOperation {
		/**
		 * Indicates that no action should be taken for the specified table.
		 */
		NONE,
		/**
		 * Indicates that the table should be removed from the database.
		 */
		DROP,
		/**
		 * Indicates that the is inconsistent with the database's schema should
		 * modified.
		 */
		UPDATE;
	}

	/**
	 * A Database#SchemaSpeciaName enumeration indicates special column names
	 * that are used to describe metadata of a table.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	protected enum SpecialColumn {
		/**
		 * Indicates the primary keys of the table as String of comma separated
		 * column names.
		 */
		PRIMARY_KEY("<PRIMARY_KEY>");

		/**
		 * A set of all special column names.
		 */
		public static final Set<String> COLUMN_NAMES = Stream.of(SpecialColumn.values()).map(x -> x.getName())
		        .collect(Collectors.toSet());

		private final String name;

		private SpecialColumn(final String name) {

			this.name = name;
		}

		/**
		 * @return Then column's name.
		 */
		public final String getName() {

			return this.name;
		}

	}

	private final String path;

	private Connection connection			 = null;
	private boolean	   is_database_corrupted = false;

	/**
	 * Allocates a new Database object binded to the path's location. If the
	 * database file does not exist it is created automatically.
	 *
	 * @param path
	 *            The path to the SQLite's database file.
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
		 * Release any resources, eg. the driver's connection.
		 */
		this.disconnect();
	}

	/**
	 * Tries to establish a connection with the database. The location of the
	 * database's file is specified during the the database's allocation.
	 *
	 * @return True If a connection with the database established successfully.
	 */
	public final boolean connect() {

		try {

			if (!this.isConnected()) {

				/*
				 * Connect to the database using the SQLite JDBC driver. The
				 * corresponding JAR file should be referenced in the build's
				 * classpath.
				 */
				this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", this.path));
				return true;

			}

		} catch (final SQLException ex) {

			LoggerManager.tracedLog(Level.SEVERE,
			        String.format("A connection to the database <%s> could not be established.", this.path), ex);

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

			if (this.connection != null) {
				
				this.connection.close();
				return true;
				
			}

		} catch (final SQLException ex) {

			LoggerManager.tracedLog(Level.WARNING, "The connection to the database couldn't be terminated properly.",
			        ex);

		}

		return false;

	}

	/**
	 * Fixes any database's inconsistencies with the schema.
	 *
	 * @param schema
	 *            The schema as described by the getSchema() method.
	 * @return True If database is inconsistent.
	 */
	public boolean fix(final Map<String, Map<String, String>> schema) {

		/*
		 * Store the final operations that should be applied to each table in
		 * order to fix the database.
		 */
		final Map<String, SchemaOperation> is_ok_tables = schema.keySet().stream().parallel()
		        .collect(Collectors.toMap(x -> x, x -> SchemaOperation.NONE));

		try (final Connection tmp_connection = this.getConnection()) {

			this.is_database_corrupted = true;

			final DatabaseMetaData metadata = tmp_connection.getMetaData();

			/*
			 * Retrieve metadata of the database's tables.
			 */
			try (final ResultSet tables = metadata.getTables(null, null, "%", null)) {

				while (tables.next()) {

					/*
					 * Check the schema of the specific table.
					 */
					final String table_name = tables.getString("TABLE_NAME");

					if (is_ok_tables.containsKey(table_name)) {

						/*
						 * Create a map from column names to the status of the
						 * column. Starts by assuming that all columns are
						 * inconsistent.
						 */
						final HashMap<String, Boolean> is_ok_columns = new HashMap<>(schema.get(table_name).keySet()
						        .parallelStream().filter(x -> !x.equals(SpecialColumn.PRIMARY_KEY.getName()))
						        .collect(Collectors.toMap(x -> x, x -> Boolean.FALSE)));

						/*
						 * Retrieve metadata of the table's columns.
						 */
						try (final ResultSet columns = metadata.getColumns(null, null, table_name, "%")) {

							/*
							 * This loop will stop either when all columns where
							 * checked for inconsistencies or an inconsistency
							 * was found. In the later case the table's
							 * operation should be set to UPDATE.
							 */
							while ((is_ok_tables.get(table_name) == SchemaOperation.NONE) && columns.next()) {

								final String column_name = columns.getString("COLUMN_NAME");

								if (is_ok_columns.containsKey(column_name)) {

									final String expected_data_type = schema.get(table_name).get(column_name);

									/*
									 * Check If the type of the specific column
									 * is consistent with the schema's
									 * description.
									 */
									if (columns.getString("TYPE_NAME").equals(expected_data_type)) {
										is_ok_columns.put(column_name, Boolean.TRUE);
									}
									else {
										is_ok_tables.put(table_name, SchemaOperation.UPDATE);
									}

								}
								else {

									/*
									 * Check If the column's name is described
									 * in the database's schema.
									 */
									is_ok_tables.put(table_name, SchemaOperation.UPDATE);

								}

							}

						}

						/*
						 * Check If one ore more columns described in the
						 * database's schema are absent.
						 */
						if (is_ok_columns.values().stream().parallel().filter(x -> x.booleanValue())
						        .count() < is_ok_columns.size()) {

							is_ok_tables.put(table_name, SchemaOperation.UPDATE);

						}

					}
					else {

						/*
						 * Check If the table's name is described in the
						 * database's schema.
						 */
						is_ok_tables.put(table_name, SchemaOperation.DROP);

					}

				}

			}

			for (final String table_name : is_ok_tables.keySet()) {

				/*
				 * Perform a drop of the table if the determined operation is
				 * either UPDATE or SROP.
				 */
				if (is_ok_tables.get(table_name) != SchemaOperation.NONE) {

					try (final PreparedStatement statement = tmp_connection
					        .prepareStatement(String.format("DROP TABLE IF EXISTS `%s`;", table_name))) {

						statement.executeUpdate();

					}

				}

				/*
				 * Create the table If it does not exist and the determined
				 * operation is either NONE or UPDATE.
				 */
				if (is_ok_tables.get(table_name) != SchemaOperation.DROP) {

					/*
					 * Set table's primary key if any is specified in the
					 * database's schema.
					 */
					final String primary_key = schema.get(table_name).containsKey(SpecialColumn.PRIMARY_KEY.getName())
					        ? String.format(", PRIMARY KEY (%s)",
					                schema.get(table_name).get(SpecialColumn.PRIMARY_KEY.getName()))
					        : "";

					/*
					 * Create the complete insert command based on the
					 * database's schema.
					 */
					final String sql_query = String.format("CREATE TABLE IF NOT EXISTS `%s` (%s);", table_name,
					        schema.get(table_name).entrySet().stream()
					                .filter(x -> !SpecialColumn.COLUMN_NAMES.contains(x.getKey()))
					                .map(x -> String.format("`%s` %s", x.getKey(), x.getValue()))
					                .collect(Collectors.joining(", ")) + primary_key);

					try (PreparedStatement statement = tmp_connection.prepareStatement(sql_query)) {

						statement.executeUpdate();

					}

				}

			}

			this.is_database_corrupted = false;

		} catch (final SQLException ex) {

			LoggerManager.tracedLog(Level.SEVERE,
			        "An exception occurred while fixing inconsistencies with the database's schema.", ex);

		}

		return !this.is_database_corrupted;

	}

	/**
	 * Establishes a connection with the database and gives direct access to the
	 * caller. Should be used with caution if not overrided by a subclasses.
	 *
	 * @return An existing or new connection to the database.
	 */
	public Connection getConnection() {

		/*
		 * Establishes a connection if one does not exist.
		 */
		this.connect();

		return this.connection;

	}

	/**
	 * Returns the schema of the database as a Map with keys the table names and
	 * values the table descriptions. A table description is itself a Map object
	 * with keys the column names and values the data types of the columns. A
	 * special key namely <PRIMARY_KEY> can also be used to indicate the column
	 * (or columns, separated by <,>) that is going to be used as primary key of
	 * the table.
	 *
	 * @return The schema of database.
	 */
	public abstract Map<String, Map<String, String>> getSchema();

	/**
	 * @return True If a connection to the database is currently established.
	 */
	public final boolean isConnected() {

		try {

			return (this.connection != null) && !this.connection.isClosed();

		} catch (final SQLException ex) {

			LoggerManager.tracedLog(Level.WARNING,
			        "An exception occurred while checking if a connection with the database is established.", ex);

		}

		return false;

	}

	/**
	 * Checks if the database is marked as corrupted. Errors might occur during
	 * the execution of the queries if no action is taken to fix a corrupted
	 * database. Of course this is just an indication. It is up to the caller
	 * decide if to take advantage of this information and try to fix the
	 * database.
	 *
	 * @return True If the database is corrupted and should be fixed before any
	 *         queries can be executed.
	 */
	public final boolean isCorrupted() {

		return this.is_database_corrupted;

	}

	/**
	 * Marks the database as corrupted.
	 */
	protected final void setAsCorrupted() {

		this.is_database_corrupted = true;
	}

}
