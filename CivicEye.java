/*
 * ============================================================================
 *  CivicEye — Civic Issue Reporting & Management System
 *  SINGLE-FILE EDITION
 * ----------------------------------------------------------------------------
 *  The whole application (database layer, DAOs, services, Swing UI) lives in
 *  this one source file.  Every class below is the same code, with the same
 *  names and behaviour, as its original package version under src/civiceye/**.
 *
 *  Requirements
 *      JDK 17 or newer, MySQL 8 running, the schema from sql/create_tables.sql
 *      and the MySQL Connector/J jar in ./lib
 *
 *  Run the GUI
 *      javac -cp "lib/*" CivicEye.java
 *      java  -cp ".:lib/*" CivicEye
 *
 *      or straight from source, with no build step:
 *      java  -cp ".:lib/*" CivicEye.java
 *
 *  Run every layer head-lessly (no window, writes and removes a test account)
 *      java -cp ".:lib/*" CivicEye.java --selftest
 *
 *  Database credentials are read by DBConnection from config.properties
 *  (classpath, then working directory, then built-in defaults).
 *
 *  Contents
 *      1. Constants and password hashing   AppConstants, PasswordUtils
 *      2. Domain model                     User, Citizen, Admin, Issue
 *      3. Persistence                      UserRecord, UserDAO, IssueDAO, DBConnection
 *      4. Business rules                   ValidationService, AuthService, IssueService
 *      5. Swing user interface             LoginFrame, RegistrationDialog,
 *                                          IssueReportPanel, IssueTablePanel,
 *                                          CitizenDashboard, AdminDashboard
 *      6. Entry point                      CivicEye (main) + SelfTest
 * ============================================================================
 */

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;

// ============================================================================
//  util / AppConstants
// ============================================================================

/**
 * Application-wide constants for CivicEye.
 *
 * <p>
 * All magic strings, category lists, status values, and configuration
 * keys are centralised here so that a single change propagates everywhere.
 * No instances of this class are intended; all members are
 * {@code static final}.
 */
final class AppConstants {

    // Prevent instantiation
    private AppConstants() {
    }

    // -----------------------------------------------------------------
    // Application identity
    // -----------------------------------------------------------------

    public static final String APP_NAME = "CivicEye";
    public static final String APP_VERSION = "1.0.0";

    // -----------------------------------------------------------------
    // config.properties keys (file read by DBConnection)
    // -----------------------------------------------------------------

    public static final String CFG_FILE = "config.properties";
    public static final String CFG_DB_URL = "db.url";
    public static final String CFG_DB_USER = "db.user";
    public static final String CFG_DB_PASSWORD = "db.password";

    // -----------------------------------------------------------------
    // Default DB connection URL template
    // (used if config.properties is absent — not recommended for production)
    // -----------------------------------------------------------------

    public static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/civiceye_db"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // -----------------------------------------------------------------
    // User roles (must match the ENUM in create_tables.sql)
    // -----------------------------------------------------------------

    public static final String ROLE_CITIZEN = "Citizen";
    public static final String ROLE_ADMIN = "Admin";

    // -----------------------------------------------------------------
    // Issue statuses (must match ENUM in create_tables.sql)
    // -----------------------------------------------------------------

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_RESOLVED = "Resolved";

    /** All valid status values in natural workflow order. */
    public static final String[] ALL_STATUSES = {
            STATUS_PENDING,
            STATUS_IN_PROGRESS,
            STATUS_RESOLVED
    };

    // -----------------------------------------------------------------
    // Issue categories (drives the JComboBox in IssueReportPanel)
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
    // Validation limits
    // -----------------------------------------------------------------

    public static final int MAX_LOCATION_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MIN_DESCRIPTION_LENGTH = 10;

    // -----------------------------------------------------------------
    // UI dimensions
    // -----------------------------------------------------------------

    public static final int LOGIN_WIDTH = 460;
    public static final int LOGIN_HEIGHT = 340;
    public static final int CITIZEN_DASH_WIDTH = 1000;
    public static final int CITIZEN_DASH_HEIGHT = 680;
    public static final int ADMIN_DASH_WIDTH = 1100;
    public static final int ADMIN_DASH_HEIGHT = 720;

    // -----------------------------------------------------------------
    // Password hashing
    // -----------------------------------------------------------------

    public static final String HASH_ALGORITHM = "SHA-256";
    /** Number of random bytes used for each password salt (hex → 32 chars). */
    public static final int SALT_BYTE_LENGTH = 16;
}

// ============================================================================
// util / PasswordUtils
// ============================================================================

/**
 * Password hashing and verification utilities for CivicEye.
 *
 * <h3>Algorithm</h3>
 * 
 * <pre>
 *   storedHash = SHA-256( password + salt )   [UTF-8, lowercase hex output]
 *   salt       = 16 cryptographically random bytes encoded as 32 lowercase hex chars
 * </pre>
 *
 * <p>
 * The salt is generated once per user and stored in the {@code users.salt}
 * column
 * alongside the hash. Because SHA-256 is used with a per-user salt (not
 * HMAC-based
 * or adaptive), this is appropriate for a college demonstration project.
 * Production systems should prefer BCrypt or Argon2.
 *
 * <p>
 * This class has no external dependencies beyond {@code java.security}.
 */
final class PasswordUtils {

    // Prevent instantiation
    private PasswordUtils() {
    }

