
package civiceye.model;

/**
 * Represents a registered Citizen who can submit and track civic issues.
 *
 * <p>Inherits common identity fields (userId, name, email) from {@link User}.
 */
public class Citizen extends User {

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
