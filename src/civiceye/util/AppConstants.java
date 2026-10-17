package civiceye.util;

/**
 * Application-wide constants for CivicEye.
 *
 * <p>All magic strings, category lists, status values, and configuration
 * keys are centralised here so that a single change propagates everywhere.
 * No instances of this class are intended; all members are {@code static final}.
 */
public final class AppConstants {

    // Prevent instantiation
    private AppConstants() {}

    // -----------------------------------------------------------------
    // Application identity
    // -----------------------------------------------------------------

    public static final String APP_NAME    = "CivicEye";
    public static final String APP_VERSION = "1.0.0";

    // -----------------------------------------------------------------
    // config.properties keys  (file read by DBConnection)
    // -----------------------------------------------------------------

    public static final String CFG_FILE         = "config.properties";
    public static final String CFG_DB_URL        = "db.url";
    public static final String CFG_DB_USER       = "db.user";
    public static final String CFG_DB_PASSWORD   = "db.password";

    // -----------------------------------------------------------------
    // Default DB connection URL template
    // (used if config.properties is absent — not recommended for production)
    // -----------------------------------------------------------------

    public static final String DEFAULT_DB_URL =
            "jdbc:mysql://localhost:3306/civiceye_db"
          + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // -----------------------------------------------------------------
    // User roles  (must match the ENUM in create_tables.sql)
    // -----------------------------------------------------------------

    public static final String ROLE_CITIZEN = "Citizen";
    public static final String ROLE_ADMIN   = "Admin";

    // -----------------------------------------------------------------
    // Issue statuses  (must match ENUM in create_tables.sql)
    // -----------------------------------------------------------------

    public static final String STATUS_PENDING     = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED    = "Resolved";

    /** All valid status values in natural workflow order. */
    public static final String[] ALL_STATUSES = {
        STATUS_PENDING,
        STATUS_IN_PROGRESS,
        STATUS_RESOLVED
    };

    // -----------------------------------------------------------------
    // Issue categories  (drives the JComboBox in IssueReportPanel)
    // -----------------------------------------------------------------

    public static final String[] ISSUE_CATEGORIES = {
        "Roads",
        "Sanitation",
        "Street Lighting",
        "Water Supply",
        "Drainage",
        "Public Parks",
        "Electricity",
        "Traffic",
        "Public Safety",
        "Other"
    };

    // -----------------------------------------------------------------
    // Reward points
    // -----------------------------------------------------------------

    /** Points awarded to a citizen for each successfully submitted issue. */
    public static final int REWARD_POINTS_PER_ISSUE = 5;

    // -----------------------------------------------------------------
    // Validation limits
    // -----------------------------------------------------------------

    public static final int MAX_LOCATION_LENGTH    = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MIN_DESCRIPTION_LENGTH = 10;

    // -----------------------------------------------------------------
    // UI dimensions
    // -----------------------------------------------------------------

    public static final int LOGIN_WIDTH             = 460;
    public static final int LOGIN_HEIGHT            = 340;
    public static final int CITIZEN_DASH_WIDTH      = 1000;
    public static final int CITIZEN_DASH_HEIGHT     = 680;
    public static final int ADMIN_DASH_WIDTH        = 1100;
    public static final int ADMIN_DASH_HEIGHT       = 720;

    // -----------------------------------------------------------------
    // Password hashing
    // -----------------------------------------------------------------

    public static final String HASH_ALGORITHM = "SHA-256";
    /** Number of random bytes used for each password salt (hex → 32 chars). */
    public static final int    SALT_BYTE_LENGTH = 16;
}