    // Single SecureRandom instance — thread-safe after construction
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Generates a new random salt.
     *
     * @return a lowercase hex string of length {@code SALT_BYTE_LENGTH * 2}
     *         (default 32 characters for 16 bytes)
     */
    public static String generateSalt() {
        byte[] saltBytes = new byte[AppConstants.SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }

    /**
     * Computes SHA-256( password + salt ) and returns the result as
     * a 64-character lowercase hex string.
     *
     * @param password plaintext password (not null)
     * @param salt     hex salt string returned by {@link #generateSalt()} (not
     *                 null)
     * @return 64-char lowercase hex hash
     * @throws RuntimeException if SHA-256 is unavailable (should never happen in
     *                          Java SE)
     */
    public static String hashPassword(String password, String salt) {
        if (password == null)
            throw new IllegalArgumentException("password must not be null");
        if (salt == null)
            throw new IllegalArgumentException("salt must not be null");

        try {
            MessageDigest digest = MessageDigest.getInstance(AppConstants.HASH_ALGORITHM);
            String combined = password + salt;
            byte[] hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE specification — this cannot happen
            throw new RuntimeException("SHA-256 is unavailable on this JVM", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored hash and its salt.
     *
     * <p>
     * The comparison is case-insensitive to tolerate any case differences
     * between hex strings produced by different code paths.
     *
     * @param plaintext  the password entered by the user
     * @param storedHash the hash value retrieved from the database
     * @param salt       the salt value retrieved from the database
     * @return {@code true} if the password matches; {@code false} otherwise
     */
    public static boolean verifyPassword(String plaintext, String storedHash, String salt) {
        if (plaintext == null || plaintext.isEmpty())
            return false;
        if (storedHash == null || storedHash.isEmpty())
            return false;
        if (salt == null || salt.isEmpty())
            return false;

        String computed = hashPassword(plaintext, salt);
        return computed.equalsIgnoreCase(storedHash);
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes source bytes
     * @return lowercase hex representation (length = bytes.length * 2)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

// ============================================================================
// model / User
// ============================================================================

abstract class User {

    private int userId;
    private String name;
    private String email;

    public User(int userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
    }

    public int getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public abstract String getRole();
}

// ============================================================================
// model / Citizen
// ============================================================================

/**
 * Represents a registered Citizen who can submit and track civic issues.
 *
 * <p>
 * Inherits common identity fields (userId, name, email) from {@link User}.
 */
class Citizen extends User {

    // ------------------------------------------------------------------
    // Constructor — used both at registration and when loaded from the DB
    // ------------------------------------------------------------------

    /**
     * Creates a Citizen.
     *
     * @param userId database-assigned user ID (0 before first insert)
     * @param name   full name
     * @param email  unique email address
     */
    public Citizen(int userId, String name, String email) {
        super(userId, name, email);
    }

    // ------------------------------------------------------------------
    // User contract
    // ------------------------------------------------------------------

    @Override
    public String getRole() {
        return "Citizen";
    }

    @Override
    public String toString() {
        return String.format("Citizen{id=%d, name='%s'}",
                getUserId(), getName());
    }
}

// ============================================================================
// model / Admin
// ============================================================================

class Admin extends User {

    public Admin(int userId, String name, String email) {
        super(userId, name, email);
    }

    @Override
    public String getRole() {
        return "Admin";
    }

    public void displayAdminMessage() {
        System.out.println("Admin can manage civic issues.");
    }
}

// ============================================================================
// model / Issue
// ============================================================================

/**
 * Represents a civic issue submitted by a Citizen.
 *
 * <p>
 * Two constructors are provided:
 * <ul>
 * <li>{@link #Issue(String, int, String, String, String)} — for creating a
 * new issue before it is persisted (issueId is 0, createdAt is now).</li>
 * <li>{@link #Issue(int, int, String, String, String, String, LocalDateTime)} —
 * for
 * reconstructing an issue that was loaded from the database.</li>
 * </ul>
 */
class Issue {

    // -----------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------

    private int issueId; // 0 before DB insert; assigned by DB
    private int reporterId; // FK → users.user_id
    private String category;
    private String location;
    private String description;
    private String status;
    private LocalDateTime createdAt;

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // -----------------------------------------------------------------
    // Constructor for new (not yet persisted) issues
    // -----------------------------------------------------------------

    /**
     * Creates a new issue that has not yet been saved to the database.
     * Status is automatically set to {@code "Pending"} and createdAt to now.
     *
     * @param category    issue category (e.g. "Roads", "Sanitation")
     * @param reporterId  user_id of the submitting Citizen
     * @param location    textual description of the location
     * @param description detailed report text
     * @param status      initial status (typically "Pending")
     */
    public Issue(String category, int reporterId,
            String location, String description, String status) {
        this.issueId = 0;
        this.reporterId = reporterId;
        this.category = category;
        this.location = location;
        this.description = description;
        this.status = (status != null && !status.isBlank()) ? status : "Pending";
        this.createdAt = LocalDateTime.now();
    }

    // -----------------------------------------------------------------
    // Constructor for issues loaded from the database
    // -----------------------------------------------------------------

    /**
     * Reconstructs an issue retrieved from the database.
     *
     * @param issueId     database-assigned primary key
     * @param reporterId  FK to users table
     * @param category    issue category
     * @param location    issue location
     * @param description issue description
     * @param status      current status string
     * @param createdAt   timestamp stored in DB
     */
    public Issue(int issueId, int reporterId,
            String category, String location,
            String description, String status,
            LocalDateTime createdAt) {
        this.issueId = issueId;
        this.reporterId = reporterId;
        this.category = category;
        this.location = location;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
    }

    // -----------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------

    public int getIssueId() {
        return issueId;
    }

    public int getReporterId() {
        return reporterId;
    }

    public String getCategory() {
        return category;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // -----------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------

    /** Called by IssueDAO after a successful INSERT to store the generated ID. */
    public void setIssueId(int issueId) {
        this.issueId = issueId;
    }

    /** Updated by AdminDashboard via IssueService after status change. */
    public void setStatus(String status) {
        this.status = status;
    }

    // -----------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------

    /** Returns createdAt formatted as {@code dd-MM-yyyy HH:mm} for display. */
    public String getFormattedCreatedAt() {
        return (createdAt != null) ? createdAt.format(DISPLAY_FORMAT) : "-";
    }

    @Override
    public String toString() {
        return String.format("Issue{id=%d, reporter=%d, category='%s', status='%s'}",
                issueId, reporterId, category, status);
    }
}

// ============================================================================
// dao / UserRecord
// ============================================================================

/**
 * Internal data-transfer object representing one row from the {@code users}
 * table.
 *
 * <p>
 * This class is intentionally package-private in scope for its constructor —
 * only {@link UserDAO} creates instances. {@link AuthService}
 * uses it to verify credentials, then discards it in favour of a proper
 * {@link User} subclass (Citizen or Admin) that carries no
 * password information.
 *
 * <p>
 * Fields are {@code public final} for read-only access without getters,
 * keeping the code concise for a data-carrier class.
 */
final class UserRecord {

    /** Primary key from {@code users.user_id}. */
    public final int userId;

    /** Full name from {@code users.name}. */
    public final String name;

    /** Email (unique) from {@code users.email}. */
    public final String email;

    /**
     * SHA-256 hex hash stored in {@code users.password_hash}.
     * Never expose this outside the authentication flow.
     */
    public final String passwordHash;

    /**
     * Per-user hex salt stored in {@code users.salt}.
     * Required to verify the password.
     */
    public final String salt;

    /** Role string: {@code "Citizen"} or {@code "Admin"}. */
    public final String role;

    /**
     * Package-private constructor — only {@link UserDAO} should create instances.
     */
    UserRecord(int userId, String name, String email,
            String passwordHash, String salt,
            String role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
    }
}

// ============================================================================
// dao / UserDAO
// ============================================================================

/**
 * Data-access object for the {@code users} table.
 *
 * <p>
 * All public methods open their own connection via
 * {@link DBConnection#getConnection()}
 * and close it immediately using try-with-resources. No connection is held
 * between calls.
 *
 * <p>
 * SQL is always issued through {@link PreparedStatement} — never by
 * string concatenation — to prevent SQL injection.
 *
 * <p>
 * On database errors the method logs to stderr and returns a safe sentinel
 * value ({@code null} or {@code -1} or {@code false}) so callers can detect
 * failure without catching checked exceptions.
 */
class UserDAO {

    // -----------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------

    private static final String SQL_FIND_BY_EMAIL = "SELECT user_id, name, email, password_hash, salt, role " +
            "FROM users WHERE email = ? LIMIT 1";

    private static final String SQL_INSERT_USER = "INSERT INTO users (name, email, password_hash, salt, role) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_EMAIL_EXISTS = "SELECT COUNT(*) FROM users WHERE email = ?";

    // -----------------------------------------------------------------
    // Public methods
    // -----------------------------------------------------------------

    /**
     * Looks up a user by their email address.
     *
     * @param email the email to search for (case-insensitive in most MySQL
     *              collations)
     * @return a {@link UserRecord} containing all columns including credentials,
     *         or {@code null} if no matching row exists or a DB error occurs
     */
    public UserRecord findByEmail(String email) {
        if (email == null || email.isBlank())
            return null;

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_EMAIL)) {

            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new UserRecord(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("salt"),
                            rs.getString("role"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] findByEmail failed for '" + email + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Inserts a new user into the database.
     *
     * <p>
     * Passwords must be hashed by the caller (via {@link PasswordUtils})
     * before invoking this method. Never pass a plaintext password here.
     *
     * @param name         full name
     * @param email        unique email (must not already exist)
     * @param passwordHash SHA-256 hex hash
     * @param salt         hex salt used to produce the hash
     * @param role         {@code "Citizen"} or {@code "Admin"}
     * @return the generated {@code user_id} on success, or {@code -1} on failure
     */
    public int createUser(String name, String email,
            String passwordHash, String salt, String role) {

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, passwordHash);
            ps.setString(4, salt);
            ps.setString(5, role);

            int rows = ps.executeUpdate();
            if (rows == 1) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // Duplicate email — expected when registering with an existing address
            System.err.println("[UserDAO] createUser: email already exists — " + email);
        } catch (SQLException e) {
            System.err.println("[UserDAO] createUser failed: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Returns {@code true} if the given email is already registered.
     *
     * @param email email to check
     * @return {@code true} if a row with this email exists; {@code false} otherwise
     */
    public boolean emailExists(String email) {
        if (email == null || email.isBlank())
            return false;

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_EMAIL_EXISTS)) {

            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] emailExists failed: " + e.getMessage());
        }
        return false;
    }

}

// ============================================================================
// dao / IssueDAO
// ============================================================================

/**
 * Data-access object for the {@code issues} table.
 *
 * <p>
 * Every method manages its own JDBC connection via try-with-resources.
 * SQL injection is prevented throughout by the exclusive use of
 * {@link PreparedStatement}.
 *
 * <p>
 * On database error the method logs to stderr and returns a safe sentinel
 * ({@code false}, {@code -1}, {@code null}, or an empty list).
 */
class IssueDAO {

    // -----------------------------------------------------------------
    // SQL statements
    // -----------------------------------------------------------------

    private static final String SQL_INSERT_ISSUE = "INSERT INTO issues (reporter_id, category, location, description, status) "
            +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_GET_BY_REPORTER = "SELECT issue_id, reporter_id, category, location, description, status, created_at "
            +
            "FROM issues WHERE reporter_id = ? ORDER BY created_at DESC";

    private static final String SQL_GET_ALL = "SELECT issue_id, reporter_id, category, location, description, status, created_at "
            +
            "FROM issues ORDER BY created_at DESC";

    private static final String SQL_UPDATE_STATUS = "UPDATE issues SET status = ? WHERE issue_id = ?";

    private static final String SQL_GET_BY_ID = "SELECT issue_id, reporter_id, category, location, description, status, created_at "
            +
            "FROM issues WHERE issue_id = ? LIMIT 1";

    private static final String SQL_DELETE_ISSUE = "DELETE FROM issues WHERE issue_id = ?";

    // -----------------------------------------------------------------
    // Public methods
    // -----------------------------------------------------------------

    /**
     * Persists a new issue and assigns the database-generated ID back to the
     * {@code issue} object via {@link Issue#setIssueId(int)}.
     *
     * @param issue a fully populated Issue (issueId may be 0 — it will be replaced)
     * @return {@code true} if the INSERT succeeded; {@code false} on any error
     */
    public boolean createIssue(Issue issue) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        SQL_INSERT_ISSUE, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, issue.getReporterId());
            ps.setString(2, issue.getCategory());
            ps.setString(3, issue.getLocation());
            ps.setString(4, issue.getDescription());
            ps.setString(5, issue.getStatus());

            int rows = ps.executeUpdate();
            if (rows == 1) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        issue.setIssueId(keys.getInt(1)); // write generated PK back to model
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("[IssueDAO] createIssue failed: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns all issues reported by a specific citizen, newest first.
     *
     * @param reporterId the citizen's {@code user_id}
     * @return unmodifiable list of issues; empty list on error or no results
     */
    public List<Issue> getIssuesByReporter(int reporterId) {
        List<Issue> issues = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_GET_BY_REPORTER)) {

            ps.setInt(1, reporterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    issues.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[IssueDAO] getIssuesByReporter failed for reporterId="
                    + reporterId + ": " + e.getMessage());
        }
        return Collections.unmodifiableList(issues);
    }

    /**
     * Returns every issue in the database, newest first.
     * Used by the Admin dashboard.
     *
     * @return unmodifiable list of all issues; empty list on error
     */
    public List<Issue> getAllIssues() {
        List<Issue> issues = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_GET_ALL);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                issues.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[IssueDAO] getAllIssues failed: " + e.getMessage());
        }
        return Collections.unmodifiableList(issues);
    }

    /**
     * Updates the status of a single issue.
     *
     * @param issueId   the issue to update
     * @param newStatus one of {@code "Pending"}, {@code "In Progress"},
     *                  {@code "Resolved"}
     * @return {@code true} if exactly one row was updated; {@code false} otherwise
     */
    public boolean updateIssueStatus(int issueId, String newStatus) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus);
            ps.setInt(2, issueId);
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            System.err.println("[IssueDAO] updateIssueStatus failed for issueId=" + issueId
                    + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Retrieves a single issue by its primary key.
     * Used for ownership verification before sensitive operations.
     *
     * @param issueId the issue primary key
     * @return the matching {@link Issue}, or {@code null} if not found / error
     */
    public Issue getIssueById(int issueId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_GET_BY_ID)) {

            ps.setInt(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("[IssueDAO] getIssueById failed for issueId=" + issueId
                    + ": " + e.getMessage());
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Test-support helpers (not used by production code)
    // -----------------------------------------------------------------

    /**
     * Hard-deletes a single issue by primary key.
     *
     * <p>
     * <b>Intended for automated test cleanup only.</b> Production
     * workflows should never delete issues; status transitions are the
     * authorised lifecycle mechanism.
     *
     * @param issueId the {@code issue_id} to delete
     * @return {@code true} if exactly one row was removed
     */
    public boolean deleteIssue(int issueId) {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(SQL_DELETE_ISSUE)) {
            ps.setInt(1, issueId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            System.err.println("[IssueDAO] deleteIssue failed for issueId=" + issueId
                    + ": " + e.getMessage());
        }
        return false;
    }

    // -----------------------------------------------------------------
    // Internal helper — maps one ResultSet row to an Issue object
    // -----------------------------------------------------------------

    /**
     * Converts the current {@link ResultSet} row into an {@link Issue}.
     * The ResultSet cursor must already be positioned on a valid row.
     */
    private Issue mapRow(ResultSet rs) throws SQLException {
        int issueId = rs.getInt("issue_id");
        int reporterId = rs.getInt("reporter_id");
        String category = rs.getString("category");
        String location = rs.getString("location");
        String description = rs.getString("description");
        String status = rs.getString("status");
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime createdAt = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Issue(issueId, reporterId, category, location, description, status, createdAt);
    }
}

// ============================================================================
// database / DBConnection
// ============================================================================

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
final class DBConnection {

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

// ============================================================================
// service / ValidationService
// ============================================================================

/**
 * Stateless validation helper used by every other service and by UI components.
 *
 * <p>
 * All methods are pure functions — they depend only on their arguments and
 * produce no side effects. No database access occurs here.
 */
class ValidationService {

    // Pre-compiled pattern for RFC-5321-like email validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    // -----------------------------------------------------------------
    // Email
    // -----------------------------------------------------------------

    /**
     * Returns {@code true} if {@code email} is non-null, non-blank, and
     * matches a broadly standard email format.
     *
     * @param email the email string to validate
     * @return {@code true} if valid
     */
    public boolean validateEmail(String email) {
        if (email == null || email.isBlank())
            return false;
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    // -----------------------------------------------------------------
    // Issue submission form
    // -----------------------------------------------------------------

    /**
     * Validates the three editable fields of the issue-submission form.
     *
     * @param category    selected category string
     * @param location    location text entered by the user
     * @param description description text entered by the user
     * @return {@code null} if all fields are valid, or a human-readable
     *         error message describing the first problem found
     */
    public String validateIssueForm(String category, String location, String description) {

        // --- Category ---
        if (category == null || category.isBlank()) {
            return "Please select a category.";
        }
        boolean validCategory = false;
        for (String c : AppConstants.ISSUE_CATEGORIES) {
            if (c.equalsIgnoreCase(category.trim())) {
                validCategory = true;
                break;
            }
        }
        if (!validCategory) {
            return "Invalid category: " + category;
        }

        // --- Location ---
        if (location == null || location.isBlank()) {
            return "Location cannot be empty.";
        }
        if (location.trim().length() > AppConstants.MAX_LOCATION_LENGTH) {
            return "Location must be at most " + AppConstants.MAX_LOCATION_LENGTH + " characters.";
        }

        // --- Description ---
        if (description == null || description.isBlank()) {
            return "Description cannot be empty.";
        }
        String trimDesc = description.trim();
        if (trimDesc.length() < AppConstants.MIN_DESCRIPTION_LENGTH) {
            return "Description must be at least " + AppConstants.MIN_DESCRIPTION_LENGTH
                    + " characters.";
        }
        if (trimDesc.length() > AppConstants.MAX_DESCRIPTION_LENGTH) {
            return "Description must be at most " + AppConstants.MAX_DESCRIPTION_LENGTH
                    + " characters.";
        }

        return null; // all valid
    }

    // -----------------------------------------------------------------
    // Login form
    // -----------------------------------------------------------------

    /**
     * Returns a human-readable error if the login form fields are incomplete,
     * or {@code null} if both fields are present.
     *
     * @param email    email text from the field
     * @param password password text (may be empty string if user left it blank)
     * @return error message or {@code null}
     */
    public String validateLoginForm(String email, String password) {
        if (email == null || email.isBlank()) {
            return "Email cannot be empty.";
        }
        if (!validateEmail(email)) {
            return "Please enter a valid email address.";
        }
        if (password == null || password.isEmpty()) {
            return "Password cannot be empty.";
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Registration form
    // -----------------------------------------------------------------

    /**
     * Validates the registration form.
     *
     * @param name            full name
     * @param email           email address
     * @param password        chosen password (plaintext)
     * @param confirmPassword repeated password
     * @return error message, or {@code null} if all fields are valid
     */
    public String validateRegistrationForm(String name, String email,
            String password, String confirmPassword) {
        if (name == null || name.isBlank()) {
            return "Name cannot be empty.";
        }
        if (name.trim().length() < 2) {
            return "Name must be at least 2 characters.";
        }
        String emailError = validateLoginForm(email, password);
        if (emailError != null)
            return emailError;

        if (password.length() < 6) {
            return "Password must be at least 6 characters.";
        }
        if (!password.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        return null;
    }

    // -----------------------------------------------------------------
    // Issue status
    // -----------------------------------------------------------------

    /**
     * Returns {@code true} if {@code status} is one of the recognised
     * status strings defined in {@link AppConstants}.
     *
     * @param status the status string to check
     * @return {@code true} if valid
     */
    public boolean validateStatus(String status) {
        if (status == null)
            return false;
        for (String s : AppConstants.ALL_STATUSES) {
            if (s.equalsIgnoreCase(status.trim()))
                return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if transitioning from {@code currentStatus} to
     * {@code newStatus} is a permitted workflow step.
     *
     * <p>
     * Allowed transitions:
     * <ul>
     * <li>Pending → In Progress</li>
     * <li>Pending → Resolved</li>
     * <li>In Progress → Resolved</li>
     * </ul>
     * Moving backwards (e.g. Resolved → Pending) is not permitted.
     *
     * @param currentStatus the issue's current status
     * @param newStatus     the desired new status
     * @return {@code true} if the transition is valid
     */
    public boolean validateStatusTransition(String currentStatus, String newStatus) {
        if (!validateStatus(currentStatus) || !validateStatus(newStatus))
            return false;
        if (currentStatus.equalsIgnoreCase(newStatus))
            return false; // no-op

        switch (currentStatus) {
            case AppConstants.STATUS_PENDING:
                // Pending can go to either In Progress or Resolved
                return newStatus.equals(AppConstants.STATUS_IN_PROGRESS)
                        || newStatus.equals(AppConstants.STATUS_RESOLVED);

            case AppConstants.STATUS_IN_PROGRESS:
                // In Progress can only move forward to Resolved
                return newStatus.equals(AppConstants.STATUS_RESOLVED);

            case AppConstants.STATUS_RESOLVED:
                // Resolved is a terminal state; no further transitions allowed
                return false;

            default:
                return false;
        }
    }
}

// ============================================================================
// service / AuthService
// ============================================================================

/**
 * Authentication service for CivicEye.
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Validate login-form inputs before touching the database.</li>
 * <li>Retrieve the matching {@link UserRecord} from {@link UserDAO}.</li>
 * <li>Verify the plaintext password against the stored hash using
 * {@link PasswordUtils#verifyPassword}.</li>
 * <li>Construct and return the correct {@link User} subclass
 * ({@link Citizen} or {@link Admin}) — credentials are never
 * passed beyond this class.</li>
 * <li>Register new citizens.</li>
 * </ul>
 *
 * <p>
 * This class never executes SQL directly; all database work is delegated to
 * {@link UserDAO}.
 */
class AuthService {

    private final UserDAO userDAO;
    private final ValidationService validation;

    /** Creates an AuthService with default collaborators. */
    public AuthService() {
        this.userDAO = new UserDAO();
        this.validation = new ValidationService();
    }

    // -----------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------

    /**
     * Authenticates a user with the supplied credentials.
     *
     * <p>
     * Returns {@code null} on any failure (wrong email, wrong password,
     * DB unreachable, or invalid form inputs) so that the caller cannot
     * distinguish the reason — preventing user-enumeration attacks.
     *
     * @param email    the email entered in the login form
     * @param password the plaintext password entered in the login form
     * @return an authenticated {@link Citizen} or {@link Admin} object,
     *         or {@code null} if authentication fails
     */
    public User login(String email, String password) {

        // 1. Basic form validation (avoids unnecessary DB round-trip)
        String formError = validation.validateLoginForm(email, password);
        if (formError != null) {
            System.out.println("[AuthService] Login rejected (form): " + formError);
            return null;
        }

        // 2. Retrieve credentials from database
        UserRecord record = userDAO.findByEmail(email.trim().toLowerCase());
        if (record == null) {
            // No user with this email — return null (do NOT reveal this to UI)
            return null;
        }

        // 3. Verify password hash
        boolean passwordOk = PasswordUtils.verifyPassword(
                password, record.passwordHash, record.salt);
        if (!passwordOk) {
            return null;
        }

        // 4. Construct and return the appropriate User subclass
        return buildUser(record);
    }

    // -----------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------

    /**
     * Registers a new citizen account.
     *
     * <p>
     * Citizens are the only role that can self-register. Admin accounts
     * must be created directly in the database to prevent privilege escalation.
     *
     * @param name            full name
     * @param email           unique email address
     * @param password        plaintext password chosen by the user
     * @param confirmPassword repeated password for typo detection
     * @return {@code null} on success, or a human-readable error message on failure
     */
    public String registerCitizen(String name, String email,
            String password, String confirmPassword) {

        // 1. Validate form fields
        String formError = validation.validateRegistrationForm(
                name, email, password, confirmPassword);
        if (formError != null)
            return formError;

        // 2. Check for duplicate email
        if (userDAO.emailExists(email.trim().toLowerCase())) {
            return "An account with this email already exists. Please log in instead.";
        }

        // 3. Hash the password
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword(password, salt);

        // 4. Persist (Citizens only — role is hard-coded to prevent escalation)
        int newId = userDAO.createUser(
                name.trim(), email.trim().toLowerCase(),
                hash, salt, AppConstants.ROLE_CITIZEN);

        if (newId == -1) {
            return "Registration failed due to a database error. Please try again.";
        }

        return null; // success
    }

    // -----------------------------------------------------------------
    // Internal helper
    // -----------------------------------------------------------------

    /**
     * Converts a {@link UserRecord} into the appropriate {@link User} subclass.
     * Credentials (hash, salt) are intentionally discarded here.
     *
     * @param record the record returned by UserDAO
     * @return a {@link Citizen} or {@link Admin}; never {@code null}
     */
    private User buildUser(UserRecord record) {
        if (AppConstants.ROLE_ADMIN.equalsIgnoreCase(record.role)) {
            return new Admin(record.userId, record.name, record.email);
        } else {
            // Default to Citizen for any unrecognised role as a safe fallback
            return new Citizen(record.userId, record.name, record.email);
        }
    }
}

// ============================================================================
// service / IssueService
// ============================================================================

/**
 * Business-logic layer for civic-issue operations.
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Validate issue-form input before touching the database.</li>
 * <li>Enforce the rule that only {@link Admin} users may update issue
 * status.</li>
 * <li>Delegate all persistence to {@link IssueDAO}.</li>
 * </ul>
 *
 * <p>
 * No SQL is executed in this class.
 */
class IssueService {

    private final IssueDAO issueDAO;
    private final ValidationService validation;

    /** Creates an IssueService with default collaborators. */
    public IssueService() {
        this.issueDAO = new IssueDAO();
        this.validation = new ValidationService();
    }

    // -----------------------------------------------------------------
    // Citizen operations
    // -----------------------------------------------------------------

    /**
     * Validates and persists a new civic issue submitted by a citizen.
     *
     * @param reporterId  the {@code user_id} of the submitting citizen
     * @param category    selected category (must be in
     *                    {@link AppConstants#ISSUE_CATEGORIES})
     * @param location    location text
     * @param description description text
     * @return the persisted {@link Issue} object (with its generated issue_id set),
     *         or {@code null} if validation or persistence failed
     * @throws IllegalArgumentException if {@code reporterId} is non-positive
     */
    public Issue submitIssue(int reporterId, String category,
            String location, String description) {

        if (reporterId <= 0) {
            throw new IllegalArgumentException("reporterId must be a positive integer.");
        }

        // 1. Validate form inputs
        String formError = validation.validateIssueForm(category, location, description);
        if (formError != null) {
            System.out.println("[IssueService] Validation failed: " + formError);
            return null;
        }

        // 2. Build the model object (status defaults to "Pending" inside constructor)
        Issue issue = new Issue(
                category.trim(),
                reporterId,
                location.trim(),
                description.trim(),
                AppConstants.STATUS_PENDING);

        // 3. Persist via DAO
        boolean saved = issueDAO.createIssue(issue);
        if (!saved) {
            System.err.println("[IssueService] DB insert failed for reporterId=" + reporterId);
            return null;
        }

        return issue; // issueId is now populated from DB
    }

    /**
     * Returns all issues reported by a specific citizen, newest first.
     *
     * @param reporterId the citizen's {@code user_id}
     * @return unmodifiable list of issues; empty on error
     */
    public List<Issue> getIssuesByReporter(int reporterId) {
        if (reporterId <= 0)
            return Collections.emptyList();
        return issueDAO.getIssuesByReporter(reporterId);
    }

    // -----------------------------------------------------------------
    // Admin operations
    // -----------------------------------------------------------------

    /**
     * Returns all issues in the system, newest first.
     * Intended for the Admin dashboard only.
     *
     * @return unmodifiable list; empty on error
     */
    public List<Issue> getAllIssues() {
        return issueDAO.getAllIssues();
    }

    /**
     * Updates the status of a civic issue.
     *
     * <p>
     * Authorization rule: only users whose role is {@code "Admin"} may
     * call this method successfully. The check is performed here in the
     * service layer so it cannot be bypassed by any UI path.
     *
     * <p>
     * Status transition rules are enforced by {@link ValidationService}.
     *
     * @param issueId        the issue to update
     * @param newStatus      the desired new status
     * @param requestingUser the logged-in user making the request
     * @return {@code null} on success, or a human-readable error message
     */
    public String updateStatus(int issueId, String newStatus, User requestingUser) {

        // 1. Authorization check — only Admins may update status
        if (!(requestingUser instanceof Admin)) {
            return "Access denied: only administrators can update issue status.";
        }

        // 2. Validate the new status value
        if (!validation.validateStatus(newStatus)) {
            return "Invalid status value: " + newStatus;
        }

        // 3. Load current issue to check transition validity
        Issue current = issueDAO.getIssueById(issueId);
        if (current == null) {
            return "Issue #" + issueId + " not found.";
        }

        // 4. Enforce status-transition rules
        if (!validation.validateStatusTransition(current.getStatus(), newStatus)) {
            return "Cannot change status from '" + current.getStatus()
                    + "' to '" + newStatus + "'. "
                    + "Allowed flow: Pending → In Progress → Resolved.";
        }

        // 5. Persist the change
        boolean updated = issueDAO.updateIssueStatus(issueId, newStatus);
        if (!updated) {
            return "Database error: status could not be saved. Please try again.";
        }

        return null; // success
    }
}

// ============================================================================
// ui / LoginFrame
// ============================================================================

/**
 * CivicEye login screen.
 *
 * <p>
 * On successful authentication the window closes itself and the correct
 * dashboard opens on the Swing EDT:
 * <ul>
 * <li>{@link CitizenDashboard} for role "Citizen"</li>
 * <li>{@link AdminDashboard} for role "Admin"</li>
 * </ul>
 *
 * <p>
 * The DB round-trip runs inside a {@link SwingWorker} so the UI stays
 * responsive while credentials are being verified.
 */
class LoginFrame extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    static final Color BG_TOP = new Color(10, 18, 38);
    static final Color BG_BOTTOM = new Color(24, 20, 65);
    static final Color CARD_BG = new Color(18, 28, 60);
    static final Color CARD_BORDER = new Color(60, 75, 140);
    static final Color FIELD_BG = new Color(10, 16, 44);
    static final Color ACCENT = new Color(90, 150, 255);
    static final Color ACCENT_HOV = new Color(120, 175, 255);
    static final Color ACCENT_PRESS = new Color(55, 100, 210);
    static final Color FG_PRIMARY = new Color(220, 228, 255);
    static final Color FG_MUTED = new Color(110, 130, 190);
    static final Color BORDER_IDLE = new Color(45, 60, 110);
    static final Color ERROR_CLR = new Color(248, 113, 113);
    static final Color SUCCESS_CLR = new Color(74, 222, 128);

    // ── References ───────────────────────────────────────────────────
    private JTextField emailField;
    private JPasswordField passwordField;
    private JLabel statusLabel;
    private JButton loginButton;

    private final AuthService authService = new AuthService();

    // ─────────────────────────────────────────────────────────────────
    public LoginFrame() {
        applyLookAndFeel();
        setTitle(AppConstants.APP_NAME + " \u2014 Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        JPanel bg = buildBackground();
        bg.add(buildCard(), new GridBagConstraints());
        setContentPane(bg);

        pack();
        setLocationRelativeTo(null);
    }

    // ── Look-and-Feel ────────────────────────────────────────────────
    static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Button.focus", new Color(0, 0, 0, 0));
            UIManager.put("TextField.focus", new Color(0, 0, 0, 0));
        } catch (Exception ignored) {
        }
    }

    // ── Background panel (diagonal gradient) ─────────────────────────
    static JPanel buildBackground() {
        JPanel p = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, BG_TOP,
                        getWidth(), getHeight(), BG_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        p.setPreferredSize(new Dimension(500, 640));
        return p;
    }

    // ── Card ─────────────────────────────────────────────────────────
    private JPanel buildCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f,
                        getWidth() - 1.2f, getHeight() - 1.2f, 24, 24));
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(390, 568));

        // Single-column GridBagLayout gives precise spacing control
        card.setLayout(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.CENTER;
        Insets outer = new Insets(0, 44, 0, 44);

        // ── Header ──────────────────────────────────────────────────
        JLabel icon = uiLabel("\uD83C\uDFDB", 44, Font.PLAIN, FG_PRIMARY, SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 42));

        JLabel title = uiLabel(AppConstants.APP_NAME, 27, Font.BOLD, FG_PRIMARY,
                SwingConstants.CENTER);

        JLabel sub = uiLabel("Civic Issue Reporting System", 12, Font.PLAIN, FG_MUTED,
                SwingConstants.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(48, 62, 120));

        // ── Fields ──────────────────────────────────────────────────
        JLabel emailLbl = capsLabel("Email Address");
        emailField = buildTextField();
        withFocusBorder(emailField);

        JLabel passLbl = capsLabel("Password");
        passwordField = buildPasswordField();
        withFocusBorder(passwordField);

        // ── Status label ────────────────────────────────────────────
        statusLabel = uiLabel(" ", 12, Font.PLAIN, ERROR_CLR, SwingConstants.CENTER);

        // ── Primary button ──────────────────────────────────────────
        loginButton = primaryButton("Login");
        loginButton.addActionListener(e -> attemptLogin());
        emailField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> attemptLogin());

        // ── Registration link ────────────────────────────────────────
        JLabel prompt = uiLabel("Don\u2019t have an account?", 12, Font.PLAIN,
                FG_MUTED, SwingConstants.CENTER);
        JButton regBtn = linkButton("Create Account");
        regBtn.addActionListener(e -> new RegistrationDialog(this, authService).setVisible(true));

        // ── Layout rows ──────────────────────────────────────────────
        int r = 0;
        addRow(card, gc, outer, r++, icon, new Insets(36, 0, 6, 0));
        addRow(card, gc, outer, r++, title, new Insets(0, 0, 4, 0));
        addRow(card, gc, outer, r++, sub, new Insets(0, 0, 20, 0));
        addRow(card, gc, outer, r++, sep, new Insets(0, 44, 22, 44));
        addRow(card, gc, outer, r++, emailLbl, new Insets(0, 44, 7, 44));
        addRow(card, gc, outer, r++, emailField, new Insets(0, 44, 0, 44));
        addRow(card, gc, outer, r++, passLbl, new Insets(18, 44, 7, 44));
        addRow(card, gc, outer, r++, passwordField,
                new Insets(0, 44, 0, 44));
        addRow(card, gc, outer, r++, statusLabel,
                new Insets(8, 44, 0, 44));
        addRow(card, gc, outer, r++, loginButton,
                new Insets(14, 44, 22, 44));
        addRow(card, gc, outer, r++, prompt, new Insets(0, 0, 8, 0));
        addRow(card, gc, outer, r, regBtn, new Insets(0, 0, 36, 0));

        return card;
    }

    private static void addRow(JPanel p, GridBagConstraints gc,
            Insets defaultInsets, int row,
            JComponent comp, Insets insets) {
        gc.gridy = row;
        gc.insets = insets;
        p.add(comp, gc);
    }

    // ── Login logic ──────────────────────────────────────────────────
    private void attemptLogin() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (email.isEmpty() || password.isEmpty()) {
            showStatus("Please enter both email and password.", true);
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("Logging in\u2026");
        showStatus("", false);

        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return authService.login(email, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                loginButton.setText("Login");
                try {
                    User user = get();
                    if (user != null) {
                        showStatus("Welcome, " + user.getName() + "!", false);
                        // Brief pause so user sees success message
                        Timer t = new Timer(600, ev -> openDashboard(user));
                        t.setRepeats(false);
                        t.start();
                    } else {
                        showStatus("Invalid email or password. Please try again.", true);
                        passwordField.setText("");
                        passwordField.requestFocusInWindow();
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Connection error. Check database config.", true);
                }
            }
        }.execute();
    }

    private void openDashboard(User user) {
        dispose();
        SwingUtilities.invokeLater(() -> {
            if (user instanceof Citizen) {
                new CitizenDashboard((Citizen) user).setVisible(true);
            } else if (user instanceof Admin) {
                new AdminDashboard((Admin) user).setVisible(true);
            }
        });
    }

    void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isEmpty() ? " " : msg);
        statusLabel.setForeground(isError ? ERROR_CLR : SUCCESS_CLR);
    }

    // ── Component factories (package-visible so RegistrationDialog can reuse) ──

    static JTextField buildTextField() {
        JTextField f = new JTextField();
        styleInput(f);
        return f;
    }

    static JPasswordField buildPasswordField() {
        JPasswordField f = new JPasswordField();
        styleInput(f);
        return f;
    }

    static void styleInput(JTextField f) {
        f.setBackground(FIELD_BG);
        f.setForeground(FG_PRIMARY);
        f.setCaretColor(FG_PRIMARY);
        f.setSelectionColor(new Color(90, 150, 255, 120));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setPreferredSize(new Dimension(302, 42));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        f.setOpaque(true);
        idleBorder(f);
    }

    static void idleBorder(JTextField f) {
        f.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, BORDER_IDLE),
                BorderFactory.createEmptyBorder(8, 10, 6, 10)));
    }

    static void withFocusBorder(JTextField f) {
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                f.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 2, 0, ACCENT),
                        BorderFactory.createEmptyBorder(8, 10, 6, 10)));
            }

            @Override
            public void focusLost(FocusEvent e) {
                idleBorder(f);
            }
        });
    }

    static JButton primaryButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? ACCENT_PRESS
                        : getModel().isRollover() ? ACCENT_HOV
                                : ACCENT;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 15));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(302, 48));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        return btn;
    }

    static JButton linkButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(ACCENT);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setForeground(ACCENT_HOV);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setForeground(ACCENT);
            }
        });
        return btn;
    }

    static JLabel uiLabel(String text, int size, int style,
            Color color, int align) {
        JLabel l = new JLabel(text, align);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }

    static JLabel capsLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(FG_MUTED);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        return l;
    }
}

