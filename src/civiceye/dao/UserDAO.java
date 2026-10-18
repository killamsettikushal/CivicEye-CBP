
package civiceye.dao;

import civiceye.database.DBConnection;

import java.sql.*;

/**
 * Data-access object for the {@code users} table.
 *
 * <p>All public methods open their own connection via {@link DBConnection#getConnection()}
 * and close it immediately using try-with-resources.  No connection is held
 * between calls.
 *
 * <p>SQL is always issued through {@link PreparedStatement} — never by
 * string concatenation — to prevent SQL injection.
 *
 * <p>On database errors the method logs to stderr and returns a safe sentinel
 * value ({@code null} or {@code -1} or {@code false}) so callers can detect
 * failure without catching checked exceptions.
 */
public class UserDAO {

    // -----------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------

    private static final String SQL_FIND_BY_EMAIL =
        "SELECT user_id, name, email, password_hash, salt, role " +
        "FROM users WHERE email = ? LIMIT 1";

    private static final String SQL_INSERT_USER =
        "INSERT INTO users (name, email, password_hash, salt, role) " +
        "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_EMAIL_EXISTS =
        "SELECT COUNT(*) FROM users WHERE email = ?";


    // -----------------------------------------------------------------
    // Public methods
    // -----------------------------------------------------------------

    /**
     * Looks up a user by their email address.
     *
     * @param email the email to search for (case-insensitive in most MySQL collations)
     * @return a {@link UserRecord} containing all columns including credentials,
     *         or {@code null} if no matching row exists or a DB error occurs
     */
    public UserRecord findByEmail(String email) {
        if (email == null || email.isBlank()) return null;

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
                        rs.getString("role")
                    );
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
     * <p>Passwords must be hashed by the caller (via {@link civiceye.util.PasswordUtils})
     * before invoking this method.  Never pass a plaintext password here.
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
        if (email == null || email.isBlank()) return false;

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
