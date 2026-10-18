
package civiceye.service;

import civiceye.dao.UserDAO;
import civiceye.dao.UserRecord;
import civiceye.model.Admin;
import civiceye.model.Citizen;
import civiceye.model.User;
import civiceye.util.AppConstants;
import civiceye.util.PasswordUtils;

/**
 * Authentication service for CivicEye.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate login-form inputs before touching the database.</li>
 *   <li>Retrieve the matching {@link UserRecord} from {@link UserDAO}.</li>
 *   <li>Verify the plaintext password against the stored hash using
 *       {@link PasswordUtils#verifyPassword}.</li>
 *   <li>Construct and return the correct {@link User} subclass
 *       ({@link Citizen} or {@link Admin}) — credentials are never
 *       passed beyond this class.</li>
 *   <li>Register new citizens.</li>
 * </ul>
 *
 * <p>This class never executes SQL directly; all database work is delegated to
 * {@link UserDAO}.
 */
public class AuthService {

    private final UserDAO          userDAO;
    private final ValidationService validation;

    /** Creates an AuthService with default collaborators. */
    public AuthService() {
        this.userDAO    = new UserDAO();
        this.validation = new ValidationService();
    }

    // -----------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------

    /**
     * Authenticates a user with the supplied credentials.
     *
     * <p>Returns {@code null} on any failure (wrong email, wrong password,
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
     * <p>Citizens are the only role that can self-register.  Admin accounts
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
        if (formError != null) return formError;

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
            return new Citizen(record.userId, record.name, record.email, record.rewardPoints);
        }
    }
}