// ============================================================================
// ui / RegistrationDialog
// ============================================================================

/**
 * Modal dialog that allows a new user to self-register as a Citizen.
 *
 * <p>
 * The "Admin" role cannot be assigned through this dialog — {@link AuthService}
 * hard-codes the role to "Citizen" during registration to prevent privilege
 * escalation.
 *
 * <p>
 * Shares the styling constants and helper methods from {@link LoginFrame}.
 */
class RegistrationDialog extends JDialog {

    private JTextField nameField;
    private JTextField emailField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JLabel statusLabel;
    private JButton registerButton;

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────────
    RegistrationDialog(Frame parent, AuthService authService) {
        super(parent, "Create Account \u2014 CivicEye", true); // modal
        this.authService = authService;

        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel bg = LoginFrame.buildBackground();
        bg.setPreferredSize(new Dimension(460, 580));
        bg.add(buildCard(), new GridBagConstraints());
        setContentPane(bg);

        pack();
        setLocationRelativeTo(parent);
    }

    // ── Card ─────────────────────────────────────────────────────────
    private JPanel buildCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(LoginFrame.CARD_BG);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
                g2.setColor(LoginFrame.CARD_BORDER);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f,
                        getWidth() - 1.2f, getHeight() - 1.2f, 24, 24));
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(370, 530));
        card.setLayout(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.CENTER;

        // ── Header ───────────────────────────────────────────────────
        JLabel icon = LoginFrame.uiLabel("\u270F\uFE0F", 34, Font.PLAIN, LoginFrame.FG_PRIMARY, SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 34));
        JLabel title = LoginFrame.uiLabel("Create Account", 22, Font.BOLD, LoginFrame.FG_PRIMARY,
                SwingConstants.CENTER);
        JLabel sub = LoginFrame.uiLabel("Join CivicEye as a Citizen", 12, Font.PLAIN, LoginFrame.FG_MUTED,
                SwingConstants.CENTER);

        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(48, 62, 120));

        // ── Name ─────────────────────────────────────────────────────
        JLabel nameLbl = LoginFrame.capsLabel("Full Name");
        nameField = LoginFrame.buildTextField();
        LoginFrame.withFocusBorder(nameField);

        // ── Email ────────────────────────────────────────────────────
        JLabel emailLbl = LoginFrame.capsLabel("Email Address");
        emailField = LoginFrame.buildTextField();
        LoginFrame.withFocusBorder(emailField);

        // ── Password ──────────────────────────────────────────────────
        JLabel passLbl = LoginFrame.capsLabel("Password");
        passwordField = LoginFrame.buildPasswordField();
        LoginFrame.withFocusBorder(passwordField);

        // ── Confirm ───────────────────────────────────────────────────
        JLabel confirmLbl = LoginFrame.capsLabel("Confirm Password");
        confirmField = LoginFrame.buildPasswordField();
        LoginFrame.withFocusBorder(confirmField);
        confirmField.addActionListener(e -> attemptRegister());

        // ── Status ────────────────────────────────────────────────────
        statusLabel = LoginFrame.uiLabel(" ", 12, Font.PLAIN, LoginFrame.ERROR_CLR, SwingConstants.CENTER);

        // ── Buttons ───────────────────────────────────────────────────
        registerButton = LoginFrame.primaryButton("Create Account");
        registerButton.addActionListener(e -> attemptRegister());

        JButton cancelBtn = LoginFrame.linkButton("Cancel");
        cancelBtn.addActionListener(e -> dispose());

        // ── Layout rows ───────────────────────────────────────────────
        int r = 0;
        addRow(card, gc, r++, icon, new Insets(32, 0, 5, 0));
        addRow(card, gc, r++, title, new Insets(0, 0, 4, 0));
        addRow(card, gc, r++, sub, new Insets(0, 0, 16, 0));
        addRow(card, gc, r++, sep, new Insets(0, 40, 20, 40));
        addRow(card, gc, r++, nameLbl, new Insets(0, 40, 6, 40));
        addRow(card, gc, r++, nameField, new Insets(0, 40, 0, 40));
        addRow(card, gc, r++, emailLbl, new Insets(14, 40, 6, 40));
        addRow(card, gc, r++, emailField, new Insets(0, 40, 0, 40));
        addRow(card, gc, r++, passLbl, new Insets(14, 40, 6, 40));
        addRow(card, gc, r++, passwordField, new Insets(0, 40, 0, 40));
        addRow(card, gc, r++, confirmLbl, new Insets(14, 40, 6, 40));
        addRow(card, gc, r++, confirmField, new Insets(0, 40, 0, 40));
        addRow(card, gc, r++, statusLabel, new Insets(8, 40, 0, 40));
        addRow(card, gc, r++, registerButton, new Insets(14, 40, 10, 40));
        addRow(card, gc, r, cancelBtn, new Insets(0, 0, 28, 0));

        return card;
    }

    // ── Register action ───────────────────────────────────────────────
    private void attemptRegister() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirm = new String(confirmField.getPassword());

        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            showStatus("Please fill in all fields.", true);
            return;
        }

        registerButton.setEnabled(false);
        registerButton.setText("Creating account\u2026");
        showStatus("", false);

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return authService.registerCitizen(name, email, password, confirm);
            }

            @Override
            protected void done() {
                registerButton.setEnabled(true);
                registerButton.setText("Create Account");
                try {
                    String error = get();
                    if (error == null) {
                        // Success — show message and close after a short delay
                        showStatus("\u2713 Account created! You can now log in.", false);
                        Timer t = new Timer(1800, ev -> dispose());
                        t.setRepeats(false);
                        t.start();
                    } else {
                        showStatus(error, true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Registration failed. Check database config.", true);
                }
            }
        }.execute();
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isEmpty() ? " " : msg);
        statusLabel.setForeground(isError ? LoginFrame.ERROR_CLR : LoginFrame.SUCCESS_CLR);
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private static void addRow(JPanel p, GridBagConstraints gc,
            int row, JComponent comp, Insets insets) {
        gc.gridy = row;
        gc.insets = insets;
        p.add(comp, gc);
    }
}

