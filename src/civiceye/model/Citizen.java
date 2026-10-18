
package civiceye.model;

/**
 * Represents a registered Citizen who can submit and track civic issues.
 *
 * <p>Inherits common identity fields (userId, name, email) from {@link User}.
 * Adds citizen-specific state: {@code rewardPoints} accumulated by submitting issues.
 */
public class Citizen extends User {

    private int rewardPoints;

    // ------------------------------------------------------------------
    // Constructor for brand-new citizens (registration)
    // ------------------------------------------------------------------

    /**
     * Creates a new Citizen with zero reward points.
     * Used during user registration before the record is persisted.
     *
     * @param userId database-assigned user ID (0 before first insert)
     * @param name   full name
     * @param email  unique email address
     */
    public Citizen(int userId, String name, String email) {
        super(userId, name, email);
        this.rewardPoints = 0;
    }

    // ------------------------------------------------------------------
    // Constructor for citizens loaded from the database
    // ------------------------------------------------------------------

    /**
     * Reconstructs a Citizen loaded from the database, restoring their
     * accumulated reward points exactly as stored.
     *
     * @param userId       database primary key
     * @param name         full name
     * @param email        unique email address
     * @param rewardPoints points already accumulated (from users.reward_points)
     */
    public Citizen(int userId, String name, String email, int rewardPoints) {
        super(userId, name, email);
        this.rewardPoints = Math.max(0, rewardPoints); // guard against negative DB values
    }

    // ------------------------------------------------------------------
    // Reward-points behaviour
    // ------------------------------------------------------------------

    /**
     * Adds points to the citizen's running total.
     * Silently ignores zero or negative values.
     *
     * @param points positive integer to award
     */
    public void addRewardPoints(int points) {
        if (points > 0) {
            rewardPoints += points;
        }
    }

    /** Returns the citizen's current reward-points balance. */
    public int getRewardPoints() {
        return rewardPoints;
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
        return String.format("Citizen{id=%d, name='%s', points=%d}",
                getUserId(), getName(), rewardPoints);
    }
}

