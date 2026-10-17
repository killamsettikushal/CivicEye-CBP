
package civiceye.database;

import civiceye.util.AppConstants;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Centralised JDBC connection factory for CivicEye.
 *
 * <p>
 * Credentials are loaded from {@code config.properties} at startup using
 * this resolution order:
 * <ol>
 * <li>Java classpath (e.g. when {@code config.properties} is in the
 * {@code out/} directory).</li>
 * <li>Current working directory (typical when launching from the project
 * root).</li>
 * <li>Built-in defaults — URL from {@link AppConstants#DEFAULT_DB_URL}, empty
 * password —
 * accompanied by a console warning.</li>
 * </ol>
 *
 * <p>
 * Every caller is responsible for closing the returned {@link Connection}
 * (use try-with-resources). This class intentionally does not pool connections;
 * that complexity is unnecessary for a college project demonstration.
 *
 * <p>
 * Usage example:
 * 
 * <pre>{@code
 * try (Connection conn = DBConnection.getConnection();
 *         PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
 *     // use ps ...
 * }
 * }</pre>
 */
public final class DBConnection {

    // Prevent instantiation
    private DBConnection() {
    }

    // -----------------------------------------------------------------
    // Credentials — loaded once at class-initialisation time
    // -----------------------------------------------------------------
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        Properties props = loadProperties();
        DB_URL = props.getProperty(AppConstants.CFG_DB_URL, AppConstants.DEFAULT_DB_URL);
        DB_USER = props.getProperty(AppConstants.CFG_DB_USER, "root");
        DB_PASSWORD = props.getProperty(AppConstants.CFG_DB_PASSWORD, "");
    }

    /**
     * Attempts to read {@code config.properties} from the classpath, then
     * from the current working directory. Returns an empty {@link Properties}
     * (triggering the built-in defaults) if neither location succeeds.
     */
    private static Properties loadProperties() {
        Properties props = new Properties();

        // 1 — Try classpath (works when the file is copied alongside .class files)
        try (InputStream in = DBConnection.class.getClassLoader()
                .getResourceAsStream(AppConstants.CFG_FILE)) {
            if (in != null) {
                props.load(in);
                return props;
            }
        } catch (IOException ignored) {
            // fall through to next attempt
        }

        // 2 — Try the current working directory (typical launch from project root)
        try (InputStream in = new FileInputStream(AppConstants.CFG_FILE)) {
            props.load(in);
            return props;
        } catch (IOException ignored) {
            // fall through to defaults
        }

        // 3 — Warn and return empty props so default constants are used
        System.err.println(
                "[DBConnection] WARNING: '" + AppConstants.CFG_FILE + "' not found.\n" +
                        "  Copy config.properties.template to config.properties and fill in your credentials.\n" +
                        "  Attempting connection with built-in defaults (likely to fail).");
        return props;
    }

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Opens and returns a new {@link Connection} to {@code civiceye_db}.
     *
     * <p>
     * Throws {@link SQLException} with a human-readable message when
     * the database is unreachable or credentials are wrong.
     *
     * @return an open, ready-to-use JDBC connection
     * @throws SQLException if the driver cannot establish the connection
     */
    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        } catch (SQLException e) {
            // Wrap with a friendlier message that points to the config file
            throw new SQLException(
                    "Could not connect to the database.\n" +
                            "  URL  : " + DB_URL + "\n" +
                            "  User : " + DB_USER + "\n" +
                            "  Hint : Verify that MySQL is running and '" +
                            AppConstants.CFG_FILE + "' has the correct password.\n" +
                            "  Cause: " + e.getMessage(),
                    e.getSQLState(),
                    e.getErrorCode(),
                    e);
        }
    }

    /**
     * Quick connectivity smoke-test.
     *
     * @return {@code true} if a connection can be opened and is not closed;
     *         {@code false} otherwise (error is printed to stderr).
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            boolean ok = conn != null && !conn.isClosed();
            if (ok) {
                System.out.println("[DBConnection] Connection test PASSED — database is reachable.");
            }
            return ok;
        } catch (SQLException e) {
            System.err.println("[DBConnection] Connection test FAILED:\n  " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the JDBC URL being used (useful for diagnostics / logs).
     * Does NOT expose credentials.
     */
    public static String getDbUrl() {
        return DB_URL;
    }
}