// ============================================================================
// ui / IssueReportPanel
// ============================================================================

/**
 * Citizen issue-submission form panel.
 *
 * <p>
 * Validates inputs using {@link ValidationService} (via
 * {@link IssueService}), submits through the service layer, then invokes
 * {@code onSuccess} so the parent dashboard can refresh the table.
 *
 * <p>
 * The DB call runs inside a {@link SwingWorker} to keep the UI responsive.
 */
class IssueReportPanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG = new Color(14, 22, 50);
    private static final Color TITLE_BG = new Color(16, 25, 56);
    private static final Color FIELD_BG = new Color(10, 16, 44);
    private static final Color ACCENT = new Color(90, 150, 255);
    private static final Color ACCENT_H = new Color(120, 175, 255);
    private static final Color ACCENT_P = new Color(55, 100, 210);
    private static final Color FG_PRI = new Color(220, 228, 255);
    private static final Color FG_MUTED = new Color(110, 130, 190);
    private static final Color BORDER = new Color(35, 48, 95);
    private static final Color ERROR_CLR = new Color(248, 113, 113);
    private static final Color SUCCESS = new Color(74, 222, 128);
    private static final Color WARN_CLR = new Color(245, 180, 50);

    // ── Widgets ───────────────────────────────────────────────────────
    private JComboBox<String> categoryBox;
    private JTextField locationField;
    private JTextArea descArea;
    private JLabel charCounter;
    private JLabel statusLabel;
    private JButton submitBtn;
    private JButton clearBtn;

    // ── Dependencies ──────────────────────────────────────────────────
    private final Citizen citizen;
    private final IssueService issueService;
    private final Consumer<Issue> onSuccess; // called after successful submit

    // ─────────────────────────────────────────────────────────────────
    public IssueReportPanel(Citizen citizen,
            IssueService issueService,
            Consumer<Issue> onSuccess) {
        this.citizen = citizen;
        this.issueService = issueService;
        this.onSuccess = onSuccess;

        setBackground(BG);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER));

        add(buildTitleBar(), BorderLayout.NORTH);
        add(buildForm(), BorderLayout.CENTER);
    }

    // ── Title bar ─────────────────────────────────────────────────────
    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        bar.setBackground(TITLE_BG);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JLabel lbl = new JLabel("\uD83D\uDCDD  Report a New Issue");
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(new Color(160, 180, 240));
        bar.add(lbl);
        return bar;
    }

    // ── Form body ─────────────────────────────────────────────────────
    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG);
        form.setBorder(new EmptyBorder(20, 22, 20, 22));

        GridBagConstraints gc = fullWidthGC();

        // ── Category ──────────────────────────────────────────────────
        int row = 0;
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("CATEGORY"), gc);

        categoryBox = buildComboBox();
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 18, 0);
        form.add(categoryBox, gc);

        // ── Location ──────────────────────────────────────────────────
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("LOCATION"), gc);

        locationField = buildTextField();
        LoginFrame.withFocusBorder(locationField);
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 18, 0);
        form.add(locationField, gc);

        // ── Description ───────────────────────────────────────────────
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 6, 0);
        form.add(fieldLabel("DESCRIPTION"), gc);

        descArea = buildTextArea();
        JScrollPane descScroll = new JScrollPane(descArea);
        descScroll.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, BORDER),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        descScroll.setBackground(FIELD_BG);
        descScroll.getViewport().setBackground(FIELD_BG);
        descScroll.setPreferredSize(new Dimension(0, 130));
        descScroll.setMinimumSize(new Dimension(0, 100));
        descScroll.getVerticalScrollBar().setUI(new IssueTablePanel.DarkScrollBarUI());
        descArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                descScroll.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 2, 0, ACCENT),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                descScroll.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 2, 0, BORDER),
                        BorderFactory.createEmptyBorder(0, 0, 0, 0)));
            }
        });

        gc.gridy = row++;
        gc.weighty = 1.0; // description area grows vertically with panel resize
        gc.fill = GridBagConstraints.BOTH;
        gc.insets = new Insets(0, 0, 4, 0);
        form.add(descScroll, gc);
        gc.weighty = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;

        // ── Character counter ─────────────────────────────────────────
        charCounter = new JLabel("0 / " + AppConstants.MAX_DESCRIPTION_LENGTH,
                SwingConstants.RIGHT);
        charCounter.setFont(new Font("SansSerif", Font.PLAIN, 11));
        charCounter.setForeground(FG_MUTED);
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 14, 0);
        form.add(charCounter, gc);

        // Wire character counter
        descArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCounter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCounter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCounter();
            }
        });

        // ── Status / error label ──────────────────────────────────────
        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setForeground(ERROR_CLR);
        gc.gridy = row++;
        gc.insets = new Insets(0, 0, 12, 0);
        form.add(statusLabel, gc);

        // ── Buttons ───────────────────────────────────────────────────
        submitBtn = buildSubmitButton("Submit Issue");
        clearBtn = buildClearButton("Clear");
        submitBtn.addActionListener(e -> attemptSubmit());
        clearBtn.addActionListener(e -> clearForm());

        JPanel btnRow = new JPanel(new GridLayout(1, 2, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(submitBtn);
        btnRow.add(clearBtn);

        gc.gridy = row;
        gc.insets = new Insets(0, 0, 0, 0);
        form.add(btnRow, gc);

        return form;
    }

    // ── Submit logic ──────────────────────────────────────────────────
    private void attemptSubmit() {
        String category = (String) categoryBox.getSelectedItem();
        String location = locationField.getText().trim();
        String description = descArea.getText().trim();

        if (category == null || category.isBlank() || location.isBlank() || description.isBlank()) {
            showStatus("Please fill in all fields.", true);
            return;
        }

        setFormEnabled(false);
        submitBtn.setText("Submitting\u2026");
        showStatus("", false);

        new SwingWorker<Issue, Void>() {
            @Override
            protected Issue doInBackground() {
                return issueService.submitIssue(
                        citizen.getUserId(), category, location, description);
            }

            @Override
            protected void done() {
                setFormEnabled(true);
                submitBtn.setText("Submit Issue");
                try {
                    Issue submitted = get();
                    if (submitted != null) {
                        showStatus("\u2713 Issue #" + submitted.getIssueId()
                                + " submitted successfully!", false);
                        clearForm();
                        if (onSuccess != null)
                            onSuccess.accept(submitted);
                    } else {
                        showStatus("Submission failed. Check your inputs and try again.", true);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    showStatus("Database error during submission.", true);
                }
            }
        }.execute();
    }

    private void clearForm() {
        categoryBox.setSelectedIndex(0);
        locationField.setText("");
        descArea.setText("");
        showStatus("", false);
        categoryBox.requestFocusInWindow();
    }

    private void setFormEnabled(boolean enabled) {
        categoryBox.setEnabled(enabled);
        locationField.setEnabled(enabled);
        descArea.setEnabled(enabled);
        submitBtn.setEnabled(enabled);
        clearBtn.setEnabled(enabled);
    }

    private void showStatus(String msg, boolean isError) {
        statusLabel.setText(msg.isBlank() ? " " : msg);
        statusLabel.setForeground(isError ? ERROR_CLR : SUCCESS);
    }

    private void updateCounter() {
        int len = descArea.getText().length();
        charCounter.setText(len + " / " + AppConstants.MAX_DESCRIPTION_LENGTH);
        if (len > AppConstants.MAX_DESCRIPTION_LENGTH) {
            charCounter.setForeground(ERROR_CLR);
        } else if (len > AppConstants.MAX_DESCRIPTION_LENGTH * 0.9) {
            charCounter.setForeground(WARN_CLR);
        } else {
            charCounter.setForeground(FG_MUTED);
        }
    }

    // ── Component factories ───────────────────────────────────────────
    private JComboBox<String> buildComboBox() {
        JComboBox<String> box = new JComboBox<>(AppConstants.ISSUE_CATEGORIES);
        box.setBackground(FIELD_BG);
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 14));
        box.setPreferredSize(new Dimension(0, 42));
        box.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(30, 55, 120) : FIELD_BG);
                setForeground(FG_PRI);
                setFont(new Font("SansSerif", Font.PLAIN, 14));
                setBorder(new EmptyBorder(6, 10, 6, 10));
                return this;
            }
        });
        return box;
    }

    private JTextField buildTextField() {
        JTextField f = new JTextField();
        f.setBackground(FIELD_BG);
        f.setForeground(FG_PRI);
        f.setCaretColor(FG_PRI);
        f.setSelectionColor(new Color(90, 150, 255, 120));
        f.setFont(new Font("SansSerif", Font.PLAIN, 14));
        f.setPreferredSize(new Dimension(0, 42));
        LoginFrame.idleBorder(f);
        f.setOpaque(true);
        return f;
    }

    private JTextArea buildTextArea() {
        JTextArea ta = new JTextArea();
        ta.setBackground(FIELD_BG);
        ta.setForeground(FG_PRI);
        ta.setCaretColor(FG_PRI);
        ta.setSelectionColor(new Color(90, 150, 255, 120));
        ta.setFont(new Font("SansSerif", Font.PLAIN, 14));
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(8, 10, 8, 10));
        return ta;
    }

    private JButton buildSubmitButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color base = !isEnabled() ? new Color(50, 80, 160)
                        : getModel().isPressed() ? ACCENT_P
                                : getModel().isRollover() ? ACCENT_H
                                        : ACCENT;
                g2.setColor(base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(isEnabled() ? Color.WHITE : new Color(160, 180, 220));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 44));
        return btn;
    }

    private JButton buildClearButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? new Color(50, 65, 115)
                        : getModel().isRollover() ? new Color(40, 55, 100)
                                : new Color(28, 40, 80);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(55, 80, 150));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(160, 185, 255));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(0, 44));
        return btn;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(new Color(110, 130, 190));
        return l;
    }

    private static GridBagConstraints fullWidthGC() {
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        return gc;
    }
}

