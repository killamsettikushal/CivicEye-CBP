
package civiceye.test;

import civiceye.database.DBConnection;
import civiceye.model.Admin;
import civiceye.model.Citizen;
import civiceye.model.Issue;
import civiceye.model.User;
import civiceye.service.AuthService;
import civiceye.service.IssueService;
import civiceye.service.ValidationService;
import civiceye.util.AppConstants;

import java.util.List;

/**
 * Phase 3 integration + unit test runner for CivicEye.
 *
 * Run with:
 *   java -cp "out:lib/*" civiceye.test.Phase3Test
 *
 * Tests are split into two groups:
 *   UNIT  — require no database (always run)
 *   INT   — require MySQL + config.properties (skipped if DB unreachable)
 */
public class Phase3Test {

    // -----------------------------------------------------------------------
    // Counters
    // -----------------------------------------------------------------------
    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("=================================================================");
        System.out.println("  CivicEye  –  Phase 3 Test Suite");
        System.out.println("=================================================================\n");

        // --- UNIT tests (no DB required) ---
        System.out.println("── UNIT TESTS (no database required) ───────────────────────────");
        runValidationServiceTests();

        // --- Integration tests (DB required) ---
        System.out.println("\n── INTEGRATION TESTS (requires MySQL + config.properties) ──────");
        boolean dbAvailable = DBConnection.testConnection();
        if (!dbAvailable) {
            System.out.println("[SKIP] Database unreachable — all integration tests skipped.");
            System.out.println("       Set up config.properties and run the SQL scripts, then retry.");
            skipped += 6;
        } else {
            runAuthServiceTests();
            runIssueServiceTests();
        }

        // --- Summary ---
        System.out.println("\n=================================================================");
        System.out.printf("  Results:  %d passed  |  %d failed  |  %d skipped%n",
                passed, failed, skipped);
        System.out.println("=================================================================");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ===================================================================
    // UNIT tests — ValidationService
    // ===================================================================
    private static void runValidationServiceTests() {
        ValidationService vs = new ValidationService();

        // --- Email ---
        assertThat("valid email: user@example.com",
                vs.validateEmail("user@example.com"));
        assertThat("valid email: a@b.in",
                vs.validateEmail("a@b.in"));
        assertFalse("invalid email: no @",
                vs.validateEmail("notanemail"));
        assertFalse("invalid email: null",
                vs.validateEmail(null));
        assertFalse("invalid email: blank",
                vs.validateEmail("   "));
        assertFalse("invalid email: missing TLD",
                vs.validateEmail("user@nodot"));

        // --- Login form ---
        assertNull("login valid inputs → null error",
                vs.validateLoginForm("admin@civiceye.com", "Admin@123"));
        assertNotNull("login empty email → error",
                vs.validateLoginForm("", "password"));
        assertNotNull("login empty password → error",
                vs.validateLoginForm("admin@civiceye.com", ""));

        // --- Registration form ---
        assertNull("registration valid → null",
                vs.validateRegistrationForm("Ravi", "r@r.com", "pass123", "pass123"));
        assertNotNull("registration passwords mismatch → error",
                vs.validateRegistrationForm("Ravi", "r@r.com", "abc", "xyz"));
        assertNotNull("registration short password → error",
                vs.validateRegistrationForm("Ravi", "r@r.com", "ab", "ab"));
        assertNotNull("registration blank name → error",
                vs.validateRegistrationForm("", "r@r.com", "pass123", "pass123"));

        // --- Issue form ---
        assertNull("issue form valid → null",
                vs.validateIssueForm("Roads", "MG Road", "Pothole near signal causing danger to cyclists."));
        assertNotNull("issue form blank category → error",
                vs.validateIssueForm("", "MG Road", "Some description here is long enough."));
        assertNotNull("issue form blank location → error",
                vs.validateIssueForm("Roads", "", "Some description here is long enough."));
        assertNotNull("issue form short description → error",
                vs.validateIssueForm("Roads", "MG Road", "short"));
        assertNotNull("issue form invalid category → error",
                vs.validateIssueForm("Aliens", "MG Road", "Some description here is long enough."));

        // --- Status validation ---
        assertThat("Pending is valid",      vs.validateStatus("Pending"));
        assertThat("In Progress is valid",  vs.validateStatus("In Progress"));
        assertThat("Resolved is valid",     vs.validateStatus("Resolved"));
        assertFalse("Closed is not valid",  vs.validateStatus("Closed"));
        assertFalse("null is not valid",    vs.validateStatus(null));

        // --- Status transitions ---
        assertThat("Pending → In Progress allowed",
                vs.validateStatusTransition("Pending", "In Progress"));
        assertThat("Pending → Resolved allowed",
                vs.validateStatusTransition("Pending", "Resolved"));
        assertThat("In Progress → Resolved allowed",
                vs.validateStatusTransition("In Progress", "Resolved"));
        assertFalse("In Progress → Pending NOT allowed",
                vs.validateStatusTransition("In Progress", "Pending"));
        assertFalse("Resolved → Pending NOT allowed",
                vs.validateStatusTransition("Resolved", "Pending"));
        assertFalse("Resolved → In Progress NOT allowed",
                vs.validateStatusTransition("Resolved", "In Progress"));
        assertFalse("same status NOT allowed",
                vs.validateStatusTransition("Pending", "Pending"));
    }

