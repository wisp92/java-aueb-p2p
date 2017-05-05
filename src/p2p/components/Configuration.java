package p2p.components;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;

import p2p.utilities.LoggerManager;

/**
 * A Configuration object extends the functionality of a {@link Properties} and
 * can be used to set and retrieve a static default configuration.
 *
 * @author {@literal p3100161 <Joseph Sakos>}
 */
public class Configuration extends Properties {

	private static Configuration default_configuration = new Configuration();

	/**
	 * The serialVersionID required by the {@link Serializable} interface to
	 * ensure the integrity of the object during a serialization and
	 * deserialization process.
	 */
	private static final long serialVersionUID = 5939886933421026993L;

	/**
	 * @return The default configuration.
	 */
	public static Configuration getDefault() {

		return Configuration.default_configuration;
	}

	/**
	 * Update the default configuration.
	 *
	 * @param new_configuration
	 *            The new configuration.
	 * @return The old configuration.
	 */
	public static final Configuration setAsDefault(final Configuration new_configuration) {

		final Configuration last_configuration = Configuration.default_configuration;
		Configuration.default_configuration = new_configuration;
		return last_configuration;

	}

	private final String path;

	/**
	 * Allocates a new empty Configuration object.
	 */
	public Configuration() {
		this(null);
	}

	/**
	 * Allocates a new Configuration object
	 *
	 * @param path
	 *            The path to the properties file.
	 */
	public Configuration(final String path) {

		this.path = path;

		if (path != null) {

			try (final FileInputStream in = new FileInputStream(new File(path))) {

				this.load(in);
				
				LoggerManager.tracedLog(Level.INFO,
				        String.format("The configuration file <%s> loaded successfully.", path));

			} catch (final IOException ex) {

				LoggerManager.tracedLog(Level.WARNING,
				        String.format("The configuration file <%s> could not be loaded.", path));

			}

		}

	}

	/**
	 * Get an integer value from the properties file.
	 *
	 * @param key
	 *            The key of the property.
	 * @return The value of the property.
	 */
	public final int getInteger(final String key) {

		return Integer.parseInt(this.getProperty(key));
	}

	/**
	 * Get an integer value from the properties file.
	 *
	 * @param key
	 *            The key of the property.
	 * @param default_value
	 *            The default value that is returned in case the configuration
	 *            does not have the specified property.
	 * @return The value of the property.
	 */
	public final int getInteger(final String key, final int default_value) {

		try {

			return Integer.parseInt(this.getProperty(key));

		} catch (final NumberFormatException ex) {

			return default_value;

		}

	}

	/**
	 * @return The path of the properties file.
	 */
	public String getPath() {

		return this.path;
	}

	/**
	 * Same as {@link Properties#getProperty getPropert(String)} method.
	 *
	 * @param key
	 *            The key of the property.
	 * @return The value of the property.
	 */
	public final String getString(final String key) {

		return this.getProperty(key);
	}

	/**
	 * Same as {@link Properties#getProperty getPropert(String, String)} method.
	 *
	 * @param key
	 *            The key of the property.
	 * @param default_value
	 *            The default value that is returned in case the configuration
	 *            does not have the specified property.
	 * @return The value of the property.
	 */
	public final String getString(final String key, final String default_value) {

		return this.getProperty(key, default_value);
	}
	
	/**
	 * Get an boolean value from the properties file.
	 *
	 * @param key
	 *            The key of the property.
	 * @param default_value
	 *            The default value that is returned in case the configuration
	 *            does not have the specified property.
	 * @return The value of the property.
	 */
	public final boolean getBoolean(final String key, final boolean default_value) {
		
		if (Boolean.parseBoolean(this.getProperty(key))) return true;
		return default_value;
		
	}
	
	/**
	 * Get an boolean value from the properties file.
	 *
	 * @param key
	 *            The key of the property.
	 * @return The value of the property or false.
	 */
	public final boolean getBoolean(final String key) {
		
		return this.getBoolean(key, false);
	}

}