// ============================================================================
// ui / IssueTablePanel
// ============================================================================

/**
 * Reusable panel that displays a list of {@link Issue} objects in a styled
 * table.
 *
 * <p>
 * Used by both {@link CitizenDashboard} (citizen's own reports) and
 * {@link AdminDashboard} (all reports). The admin version additionally calls
 * {@link #getSelectedIssue()} to drive its status-update workflow.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Alternating row colours</li>
 * <li>Coloured status badge per row (Pending / In Progress / Resolved)</li>
 * <li>Description column auto-truncated at 45 chars with full-text tooltip</li>
 * <li>Click-to-sort on any column header</li>
 * <li>Empty-state illustration when no issues exist</li>
 * <li>Dark custom scroll bar</li>
 * </ul>
 */
class IssueTablePanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG = new Color(10, 18, 38);
    static final Color TABLE_BG = new Color(14, 22, 50);
    static final Color ROW_ALT = new Color(17, 27, 58);
    static final Color ROW_SEL = new Color(28, 55, 120);
    private static final Color HDR_BG = new Color(22, 34, 74);
    private static final Color HDR_FG = new Color(160, 180, 240);
    static final Color FG_PRI = new Color(220, 228, 255);
    private static final Color BORDER = new Color(35, 48, 95);

    // Status badge colours [background, foreground]
    static final Color[] PEND_COLORS = { new Color(55, 40, 8), new Color(245, 180, 50) };
    static final Color[] PROG_COLORS = { new Color(12, 35, 90), new Color(100, 165, 255) };
    static final Color[] RESV_COLORS = { new Color(12, 50, 30), new Color(80, 215, 130) };

    // Column definitions: name, min, pref, max
    private static final Object[][] COL_DEF = {
            { "#", 40, 45, 50 },
            { "Category", 80, 110, 140 },
            { "Location", 100, 150, 220 },
            { "Description", 160, 250, 9999 },
            { "Status", 110, 120, 120 },
            { "Submitted", 120, 135, 145 },
    };

    // ── State ─────────────────────────────────────────────────────────
    private final DefaultTableModel model;
    private final JTable table;
    private final List<Issue> issues = new ArrayList<>();
    private final JPanel emptyPanel;
    private final JScrollPane scrollPane;
    private final JPanel contentArea; // CardLayout: "TABLE" / "EMPTY"

    // ─────────────────────────────────────────────────────────────────
    public IssueTablePanel(String title) {
        super(new BorderLayout());
        setBackground(BG);

        // Title bar
        add(buildTitleBar(title), BorderLayout.NORTH);

        // Non-editable model; column-0 class is Integer so numeric sorting works
        model = new DefaultTableModel(colNames(), 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int c) {
                return c == 0 ? Integer.class : String.class;
            }
        };

        table = buildTable();
        emptyPanel = buildEmptyPanel();

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBackground(TABLE_BG);
        scrollPane.getViewport().setBackground(TABLE_BG);
        scrollPane.getVerticalScrollBar().setUI(new DarkScrollBarUI());

        contentArea = new JPanel(new CardLayout());
        contentArea.setBackground(BG);
        contentArea.add(scrollPane, "TABLE");
        contentArea.add(emptyPanel, "EMPTY");
        add(contentArea, BorderLayout.CENTER);

        showEmpty();
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Replaces the current contents with the supplied issue list.
     * Safe to call from any thread — internally dispatches to the EDT.
     */
    public void loadIssues(List<Issue> newIssues) {
        issues.clear();
        if (newIssues != null)
            issues.addAll(newIssues);

        SwingUtilities.invokeLater(() -> {
            model.setRowCount(0);
            if (issues.isEmpty()) {
                showEmpty();
                return;
            }
            for (int i = 0; i < issues.size(); i++) {
                Issue iss = issues.get(i);
                model.addRow(new Object[] {
                        i + 1,
                        iss.getCategory(),
                        iss.getLocation(),
                        iss.getDescription(),
                        iss.getStatus(),
                        iss.getFormattedCreatedAt()
                });
            }
            showTable();
        });
    }

    /**
     * Returns the {@link Issue} corresponding to the currently selected row,
     * or {@code null} if nothing is selected.
     */
    public Issue getSelectedIssue() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0)
            return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return (modelRow >= 0 && modelRow < issues.size()) ? issues.get(modelRow) : null;
    }

    /** Programmatically clears the row selection. */
    public void clearSelection() {
        table.clearSelection();
    }

    /** Returns the raw {@link JTable} for attaching external listeners. */
    public JTable getTable() {
        return table;
    }

    /** Returns the current number of displayed rows. */
    public int getIssueCount() {
        return issues.size();
    }

    // ── Build helpers ─────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(model);
        t.setBackground(TABLE_BG);
        t.setForeground(FG_PRI);
        t.setFont(new Font("SansSerif", Font.PLAIN, 13));
        t.setRowHeight(36);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(ROW_SEL);
        t.setSelectionForeground(FG_PRI);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // ── Header ──
        JTableHeader header = t.getTableHeader();
        header.setBackground(HDR_BG);
        header.setForeground(HDR_FG);
        header.setFont(new Font("SansSerif", Font.BOLD, 12));
        header.setPreferredSize(new Dimension(0, 40));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());

        // ── Column widths & renderers ──
        for (int c = 0; c < COL_DEF.length; c++) {
            TableColumn tc = t.getColumnModel().getColumn(c);
            tc.setMinWidth((int) COL_DEF[c][1]);
            tc.setPreferredWidth((int) COL_DEF[c][2]);
            tc.setMaxWidth((int) COL_DEF[c][3]);
        }
        t.getColumnModel().getColumn(0).setCellRenderer(new CenteredRenderer());
        t.getColumnModel().getColumn(1).setCellRenderer(new PaddedRenderer());
        t.getColumnModel().getColumn(2).setCellRenderer(new PaddedRenderer());
        t.getColumnModel().getColumn(3).setCellRenderer(new DescriptionRenderer());
        t.getColumnModel().getColumn(4).setCellRenderer(new StatusBadgeRenderer());
        t.getColumnModel().getColumn(5).setCellRenderer(new PaddedRenderer());

        return t;
    }

    private JPanel buildTitleBar(String title) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 10));
        bar.setBackground(new Color(16, 25, 56));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 13));
        lbl.setForeground(new Color(160, 180, 240));
        bar.add(lbl);
        return bar;
    }

    private JPanel buildEmptyPanel() {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(TABLE_BG);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

        JLabel ico = new JLabel("\uD83D\uDCCB", SwingConstants.CENTER);
        ico.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 52));
        ico.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel h = new JLabel("No issues yet", SwingConstants.CENTER);
        h.setFont(new Font("SansSerif", Font.BOLD, 17));
        h.setForeground(FG_PRI);
        h.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel(
                "<html><div style='text-align:center; color:#6e82be; width:260px;'>"
                        + "Submit a civic issue using the form<br>to start tracking it here.</div></html>",
                SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        inner.add(ico);
        inner.add(Box.createVerticalStrut(10));
        inner.add(h);
        inner.add(Box.createVerticalStrut(8));
        inner.add(sub);

        outer.add(inner);
        return outer;
    }

    private void showEmpty() {
        ((CardLayout) contentArea.getLayout()).show(contentArea, "EMPTY");
    }

    private void showTable() {
        ((CardLayout) contentArea.getLayout()).show(contentArea, "TABLE");
    }

    private static String[] colNames() {
        String[] n = new String[COL_DEF.length];
        for (int i = 0; i < n.length; i++)
            n[i] = (String) COL_DEF[i][0];
        return n;
    }

    // ── Cell renderers ────────────────────────────────────────────────

    static class PaddedRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setForeground(sel ? new Color(230, 238, 255) : new Color(190, 205, 240));
            setBackground(sel ? ROW_SEL : (r % 2 == 0 ? TABLE_BG : ROW_ALT));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setFont(new Font("SansSerif", Font.PLAIN, 13));
            setOpaque(true);
            return this;
        }
    }

    static class CenteredRenderer extends PaddedRenderer {
        CenteredRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    static class DescriptionRenderer extends PaddedRenderer {
        private static final int MAX = 45;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            String full = v != null ? v.toString() : "";
            String shown = full.length() > MAX ? full.substring(0, MAX) + "\u2026" : full;
            super.getTableCellRendererComponent(t, shown, sel, foc, r, c);
            setToolTipText(
                    "<html><body style='width:300px; padding:4px;'>" + full + "</body></html>");
            return this;
        }
    }

    /** Renders the Status column as a coloured pill badge. */
    static class StatusBadgeRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {

            String status = v != null ? v.toString() : "";
            final Color[] cols;
            switch (status) {
                case AppConstants.STATUS_IN_PROGRESS:
                    cols = PROG_COLORS;
                    break;
                case AppConstants.STATUS_RESOLVED:
                    cols = RESV_COLORS;
                    break;
                default:
                    cols = PEND_COLORS;
                    break;
            }
            final Color rowBg = sel ? ROW_SEL : (r % 2 == 0 ? TABLE_BG : ROW_ALT);

            // Outer wrapper (sets the alternating row colour)
            JPanel wrap = new JPanel(new GridBagLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(rowBg);
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
            };
            wrap.setOpaque(false);

            // The pill
            JLabel badge = new JLabel(status, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(cols[0]);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    super.paintComponent(g);
                }
            };
            badge.setFont(new Font("SansSerif", Font.BOLD, 11));
            badge.setForeground(cols[1]);
            badge.setOpaque(false);
            badge.setPreferredSize(new Dimension(100, 22));

            wrap.add(badge);
            return wrap;
        }
    }

    /** Styled table-header renderer. */
    static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEFT);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v,
                boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, foc, r, c);
            setBackground(HDR_BG);
            setForeground(HDR_FG);
            setFont(new Font("SansSerif", Font.BOLD, 12));
            setBorder(new EmptyBorder(0, 12, 0, 12));
            return this;
        }
    }

    /** Minimal dark scrollbar — no arrow buttons, slim thumb. */
    static class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(55, 75, 140);
            thumbDarkShadowColor = new Color(55, 75, 140);
            thumbHighlightColor = new Color(55, 75, 140);
            thumbLightShadowColor = new Color(55, 75, 140);
            trackColor = new Color(14, 22, 50);
            trackHighlightColor = new Color(14, 22, 50);
        }

        @Override
        protected JButton createDecreaseButton(int o) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            return b;
        }
    }
}