    // ===================================================================
    // INTEGRATION tests — AuthService
    // ===================================================================
    private static void runAuthServiceTests() {
        AuthService auth = new AuthService();

        // Correct admin credentials
        User adminUser = auth.login("admin@civiceye.com", "Admin@123");
        assertThat("admin login with correct credentials", adminUser != null);
        if (adminUser != null) {
            assertThat("logged-in admin role is 'Admin'",
                    AppConstants.ROLE_ADMIN.equals(adminUser.getRole()));
            assertThat("logged-in user is Admin instance",
                    adminUser instanceof Admin);
        }

        // Correct citizen credentials
        User citizenUser = auth.login("citizen@civiceye.com", "Citizen@123");
        assertThat("citizen login with correct credentials", citizenUser != null);
        if (citizenUser != null) {
            assertThat("logged-in citizen role is 'Citizen'",
                    AppConstants.ROLE_CITIZEN.equals(citizenUser.getRole()));
            assertThat("logged-in user is Citizen instance",
                    citizenUser instanceof Citizen);

        }

        // Wrong password
        User wrongPw = auth.login("admin@civiceye.com", "wrongpassword");
        assertFalse("wrong password returns null", wrongPw != null);

        // Non-existent email
        User noUser = auth.login("nobody@civiceye.com", "password");
        assertFalse("unknown email returns null", noUser != null);

        // Empty credentials
        User emptyCredentials = auth.login("", "");
        assertFalse("empty credentials return null", emptyCredentials != null);
    }

    // ===================================================================
    // INTEGRATION tests — IssueService
    // ===================================================================
    private static void runIssueServiceTests() {
        AuthService  auth  = new AuthService();
        IssueService svc   = new IssueService();

        // Log in as citizen to get a valid reporterId
        User citizen = auth.login("citizen@civiceye.com", "Citizen@123");
        if (citizen == null) {
            System.out.println("[SKIP] Could not authenticate demo citizen — skipping IssueService tests.");
            skipped += 4;
            return;
        }
        int reporterId = citizen.getUserId();

        // Admin login for status-update tests
        User admin = auth.login("admin@civiceye.com", "Admin@123");

        // --- getIssuesByReporter ---
        List<Issue> myIssues = svc.getIssuesByReporter(reporterId);
        assertThat("getIssuesByReporter returns non-null list", myIssues != null);

        // --- getAllIssues ---
        List<Issue> allIssues = svc.getAllIssues();
        assertThat("getAllIssues returns non-null list", allIssues != null);
        assertThat("getAllIssues has at least 3 sample rows (from SQL seed)",
                allIssues.size() >= 3);

        // --- submitIssue ---
        int beforeCount = myIssues.size();
        Issue newIssue = svc.submitIssue(
                reporterId, "Roads", "Test Location Avenue",
                "Integration test issue — auto-submitted by Phase3Test runner.");
        assertThat("submitIssue returns non-null Issue", newIssue != null);
        if (newIssue != null) {
            assertThat("submitted issue has a generated ID > 0", newIssue.getIssueId() > 0);
            assertThat("submitted issue status is Pending",
                    AppConstants.STATUS_PENDING.equals(newIssue.getStatus()));

            List<Issue> afterIssues = svc.getIssuesByReporter(reporterId);
            assertThat("issue count increased by 1 after submitIssue",
                    afterIssues.size() == beforeCount + 1);

            // --- updateStatus (admin authorized) ---
            if (admin != null) {
                String updateError = svc.updateStatus(
                        newIssue.getIssueId(), AppConstants.STATUS_IN_PROGRESS, admin);
                assertThat("admin can update Pending → In Progress (no error returned)",
                        updateError == null);

                // --- updateStatus (citizen unauthorized) ---
                String unauthorizedError = svc.updateStatus(
                        newIssue.getIssueId(), AppConstants.STATUS_RESOLVED, citizen);
                assertNotNull("citizen cannot update status — error message returned",
                        unauthorizedError);
            }
        }

        // --- submitIssue validation gate ---
        Issue badIssue = svc.submitIssue(reporterId, "", "Loc", "Too short.");
        assertFalse("submitIssue rejects invalid form (blank category)", badIssue != null);
    }

    // ===================================================================
    // Mini assertion helpers
    // ===================================================================
    private static void assertThat(String label, boolean condition) {
        if (condition) {
            System.out.println("[PASS] " + label);
            passed++;
        } else {
            System.out.println("[FAIL] " + label);
            failed++;
        }
    }

    private static void assertFalse(String label, boolean condition) {
        assertThat(label, !condition);
    }

    private static void assertNull(String label, Object value) {
        assertThat(label, value == null);
    }

    private static void assertNotNull(String label, Object value) {
        assertThat(label, value != null);
    }
}
