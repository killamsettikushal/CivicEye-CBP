
package civiceye.dao;

import civiceye.database.DBConnection;
import civiceye.model.Issue;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Data-access object for the {@code issues} table.
 *
 * <p>Every method manages its own JDBC connection via try-with-resources.
 * SQL injection is prevented throughout by the exclusive use of
 * {@link PreparedStatement}.
 *
 * <p>On database error the method logs to stderr and returns a safe sentinel
 * ({@code false}, {@code -1}, {@code null}, or an empty list).
 */
public class IssueDAO {

    // -----------------------------------------------------------------
    // SQL statements
    // -----------------------------------------------------------------

    private static final String SQL_INSERT_ISSUE =
        "INSERT INTO issues (reporter_id, category, location, description, status) " +
        "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_GET_BY_REPORTER =
        "SELECT issue_id, reporter_id, category, location, description, status, created_at " +
        "FROM issues WHERE reporter_id = ? ORDER BY created_at DESC";

    private static final String SQL_GET_ALL =
        "SELECT issue_id, reporter_id, category, location, description, status, created_at " +
        "FROM issues ORDER BY created_at DESC";

    private static final String SQL_UPDATE_STATUS =
        "UPDATE issues SET status = ? WHERE issue_id = ?";

    private static final String SQL_GET_BY_ID =
        "SELECT issue_id, reporter_id, category, location, description, status, created_at " +
        "FROM issues WHERE issue_id = ? LIMIT 1";

    private static final String SQL_DELETE_ISSUE =
        "DELETE FROM issues WHERE issue_id = ?";

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

            ps.setInt   (1, issue.getReporterId());
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
     * @param newStatus one of {@code "Pending"}, {@code "In Progress"}, {@code "Resolved"}
     * @return {@code true} if exactly one row was updated; {@code false} otherwise
     */
    public boolean updateIssueStatus(int issueId, String newStatus) {
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus);
            ps.setInt   (2, issueId);
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
     * <p><b>Intended for automated test cleanup only.</b>  Production
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
        int           issueId     = rs.getInt("issue_id");
        int           reporterId  = rs.getInt("reporter_id");
        String        category    = rs.getString("category");
        String        location    = rs.getString("location");
        String        description = rs.getString("description");
        String        status      = rs.getString("status");
        Timestamp     ts          = rs.getTimestamp("created_at");
        LocalDateTime createdAt   = (ts != null) ? ts.toLocalDateTime() : LocalDateTime.now();

        return new Issue(issueId, reporterId, category, location, description, status, createdAt);
    }
}