// ============================================================================
// ui / CitizenDashboard
// ============================================================================

/**
 * Full Citizen Dashboard — main screen shown after citizen login.
 *
 * <p>
 * Layout:
 * 
 * <pre>
 * ┌─ Header ────────────────────────────────────────────────────────────────┐
 * │ 🏛 CivicEye │ Welcome, NAME          ⭐ N pts  [Refresh ↺]  [Logout]   │
 * ├─ Left (form) ──────────────────────┬─ Right (table) ────────────────────┤
 * │  📝 Report a New Issue             │  📋 My Reported Issues              │
 * │  Category  [combobox]              │  # │ Category │ Location │ Status  │
 * │  Location  [textfield]             │  ──┼──────────┼──────────┼──────── │
 * │  Description [textarea] 0/2000     │  … rows …                          │
 * │  [Submit Issue] [Clear]            │                                    │
 * ├────────────────────────────────────┴────────────────────────────────────┤
 * │ Status bar  •  Last refreshed: 10:30 AM  •  N issues                   │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>
 * F5 / Refresh button reloads the issue list from the database.
 */
class CitizenDashboard extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG_DARK = new Color(10, 18, 38);
    private static final Color HDR_BG = new Color(18, 28, 60);
    private static final Color HDR_BDR = new Color(35, 50, 100);
    private static final Color FG_PRI = new Color(220, 228, 255);
    private static final Color FG_MUTED = new Color(110, 130, 190);
    private static final Color ACCENT = new Color(90, 150, 255);
    private static final Color SUCCESS = new Color(74, 222, 128);
    private static final Color DIVIDER = new Color(35, 50, 100);
    private static final Color BAR_BG = new Color(14, 22, 50);

    // ── State ─────────────────────────────────────────────────────────
    private final Citizen citizen;
    private final IssueService issueService = new IssueService();

    // ── Widget refs ────────────────────────────────────────────────────
    private JLabel statusBarLabel;
    private IssueTablePanel tablePanel;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    // ─────────────────────────────────────────────────────────────────
    public CitizenDashboard(Citizen citizen) {
        this.citizen = citizen;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Citizen Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.CITIZEN_DASH_WIDTH, AppConstants.CITIZEN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(900, 600));

        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMain(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // F5 shortcut for refresh
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f5, "refresh");
        getRootPane().getActionMap().put("refresh", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshTable();
            }
        });

        // Load issues on open
        refreshTable();
    }

    // ── Header ────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HDR_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HDR_BDR),
                new EmptyBorder(12, 22, 12, 22)));

        // ── Left: logo + name ──────────────────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel("\uD83C\uDFDB");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        icon.setForeground(FG_PRI);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRI);

        JLabel sep1 = new JLabel("\u2502");
        sep1.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sep1.setForeground(new Color(45, 60, 110));

        JLabel welcome = new JLabel("Welcome, " + citizen.getName());
        welcome.setFont(new Font("SansSerif", Font.PLAIN, 14));
        welcome.setForeground(FG_MUTED);

        left.add(icon);
        left.add(appName);
        left.add(sep1);
        left.add(welcome);

        // ── Right: refresh + logout ───────────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton refreshBtn = buildIconButton("\u21BA  Refresh", new Color(40, 60, 120),
                new Color(60, 90, 170));
        refreshBtn.addActionListener(e -> refreshTable());

        JButton logoutBtn = buildIconButton("Logout", new Color(130, 35, 35),
                new Color(200, 70, 70));
        logoutBtn.addActionListener(e -> logout());

        right.add(refreshBtn);
        right.add(logoutBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JButton buildIconButton(String label, Color base, Color hover) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? base.darker()
                        : getModel().isRollover() ? hover
                                : base;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(90, 32));
        return btn;
    }

    // ── Main content: split form | table ──────────────────────────────
    private JSplitPane buildMain() {
        // Form panel
        IssueReportPanel reportPanel = new IssueReportPanel(
                citizen,
                issueService,
                submittedIssue -> refreshTable());
        reportPanel.setPreferredSize(new Dimension(360, 0));
        reportPanel.setMinimumSize(new Dimension(300, 0));

        // Table panel
        tablePanel = new IssueTablePanel("📋  My Reported Issues");
        tablePanel.setMinimumSize(new Dimension(350, 0));

        // Split pane
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                reportPanel, tablePanel);
        split.setDividerLocation(360);
        split.setDividerSize(5);
        split.setResizeWeight(0.0); // table panel absorbs extra width
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(DIVIDER);

        // Recolour the divider itself
        SplitPaneUI spui = split.getUI();
        if (spui instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui) {
            bui.getDivider().setBackground(DIVIDER);
        }

        return split;
    }

    // ── Status bar ────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                new EmptyBorder(6, 16, 6, 16)));

        JLabel left = new JLabel(AppConstants.APP_NAME + " v1.0  \u2022  Citizen Portal");
        left.setFont(new Font("SansSerif", Font.PLAIN, 11));
        left.setForeground(FG_MUTED);

        statusBarLabel = new JLabel("Loading\u2026");
        statusBarLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBarLabel.setForeground(FG_MUTED);

        bar.add(left, BorderLayout.WEST);
        bar.add(statusBarLabel, BorderLayout.EAST);
        return bar;
    }

    // ── Data refresh ──────────────────────────────────────────────────

    /**
     * Reloads the citizen's issue list from the database in a background thread.
     * Updates the table and the status bar on the EDT when done.
     */
    public void refreshTable() {
        statusBarLabel.setText("Refreshing\u2026");
        int uid = citizen.getUserId();

        new SwingWorker<List<Issue>, Void>() {
            @Override
            protected List<Issue> doInBackground() {
                return issueService.getIssuesByReporter(uid);
            }

            @Override
            protected void done() {
                try {
                    List<Issue> issues = get();
                    tablePanel.loadIssues(issues);
                    String time = LocalTime.now().format(TIME_FMT);
                    statusBarLabel.setText(
                            "Last refreshed: " + time
                                    + "  \u2022  " + issues.size() + " issue(s)  \u2022  "
                                    + "Press F5 to refresh");
                } catch (InterruptedException | ExecutionException ex) {
                    statusBarLabel.setText("Error loading issues — check database connection.");
                }
            }
        }.execute();
    }

    // ── Logout ────────────────────────────────────────────────────────
    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }
}

