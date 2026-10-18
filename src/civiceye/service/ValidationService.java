
package civiceye.service;

import civiceye.util.AppConstants;

import java.util.regex.Pattern;

/**
 * Stateless validation helper used by every other service and by UI components.
 *
 * <p>All methods are pure functions — they depend only on their arguments and
 * produce no side effects.  No database access occurs here.
 */
public class ValidationService {

    // Pre-compiled pattern for RFC-5321-like email validation
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

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
        if (email == null || email.isBlank()) return false;
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
        if (emailError != null) return emailError;

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
        if (status == null) return false;
        for (String s : AppConstants.ALL_STATUSES) {
            if (s.equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if transitioning from {@code currentStatus} to
     * {@code newStatus} is a permitted workflow step.
     *
     * <p>Allowed transitions:
     * <ul>
     *   <li>Pending → In Progress</li>
     *   <li>Pending → Resolved</li>
     *   <li>In Progress → Resolved</li>
     * </ul>
     * Moving backwards (e.g. Resolved → Pending) is not permitted.
     *
     * @param currentStatus the issue's current status
     * @param newStatus     the desired new status
     * @return {@code true} if the transition is valid
     */
    public boolean validateStatusTransition(String currentStatus, String newStatus) {
        if (!validateStatus(currentStatus) || !validateStatus(newStatus)) return false;
        if (currentStatus.equalsIgnoreCase(newStatus)) return false; // no-op

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
