
package civiceye.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a civic issue submitted by a Citizen.
 *
 * <p>Two constructors are provided:
 * <ul>
 *   <li>{@link #Issue(String, int, String, String, String)} — for creating a
 *       new issue before it is persisted (issueId is 0, createdAt is now).</li>
 *   <li>{@link #Issue(int, int, String, String, String, String, LocalDateTime)} — for
 *       reconstructing an issue that was loaded from the database.</li>
 * </ul>
 */
public class Issue {

    // -----------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------

    private int           issueId;       // 0 before DB insert; assigned by DB
    private int           reporterId;    // FK → users.user_id
    private String        category;
    private String        location;
    private String        description;
    private String        status;
    private LocalDateTime createdAt;

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

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
        this.issueId     = 0;
        this.reporterId  = reporterId;
        this.category    = category;
        this.location    = location;
        this.description = description;
        this.status      = (status != null && !status.isBlank()) ? status : "Pending";
        this.createdAt   = LocalDateTime.now();
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
        this.issueId     = issueId;
        this.reporterId  = reporterId;
        this.category    = category;
        this.location    = location;
        this.description = description;
        this.status      = status;
        this.createdAt   = createdAt;
    }

    // -----------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------

    public int getIssueId()       { return issueId; }
    public int getReporterId()    { return reporterId; }
    public String getCategory()   { return category; }
    public String getLocation()   { return location; }
    public String getDescription(){ return description; }
    public String getStatus()     { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // -----------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------

    /** Called by IssueDAO after a successful INSERT to store the generated ID. */
    public void setIssueId(int issueId) { this.issueId = issueId; }

    /** Updated by AdminDashboard via IssueService after status change. */
    public void setStatus(String status) { this.status = status; }

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