// ============================================================================
// ui / AdminDashboard
// ============================================================================

/**
 * Full Admin Dashboard — shown after admin login.
 *
 * <p>
 * Layout:
 * 
 * <pre>
 * ┌─ Header ─────────────────────────────────────────────────────────────────┐
 * │ 🏛 CivicEye  ⚡ ADMIN  Admin Name      [↺ Refresh]  [🔍 Filter]  [Logout]│
 * ├─ Top: Issue Table (all issues) ─────────────────────────────────────────┤
 * │  # │ Category │ Location │ Description │ Status │ Reporter ID │ Date     │
 * │  … rows, click to select …                                              │
 * ├─ Bottom: Status Update Panel ───────────────────────────────────────────┤
 * │  Selected: #3 — Roads — MG Road        [In Progress ▼]  [Update Status] │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ CivicEye v1.0 • Admin Portal  •  Last refreshed: 10:30 AM • N issues   │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>
 * Features:
 * <ul>
 * <li>View all civic issues from all reporters</li>
 * <li>Click a row to load it into the status-update panel</li>
 * <li>Select a new status (respects workflow Pending → In Progress →
 * Resolved)</li>
 * <li>Filter table by status (All / Pending / In Progress / Resolved)</li>
 * <li>F5 / Refresh button reloads from the database</li>
 * <li>All DB calls run in SwingWorker for a responsive UI</li>
 * </ul>
 */
class AdminDashboard extends JFrame {

    // ── Colour palette ────────────────────────────────────────────────
    private static final Color BG_DARK = new Color(10, 18, 38);
    private static final Color HDR_BG = new Color(18, 28, 60);
    private static final Color HDR_BDR = new Color(35, 50, 100);
    private static final Color FG_PRI = new Color(220, 228, 255);
    private static final Color FG_MUTED = new Color(110, 130, 190);
    private static final Color ACCENT = new Color(90, 150, 255);
    private static final Color ADMIN_TAG = new Color(240, 160, 60);
    private static final Color DIVIDER = new Color(35, 50, 100);
    private static final Color BAR_BG = new Color(14, 22, 50);
    private static final Color PANEL_BG = new Color(16, 26, 55);
    private static final Color ERROR_CLR = new Color(248, 113, 113);
    private static final Color SUCCESS = new Color(74, 222, 128);

    // ── State ─────────────────────────────────────────────────────────
    private final Admin admin;
    private final IssueService issueService = new IssueService();

    // ── Widget refs ───────────────────────────────────────────────────
    private IssueTablePanel tablePanel;
    private JLabel statusBarLabel;
    private JLabel selectedInfoLabel;
    private JComboBox<String> statusCombo;
    private JButton updateBtn;
    private JComboBox<String> filterCombo;
    private JLabel updateResultLabel;

    private List<Issue> allIssues = List.of(); // master list (unfiltered)
    private Issue selected = null; // row currently selected

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a");

    // ─────────────────────────────────────────────────────────────────
    public AdminDashboard(Admin admin) {
        this.admin = admin;
        LoginFrame.applyLookAndFeel();

        setTitle(AppConstants.APP_NAME + " \u2014 Admin Dashboard");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppConstants.ADMIN_DASH_WIDTH, AppConstants.ADMIN_DASH_HEIGHT);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(960, 620));

        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMain(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // F5 shortcut
        KeyStroke f5 = KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f5, "refresh");
        getRootPane().getActionMap().put("refresh", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                refreshTable();
            }
        });

        refreshTable();
    }

    // ── Header ────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HDR_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HDR_BDR),
                new EmptyBorder(12, 22, 12, 22)));

        // ── Left: logo + admin badge + name ───────────────────────────
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel icon = new JLabel("\uD83C\uDFDB");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22));
        icon.setForeground(FG_PRI);

        JLabel appName = new JLabel(AppConstants.APP_NAME);
        appName.setFont(new Font("SansSerif", Font.BOLD, 18));
        appName.setForeground(FG_PRI);

        JLabel sep = new JLabel("\u2502");
        sep.setFont(new Font("SansSerif", Font.PLAIN, 18));
        sep.setForeground(new Color(45, 60, 110));

        JLabel adminBadge = buildAdminBadge();
        JLabel adminName = new JLabel(admin.getName());
        adminName.setFont(new Font("SansSerif", Font.PLAIN, 14));
        adminName.setForeground(FG_MUTED);

        left.add(icon);
        left.add(appName);
        left.add(sep);
        left.add(adminBadge);
        left.add(adminName);

        // ── Right: filter + refresh + logout ──────────────────────────
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        // Status filter combo
        JLabel filterLbl = new JLabel("Filter:");
        filterLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        filterLbl.setForeground(FG_MUTED);

        String[] filters = { "All Issues",
                AppConstants.STATUS_PENDING,
                AppConstants.STATUS_IN_PROGRESS,
                AppConstants.STATUS_RESOLVED };
        filterCombo = new JComboBox<>(filters);
        styleHeaderCombo(filterCombo);
        filterCombo.addActionListener(e -> applyFilter());

        JButton refreshBtn = buildHeaderButton("\u21BA  Refresh",
                new Color(30, 55, 110), new Color(50, 90, 170));
        refreshBtn.addActionListener(e -> refreshTable());

        JButton logoutBtn = buildHeaderButton("Logout",
                new Color(120, 30, 30), new Color(190, 65, 65));
        logoutBtn.addActionListener(e -> logout());

        right.add(filterLbl);
        right.add(filterCombo);
        right.add(Box.createHorizontalStrut(4));
        right.add(refreshBtn);
        right.add(logoutBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JLabel buildAdminBadge() {
        JLabel badge = new JLabel("  \u26A1 ADMIN  ", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(55, 33, 8));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(110, 65, 18));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("SansSerif", Font.BOLD, 11));
        badge.setForeground(ADMIN_TAG);
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(4, 2, 4, 2));
        return badge;
    }

    // ── Main content: table (top) + update panel (bottom) ─────────────
    private JSplitPane buildMain() {
        tablePanel = new IssueTablePanel("\uD83D\uDEE1\uFE0F  All Civic Issues");
        tablePanel.setMinimumSize(new Dimension(0, 200));

        // Wire row selection → populate update panel
        tablePanel.getTable().getSelectionModel()
                .addListSelectionListener(e -> {
                    if (!e.getValueIsAdjusting())
                        onRowSelected();
                });

        JPanel updatePanel = buildUpdatePanel();
        updatePanel.setPreferredSize(new Dimension(0, 120));
        updatePanel.setMinimumSize(new Dimension(0, 110));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                tablePanel, updatePanel);
        split.setResizeWeight(1.0); // table gets all extra vertical space
        split.setDividerSize(5);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(DIVIDER);

        SplitPaneUI spui = split.getUI();
        if (spui instanceof javax.swing.plaf.basic.BasicSplitPaneUI bui) {
            bui.getDivider().setBackground(DIVIDER);
        }

        return split;
    }

    // ── Status Update Panel ───────────────────────────────────────────
    private JPanel buildUpdatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                new EmptyBorder(16, 24, 16, 24)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(0, 0, 0, 14);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.NONE;

        // Section label
        JLabel sectionLbl = new JLabel("UPDATE ISSUE STATUS");
        sectionLbl.setFont(new Font("SansSerif", Font.BOLD, 11));
        sectionLbl.setForeground(FG_MUTED);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 4;
        panel.add(sectionLbl, gc);

        // Selected issue info
        selectedInfoLabel = new JLabel("← Select a row in the table above");
        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectedInfoLabel.setForeground(FG_MUTED);
        gc.gridy = 1;
        gc.gridwidth = 4;
        gc.insets = new Insets(10, 0, 0, 14);
        panel.add(selectedInfoLabel, gc);

        // Status combo
        String[] statuses = { AppConstants.STATUS_PENDING,
                AppConstants.STATUS_IN_PROGRESS,
                AppConstants.STATUS_RESOLVED };
        statusCombo = new JComboBox<>(statuses);
        styleFormCombo(statusCombo);
        statusCombo.setEnabled(false);
        statusCombo.setPreferredSize(new Dimension(160, 36));

        JLabel newStatusLbl = new JLabel("New Status:");
        newStatusLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        newStatusLbl.setForeground(FG_PRI);

        // Update button
        updateBtn = buildUpdateButton("Update Status");
        updateBtn.setEnabled(false);
        updateBtn.addActionListener(e -> attemptStatusUpdate());

        // Result label
        updateResultLabel = new JLabel(" ");
        updateResultLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        updateResultLabel.setForeground(SUCCESS);

        gc.gridwidth = 1;
        gc.gridy = 2;
        gc.insets = new Insets(12, 0, 0, 14);

        gc.gridx = 0;
        panel.add(newStatusLbl, gc);
        gc.gridx = 1;
        panel.add(statusCombo, gc);
        gc.gridx = 2;
        panel.add(updateBtn, gc);
        gc.gridx = 3;
        gc.insets = new Insets(12, 0, 0, 0);
        panel.add(updateResultLabel, gc);

        // Filler
        gc.gridx = 4;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(Box.createHorizontalGlue(), gc);

        return panel;
    }

    // ── Status bar ────────────────────────────────────────────────────
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(BAR_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DIVIDER),
                new EmptyBorder(6, 16, 6, 16)));

        JLabel left = new JLabel(AppConstants.APP_NAME + " v1.0  \u2022  Admin Portal");
        left.setFont(new Font("SansSerif", Font.PLAIN, 11));
        left.setForeground(FG_MUTED);

        statusBarLabel = new JLabel("Loading\u2026");
        statusBarLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statusBarLabel.setForeground(FG_MUTED);

        bar.add(left, BorderLayout.WEST);
        bar.add(statusBarLabel, BorderLayout.EAST);
        return bar;
    }

    // ── Actions ───────────────────────────────────────────────────────

    /** Loads all issues from DB in a background thread and refreshes the table. */
    public void refreshTable() {
        statusBarLabel.setText("Refreshing\u2026");
        clearUpdatePanel();

        new SwingWorker<List<Issue>, Void>() {
            @Override
            protected List<Issue> doInBackground() {
                return issueService.getAllIssues();
            }

            @Override
            protected void done() {
                try {
                    allIssues = get();
                    applyFilter(); // respects current filterCombo selection
                    String time = LocalTime.now().format(TIME_FMT);
                    statusBarLabel.setText(
                            "Last refreshed: " + time
                                    + "  \u2022  " + allIssues.size() + " total issue(s)"
                                    + "  \u2022  Press F5 to refresh");
                } catch (InterruptedException | ExecutionException ex) {
                    statusBarLabel.setText("Error loading issues — check database.");
                }
            }
        }.execute();
    }

    /** Filters the displayed rows based on filterCombo selection. */
    private void applyFilter() {
        String filter = (String) filterCombo.getSelectedItem();
        if (filter == null || filter.equals("All Issues")) {
            tablePanel.loadIssues(allIssues);
        } else {
            List<Issue> filtered = allIssues.stream()
                    .filter(i -> i.getStatus().equalsIgnoreCase(filter))
                    .toList();
            tablePanel.loadIssues(filtered);
        }
        clearUpdatePanel();
    }

    /** Called when a row is selected in the table. */
    private void onRowSelected() {
        selected = tablePanel.getSelectedIssue();
        if (selected == null) {
            clearUpdatePanel();
            return;
        }

        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        selectedInfoLabel.setForeground(FG_PRI);
        selectedInfoLabel.setText(
                "Issue #" + selected.getIssueId()
                        + "  \u2014  " + selected.getCategory()
                        + "  \u2022  " + selected.getLocation()
                        + "  \u2022  Current status: "
                        + statusBadgeText(selected.getStatus()));

        // Pre-select the current status in the combo
        statusCombo.setSelectedItem(selected.getStatus());
        statusCombo.setEnabled(true);
        updateBtn.setEnabled(true);
        updateResultLabel.setText(" ");
    }

    /** Submits the status update to the service layer in a background thread. */
    private void attemptStatusUpdate() {
        if (selected == null)
            return;

        String newStatus = (String) statusCombo.getSelectedItem();
        if (newStatus == null)
            return;

        updateBtn.setEnabled(false);
        updateBtn.setText("Updating\u2026");
        updateResultLabel.setText("");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return issueService.updateStatus(selected.getIssueId(), newStatus, admin);
            }

            @Override
            protected void done() {
                updateBtn.setEnabled(true);
                updateBtn.setText("Update Status");
                try {
                    String error = get();
                    if (error == null) {
                        updateResultLabel.setForeground(SUCCESS);
                        updateResultLabel.setText(
                                "\u2713 Issue #" + selected.getIssueId()
                                        + " updated to \"" + newStatus + "\"");
                        // Refresh table to show updated badge
                        refreshTable();
                    } else {
                        updateResultLabel.setForeground(ERROR_CLR);
                        updateResultLabel.setText("\u26A0 " + error);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    updateResultLabel.setForeground(ERROR_CLR);
                    updateResultLabel.setText("\u26A0 Database error during update.");
                }
            }
        }.execute();
    }

    private void clearUpdatePanel() {
        selected = null;
        selectedInfoLabel.setText("\u2190 Select a row in the table above");
        selectedInfoLabel.setForeground(FG_MUTED);
        selectedInfoLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusCombo.setEnabled(false);
        statusCombo.setSelectedIndex(0);
        updateBtn.setEnabled(false);
        updateResultLabel.setText(" ");
    }

    private void logout() {
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to log out?",
                "Confirm Logout",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    // ── Helper text ───────────────────────────────────────────────────
    private static String statusBadgeText(String s) {
        return switch (s) {
            case AppConstants.STATUS_PENDING -> "🟡 Pending";
            case AppConstants.STATUS_IN_PROGRESS -> "🔵 In Progress";
            case AppConstants.STATUS_RESOLVED -> "🟢 Resolved";
            default -> s;
        };
    }

    // ── Component factories ───────────────────────────────────────────
    private static void styleHeaderCombo(JComboBox<String> box) {
        box.setBackground(new Color(28, 42, 88));
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 12));
        box.setPreferredSize(new Dimension(140, 30));
        box.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, new Color(50, 70, 130)),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(40, 65, 140) : new Color(20, 32, 70));
                setForeground(FG_PRI);
                setBorder(new EmptyBorder(5, 10, 5, 10));
                setFont(new Font("SansSerif", Font.PLAIN, 12));
                return this;
            }
        });
    }

    private static void styleFormCombo(JComboBox<String> box) {
        box.setBackground(new Color(10, 16, 44));
        box.setForeground(FG_PRI);
        box.setFont(new Font("SansSerif", Font.PLAIN, 13));
        box.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 2, 0, new Color(45, 60, 110)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val,
                    int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, val, idx, sel, foc);
                setBackground(sel ? new Color(30, 55, 120) : new Color(10, 16, 44));
                setForeground(FG_PRI);
                setBorder(new EmptyBorder(6, 10, 6, 10));
                setFont(new Font("SansSerif", Font.PLAIN, 13));
                return this;
            }
        });
    }

    private JButton buildHeaderButton(String label, Color base, Color hover) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isPressed() ? base.darker()
                        : getModel().isRollover() ? hover
                                : base;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 30));
        return btn;
    }

    private JButton buildUpdateButton(String label) {
        JButton btn = new JButton(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = !isEnabled() ? new Color(38, 70, 20)
                        : getModel().isPressed() ? new Color(55, 140, 28)
                                : getModel().isRollover() ? new Color(70, 180, 35)
                                        : new Color(55, 155, 25);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(isEnabled() ? Color.WHITE : new Color(130, 200, 100));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(148, 36));
        return btn;
    }
}

