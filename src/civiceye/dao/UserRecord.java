
package civiceye.dao;

/**
 * Internal data-transfer object representing one row from the {@code users} table.
 *
 * <p>This class is intentionally package-private in scope for its constructor —
 * only {@link UserDAO} creates instances.  {@link civiceye.service.AuthService}
 * uses it to verify credentials, then discards it in favour of a proper
 * {@link civiceye.model.User} subclass (Citizen or Admin) that carries no
 * password information.
 *
 * <p>Fields are {@code public final} for read-only access without getters,
 * keeping the code concise for a data-carrier class.
 */
public final class UserRecord {

    /** Primary key from {@code users.user_id}. */
    public final int    userId;

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
        this.userId       = userId;
        this.name         = name;
        this.email        = email;
        this.passwordHash = passwordHash;
        this.salt         = salt;
        this.role         = role;
    }
}
