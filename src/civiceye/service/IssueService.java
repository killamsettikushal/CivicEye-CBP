
package civiceye.service;

import civiceye.dao.IssueDAO;
import civiceye.model.Admin;
import civiceye.model.Issue;
import civiceye.model.User;
import civiceye.util.AppConstants;

import java.util.Collections;
import java.util.List;

/**
 * Business-logic layer for civic-issue operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate issue-form input before touching the database.</li>
 *   <li>Enforce the rule that only {@link Admin} users may update issue status.</li>
 *   <li>Delegate all persistence to {@link IssueDAO}.</li>
 * </ul>
 *
 * <p>No SQL is executed in this class.
 */
public class IssueService {

    private final IssueDAO         issueDAO;
    private final ValidationService validation;

    /** Creates an IssueService with default collaborators. */
    public IssueService() {
        this.issueDAO   = new IssueDAO();
        this.validation = new ValidationService();
    }

    // -----------------------------------------------------------------
    // Citizen operations
    // -----------------------------------------------------------------

    /**
     * Validates and persists a new civic issue submitted by a citizen.
     *
     * @param reporterId  the {@code user_id} of the submitting citizen
     * @param category    selected category (must be in {@link AppConstants#ISSUE_CATEGORIES})
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
                AppConstants.STATUS_PENDING
        );

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
        if (reporterId <= 0) return Collections.emptyList();
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
     * <p>Authorization rule: only users whose role is {@code "Admin"} may
     * call this method successfully.  The check is performed here in the
     * service layer so it cannot be bypassed by any UI path.
     *
     * <p>Status transition rules are enforced by {@link ValidationService}.
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
