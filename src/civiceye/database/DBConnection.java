
package civiceye.database;

import civiceye.util.AppConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.JarFile;

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
    // JDBC driver — located at start-up so no -cp flag is required
    // -----------------------------------------------------------------
    /** Fully-qualified name of the MySQL 8 driver shipped in {@code lib/}. */
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    /** Jar-name fragments that identify Connector/J without opening the archive. */
    private static final String[] DRIVER_JAR_HINTS = { "mysql-connector-j", "mysql-connector-java" };

    /** How many parent directories above the working directory are searched. */
    private static final int MAX_PARENT_LEVELS = 4;

    /**
     * Human-readable outcome of driver discovery. {@code null} means no driver
     * was found, so every connection attempt is guaranteed to fail.
     */
    private static String DRIVER_STATUS;

    /** Keeps the jar's class loader (and therefore its jar) reachable. */
    private static URLClassLoader driverLoader;

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
        DRIVER_STATUS = loadDriver();
    }

    // -----------------------------------------------------------------
    // Driver discovery
    // -----------------------------------------------------------------

    /**
     * Makes the MySQL driver available to {@link DriverManager}.
     *
     * <p>
     * The driver is first looked for on the ordinary classpath. When the app is
     * launched as {@code java CivicEye} (or {@code java CivicEye.java}) the jar
     * is not there, so {@code lib/*.jar} — plus the working directory and a few
     * of its parents — is scanned instead and the driver is registered through a
     * {@link DriverShim} that belongs to the application's own class loader.
     *
     * @return a description of where the driver came from, or {@code null} if it
     *         could not be found anywhere
     */
    private static String loadDriver() {
        try {
            Class.forName(DRIVER_CLASS);
            return "MySQL Connector/J on the classpath";
        } catch (ClassNotFoundException | LinkageError notOnClasspath) {
            // Expected for `java CivicEye` — fall through to the jar search.
        }

        for (File jar : findDriverJars()) {
            try {
                URLClassLoader loader = new URLClassLoader(
                        new URL[] { jar.toURI().toURL() },
                        DBConnection.class.getClassLoader());
                Class<?> driverClass = Class.forName(DRIVER_CLASS, true, loader);
                Driver delegate = (Driver) driverClass.getDeclaredConstructor().newInstance();
                DriverManager.registerDriver(new DriverShim(delegate));
                driverLoader = loader; // keep the loader alive for the JVM lifetime
                System.out.println("[DBConnection] JDBC driver loaded from " + jar.getAbsolutePath());
                return "MySQL Connector/J from " + jar.getAbsolutePath();
            } catch (Exception | LinkageError e) {
                System.err.println("[DBConnection] Could not load driver from " + jar + ": " + e);
            }
        }

        System.err.println(
                "[DBConnection] WARNING: MySQL Connector/J was not found.\n" +
                        "  Put mysql-connector-j-*.jar in " + new File("lib").getAbsolutePath() + "\n" +
                        "  or run: java -cp \".:lib/*\" CivicEye\n" +
                        "  (-Dciviceye.lib=/path/to/jar overrides the search)");
        return null;
    }

    /**
     * Collects jar files that may contain Connector/J, searching
     * {@code -Dciviceye.lib} / {@code CIVICEYE_LIB} first, then {@code lib/}
     * beside the working directory and a few of its parents.
     */
    private static List<File> findDriverJars() {
        LinkedHashSet<File> candidates = new LinkedHashSet<>();

        String explicit = System.getProperty("civiceye.lib");
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv("CIVICEYE_LIB");
        }
        if (explicit != null && !explicit.isBlank()) {
            candidates.add(new File(explicit.trim()));
        }

        File dir = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        for (int level = 0; dir != null && level <= MAX_PARENT_LEVELS; level++) {
            candidates.add(new File(dir, "lib"));
            candidates.add(dir);
            dir = dir.getParentFile();
        }

        List<File> jars = new ArrayList<>();
        for (File candidate : candidates) {
            if (candidate.isDirectory()) {
                File[] found = candidate.listFiles(
                        (d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".jar"));
                if (found != null) {
                    for (File jar : found) {
                        if (containsDriver(jar)) {
                            jars.add(jar);
                        }
                    }
                }
            } else if (containsDriver(candidate)) {
                jars.add(candidate);
            }
        }
        return jars;
    }

    /** Returns {@code true} if {@code jar} looks like Connector/J. */
    private static boolean containsDriver(File jar) {
        if (jar == null || !jar.isFile()) {
            return false;
        }
        String name = jar.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jar")) {
            return false;
        }
        for (String hint : DRIVER_JAR_HINTS) {
            if (name.contains(hint)) {
                return true;
            }
        }
        try (JarFile jf = new JarFile(jar)) {
            return jf.getEntry(DRIVER_CLASS.replace('.', '/') + ".class") != null;
        } catch (IOException e) {
            return false;
        }
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
                            "  URL   : " + DB_URL + "\n" +
                            "  User  : " + DB_USER + "\n" +
                            "  Driver: " + (DRIVER_STATUS == null ? "NOT FOUND" : DRIVER_STATUS) + "\n" +
                            "  Hint  : " + connectionHint(e) + "\n" +
                            "  Cause : " + e.getMessage(),
                    e.getSQLState(),
                    e.getErrorCode(),
                    e);
        }
    }

    /** Turns a raw JDBC failure into an actionable next step. */
    private static String connectionHint(SQLException e) {
        if (DRIVER_STATUS == null) {
            return "MySQL Connector/J is not on the classpath. Put the jar in ./lib " +
                    "or run: java -cp \".:lib/*\" CivicEye";
        }
        String cause = e.getMessage() == null ? "" : e.getMessage();
        if (cause.contains("Access denied")) {
            return "MySQL rejected the password for '" + DB_USER + "'. Update db.password in '" +
                    AppConstants.CFG_FILE + "'.";
        }
        if (cause.contains("Unknown database")) {
            return "Run sql/create_database.sql and sql/create_tables.sql to create civiceye_db.";
        }
        if (cause.contains("Communications link failure") || cause.contains("Connection refused")) {
            return "MySQL is not reachable at " + DB_URL + " — check that the server is running.";
        }
        return "Verify that MySQL is running and '" + AppConstants.CFG_FILE +
                "' has the correct password.";
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

/**
 * Wrapper that lets a JDBC driver loaded from an external jar — and therefore
 * from a different class loader — be registered with {@link DriverManager}.
 *
 * <p>
 * {@code DriverManager} ignores drivers that the calling class loader cannot
 * see, so the real driver is registered behind this shim, which lives in the
 * application's own loader.
 */
final class DriverShim implements Driver {

    private final Driver delegate;

    DriverShim(Driver delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
