package p2p.common.stubs;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.stream.Collectors;

import p2p.common.LoggerManager;

/**
 * A Database object acts as an interface to perform transaction with
 * an SQLite database by using the SQLite JDBC driver.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 * @see <a href="https://github.com/xerial/sqlite-jdbc">SQLite
 *      JDBC</a>
 */
public abstract class Database implements Closeable {

	private enum SchemaOperation {
		UNKNOWN, DROP, UPDATE;
	}

	/**
	 * A SchemaSpeciaName enumeration indicates that a name can't be
	 * used as the name of a column.
	 *
	 * @author {@literal p3100161 <Joseph Sakos>}
	 */
	protected enum SchemaSpecialName {
		/**
		 * Indicates that the name is used to declare the primary keys
		 * of the table.
		 */
		PRIMARY_KEY("<PRIMARY_KEY>"); //$NON-NLS-1$

		private final String name;

		private SchemaSpecialName(String name) {

			this.name = name;

		}

		/**
		 * @return Then name of the enumeration.
		 */
		public String getName() {

			return this.name;
		}

	}

	private final String path;

	private Connection connection;
	private boolean	   is_corrupted	= false;

	/**
	 * Allocates a new Database object binded to the path's location.
	 * If the database file does not exist it is going to be created
	 * automatically.
	 *
	 * @param path
	 *        The path to the database's file.
	 */
	public Database(String path) {

		this.path = path;

	}

	/*
	 * (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {

		this.disconnect();

	}

	/**
	 * Tries to establish a connection with the database. The location
	 * of the database's file is specified during the the object's
	 * construction.
	 *
	 * @return If a connection with database was established
	 *         successfully.
	 */
	public boolean connect() {

		try {

			if (!this.isConnected()) {

				this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", this.path)); //$NON-NLS-1$

				return true;

			}

		} catch (SQLException ex) {
			LoggerManager.getDefault().getLogger(this.getClass().getName()).warning(ex.toString());
		}

		return false;

	}

	/**
	 * Tries to close the currently open connection to the database if
	 * one is established.
	 *
	 * @return If the connection was closed successfully.
	 */
	public boolean disconnect() {

		try {

			if (this.isConnected()) {

				this.connection.close();

				return true;

			}

		} catch (SQLException ex) {
			LoggerManager.getDefault().getLogger(this.getClass().getName()).warning(ex.toString());
		}

		return false;

	}