// ============================================================================
// SelfTest -- headless tour of every layer, used by `java CivicEye --selftest`
// It writes one throw-away citizen + issue, exercises the whole workflow and
// deletes the test account again (issues cascade on delete).
// ============================================================================
final class SelfTest {

    private int passed = 0;
    private int failed = 0;

    int run() {
        System.out.println();
        System.out.println("CivicEye self-test  \u2014  " + AppConstants.APP_NAME
                + " " + AppConstants.APP_VERSION);
        System.out.println("==========================================================");

        hashing();
        validation();
        scenario();

        System.out.println("==========================================================");
        System.out.println(passed + " passed, " + failed + " failed");
        return failed == 0 ? 0 : 1;
    }

    // ------------------------------------------------------------------
    private void hashing() {
        section("password hashing");
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("Citizen@123", salt);

        check("salt is 32 hex chars", salt.length() == 32);
        check("hash is 64 hex chars", hash.length() == 64);
        check("correct password verifies",
                PasswordUtils.verifyPassword("Citizen@123", hash, salt));
        check("wrong password is rejected",
                !PasswordUtils.verifyPassword("citizen@123", hash, salt));
        check("a new salt is different",
                !salt.equals(PasswordUtils.generateSalt()));
    }

    // ------------------------------------------------------------------
    private void validation() {
        section("validation rules");
        ValidationService v = new ValidationService();

        check("valid login form",
                v.validateLoginForm("citizen@civiceye.com", "Citizen@123") == null);
        check("empty password rejected",
                v.validateLoginForm("citizen@civiceye.com", "") != null);
        check("malformed email rejected",
                v.validateLoginForm("citizen", "Citizen@123") != null);

        check("valid issue form",
                v.validateIssueForm("Roads", "MG Road",
                        "Pothole near the traffic signal") == null);
        check("unknown category rejected",
                v.validateIssueForm("Aliens", "MG Road",
                        "Pothole near the traffic signal") != null);
        check("short description rejected",
                v.validateIssueForm("Roads", "MG Road", "short") != null);

        check("Pending -> In Progress allowed",
                v.validateStatusTransition(
                        AppConstants.STATUS_PENDING, AppConstants.STATUS_IN_PROGRESS));
        check("In Progress -> Resolved allowed",
                v.validateStatusTransition(
                        AppConstants.STATUS_IN_PROGRESS, AppConstants.STATUS_RESOLVED));
        check("Resolved -> In Progress rejected",
                !v.validateStatusTransition(
                        AppConstants.STATUS_RESOLVED, AppConstants.STATUS_IN_PROGRESS));
        check("same status rejected",
                !v.validateStatusTransition(
                        AppConstants.STATUS_PENDING, AppConstants.STATUS_PENDING));
    }

    // ------------------------------------------------------------------
    private void scenario() {
        section("database");
        boolean connected;
        try {
            connected = DBConnection.testConnection();
        } catch (Throwable t) {
            connected = false;
        }
        if (!check("MySQL reachable at " + DBConnection.getDbUrl(), connected)) {
            System.out.println("   (start MySQL and the schema, then re-run)");
            return;
        }

        section("register -> login -> report -> resolve");
        AuthService auth = new AuthService();
        IssueService svc = new IssueService();
        IssueDAO dao = new IssueDAO();

        String stamp = Long.toString(System.currentTimeMillis());
        String email = "selftest." + stamp + "@civiceye.com";
        String password = "Selftest@123";
        String name = "Self Test " + stamp;

        String regError = auth.registerCitizen(name, email, password, password);
        if (!check("citizen registered (" + email + ")", regError == null)) {
            System.out.println("   " + regError);
            cleanup(email);
            return;
        }

        User citizen = auth.login(email, password);
        if (!check("login returns a Citizen", citizen instanceof Citizen)) {
            cleanup(email);
            return;
        }
        check("role is Citizen", AppConstants.ROLE_CITIZEN.equals(citizen.getRole()));
        check("wrong password does not log in",
                auth.login(email, "not-the-password") == null);

        Issue created = svc.submitIssue(citizen.getUserId(), "Roads",
                "Self-test location", "Automated self-test issue for CivicEye");
        if (!check("issue created with status Pending",
                created != null && AppConstants.STATUS_PENDING.equals(created.getStatus()))) {
            cleanup(email);
            return;
        }

        boolean listed = false;
        for (Issue i : svc.getIssuesByReporter(citizen.getUserId())) {
            if (i.getIssueId() == created.getIssueId())
                listed = true;
        }
        check("issue appears in getIssuesByReporter()", listed);
        check("all issues include the new row",
                svc.getAllIssues().size() > 0);

        check("citizen cannot change status",
                svc.updateStatus(created.getIssueId(),
                        AppConstants.STATUS_IN_PROGRESS, citizen) != null);

        User admin = auth.login("admin@civiceye.com", "Admin@123");
        if (admin == null) {
            System.out.println("   (seeded admin not found -- using a local Admin object)");
            admin = new Admin(0, "Self Test Admin", "admin@civiceye.com");
        }

        check("Pending -> In Progress persisted",
                svc.updateStatus(created.getIssueId(),
                        AppConstants.STATUS_IN_PROGRESS, admin) == null);
        check("In Progress -> Resolved persisted",
                svc.updateStatus(created.getIssueId(),
                        AppConstants.STATUS_RESOLVED, admin) == null);
        check("Resolved -> In Progress rejected",
                svc.updateStatus(created.getIssueId(),
                        AppConstants.STATUS_IN_PROGRESS, admin) != null);

        Issue reread = dao.getIssueById(created.getIssueId());
        check("status read back from the DB is Resolved",
                reread != null && AppConstants.STATUS_RESOLVED.equals(reread.getStatus()));

        cleanup(email);
    }

    // ------------------------------------------------------------------
    private void cleanup(String email) {
        section("cleanup");
        try (Connection c = DBConnection.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM users WHERE email = ?")) {
            ps.setString(1, email);
            int n = ps.executeUpdate();
            System.out.println("   removed " + n
                    + " self-test user row(s); its issues cascade");
        } catch (SQLException e) {
            System.out.println("   cleanup failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    private void section(String title) {
        System.out.println();
        System.out.println("-- " + title);
    }

    private boolean check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("   PASS  " + what);
        } else {
            failed++;
            System.out.println("   FAIL  " + what);
        }
        return ok;
    }
}

// ============================================================================
// main / CivicEye
// ============================================================================

public class CivicEye {

    public static void main(String[] args) {

        // console mode: java CivicEye --selftest
        if (args.length > 0 && ("--selftest".equals(args[0])
                || "-t".equals(args[0]))) {
            System.exit(new SelfTest().run());
        }

        // Start the GUI on Swing's event-dispatch thread
        SwingUtilities.invokeLater(() -> {

            LoginFrame login = new LoginFrame();
            login.setVisible(true);

        });
    }
}