	/**
	 * Fixes any inconsistencies with the schema of the database.
	 *
	 * @param schema
	 *        The schema as described by the getSchema() method.
	 * @return If the schema was corrected.
	 */
	public boolean fix(HashMap<String, HashMap<String, String>> schema) {

		HashMap<String, SchemaOperation> is_ok_tables = new HashMap<>(
		        schema.keySet().stream().parallel().collect(Collectors.toMap(x -> x, x -> SchemaOperation.UNKNOWN)));

		try (@SuppressWarnings("hiding")
		Connection connection = this.getConnection()) {

			this.is_corrupted = true;

			DatabaseMetaData metadata = connection.getMetaData();

			try (ResultSet tables = metadata.getTables(null, null, "%", null)) { //$NON-NLS-1$

				while (tables.next()) {

					String table_name = tables.getString("TABLE_NAME"); //$NON-NLS-1$

					if (is_ok_tables.containsKey(table_name)) {

						/*
						 * Check table schema.
						 */

						HashMap<String, Boolean> is_ok_columns = new HashMap<>(schema.get(table_name).keySet().stream()
						        .parallel().filter(x -> !x.equals(SchemaSpecialName.PRIMARY_KEY.getName()))
						        .collect(Collectors.toMap(x -> x, x -> false)));

						try (ResultSet columns = metadata.getColumns(null, null, table_name, "%")) { //$NON-NLS-1$

							while ((is_ok_tables.get(table_name) == SchemaOperation.UNKNOWN) && columns.next()) {

								String column_name = columns.getString("COLUMN_NAME"); //$NON-NLS-1$

								if (is_ok_columns.containsKey(column_name)) {

									String expected_data_type = schema.get(table_name).get(column_name);

									if (columns.getString("TYPE_NAME") //$NON-NLS-1$
									        .equals(expected_data_type)) {

										/*
										 * If the data type is correct
										 * then the column is valid.
										 */
										is_ok_columns.put(column_name, true);

									}
									else {

										/*
										 * If the data type is wrong
										 * then the column is invalid.
										 */
										is_ok_tables.put(table_name, SchemaOperation.UPDATE);

									}

								}
								else {

									/*
									 * If the name of the column is
									 * not contained in the schema
									 * then the column is invalid.
									 */
									is_ok_tables.put(table_name, SchemaOperation.UPDATE);

								}

							}

						}

						if (is_ok_columns.values().stream().parallel().filter(x -> x).count() < is_ok_columns.size()) {

							/*
							 * If one of the columns is missing then
							 * the table is invalid.
							 */
							is_ok_tables.put(table_name, SchemaOperation.UPDATE);

						}

					}
					else {

						/*
						 * If the name of the table is not contained
						 * in the schema then the table is invalid.
						 */
						is_ok_tables.put(table_name, SchemaOperation.DROP);

					}

				}

			}

			for (String table_name : is_ok_tables.keySet()) {

				if (is_ok_tables.get(table_name) != SchemaOperation.UNKNOWN) {

					/*
					 * Drop the table if something is wrong and exit
					 * if the operation fails.
					 */

					try (PreparedStatement statement
					        = connection.prepareStatement(String.format("DROP TABLE IF EXISTS `%s`;", //$NON-NLS-1$
					                table_name))) {

						statement.executeUpdate();

					}

				}

				if (is_ok_tables.get(table_name) != SchemaOperation.DROP) {

					String primary_key = schema.get(table_name).containsKey(SchemaSpecialName.PRIMARY_KEY.getName())
					        ? String.format(", PRIMARY KEY (%s)", //$NON-NLS-1$
					                schema.get(table_name).get(SchemaSpecialName.PRIMARY_KEY.getName()))
					        : ""; //$NON-NLS-1$

					String sql_query = String.format("CREATE TABLE IF NOT EXISTS `%s` (%s);", //$NON-NLS-1$
					        table_name,
					        schema.get(table_name).entrySet().stream()
					                .filter(x -> !x.getKey().equals(SchemaSpecialName.PRIMARY_KEY.getName()))
					                .map(x -> String.format("`%s` %s", //$NON-NLS-1$
					                        x.getKey(), x.getValue()))
					                .collect(Collectors.joining(", ")) //$NON-NLS-1$
					                + primary_key);

					/*
					 * Recreate the table if it is referenced in the
					 * schema and exit if the operation fails.
					 */

					try (PreparedStatement statement = connection.prepareStatement(sql_query)) {

						statement.executeUpdate();

					}

				}

			}

			this.is_corrupted = false;

		} catch (SQLException ex) {

			LoggerManager.getDefault().getLogger(this.getClass().getName()).warning(ex.toString());

		}

		return !this.is_corrupted;

	}

	/**
	 * Creates and returns a connection to the caller.
	 *
	 * @return An existing or new connection.
	 */
	public Connection getConnection() {

		/*
		 * Make sure that a connection exists.
		 */
		this.connect();

		return this.connection;

	}

	/**
	 * Returns the schema of the database as HashMap with keys the
	 * table names and values HashMaps with key column names and
	 * values data types. A special key <PRIMARY_KEY> can also be used
	 * to indicate the column (or columns, separated by <,>) that is
	 * going to be used as primary key.
	 *
	 * @return The schema of database.
	 */
	public abstract HashMap<String, HashMap<String, String>> getSchema();

	/**
	 * @return If a connection to the database is currently
	 *         established.
	 */
	public boolean isConnected() {

		try {

			return (this.connection != null) && !this.connection.isClosed();

		} catch (SQLException ex) {
			LoggerManager.getDefault().getLogger(this.getClass().getName()).warning(ex.toString());
		}

		return false;

	}

	/**
	 * @return If the database is corrupted and should be fixed before
	 *         it is used again.
	 */
	public boolean isCorrupted() {

		return this.is_corrupted;

	}

	/**
	 * Should be used when the caller determined that the database is
	 * definitely corrupted.
	 */
	protected void setAsCorrupted() {

		this.is_corrupted = true;
	}

}
