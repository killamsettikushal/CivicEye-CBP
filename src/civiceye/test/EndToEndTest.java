
package civiceye.test;

import civiceye.dao.IssueDAO;
import civiceye.database.DBConnection;
import civiceye.model.Admin;
import civiceye.model.Citizen;
import civiceye.model.Issue;
import civiceye.model.User;
import civiceye.service.AuthService;
import civiceye.service.IssueService;
import civiceye.util.AppConstants;

import java.util.List;

/**
 * Phase 7 — End-to-End Integration Test.
 *
 * <p>Exercises the full user journey in one automated run:
 * <ol>
 *   <li>Citizen and admin login</li>
 *   <li>Citizen submits a new issue</li>
 *   <li>Issue visibility — citizen's own list + admin all-issues view</li>
 *   <li>Status-update workflow with all authorisation and transition rules</li>
 *   <li>Persistence — re-query confirms each state change was written to MySQL</li>
 *   <li>Citizen re-queries and sees the admin-updated status</li>
 *   <li>Cleanup — test issue removed from DB</li>
 * </ol>
 *
 * <p>Run command:
 * <pre>
 *   java -cp "out:lib/*:." civiceye.test.EndToEndTest
 * </pre>
 *
 * <p>The test is self-contained and idempotent: it creates exactly one issue,
 * then deletes it in the cleanup step, leaving the database unchanged.
 */
public class EndToEndTest {

    // ── Counters ─────────────────────────────────────────────────────
    private static int passed   = 0;
    private static int failed   = 0;
    private static int skipped  = 0;

    // Artifacts to clean up
    private static int testIssueId = -1;  // set after submission

    // ─────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        banner("CivicEye \u2014 Phase 7 End-to-End Integration Test Suite");
        System.out.println("  Full lifecycle: Login \u2192 Submit \u2192 Verify \u2192"
                         + " Admin Update \u2192 Persist \u2192 Cleanup\n");

        // ── Pre-check: Database ─────────────────────────────────────
        if (!DBConnection.testConnection()) {
            System.out.println("[SKIP] Database unreachable \u2014 all E2E tests skipped.");
            System.out.println("       Ensure config.properties exists and MySQL is running.");
            skipped = 22;
            printSummary();
            System.exit(0);
        }

        // ── Services ────────────────────────────────────────────────
        AuthService  authService  = new AuthService();
        IssueService issueService = new IssueService();
        IssueDAO     issueDAO     = new IssueDAO();

        // ─────────────────────────────────────────────────────────────
        section("STEP 1: Authentication");
        // ─────────────────────────────────────────────────────────────

        User citizenUser = authService.login("citizen@civiceye.com", "Citizen@123");
        assertTrue("Citizen login succeeds",                    citizenUser != null);
        assertTrue("Citizen is instanceof Citizen",             citizenUser instanceof Citizen);

        User adminUser = authService.login("admin@civiceye.com", "Admin@123");
        assertTrue("Admin login succeeds",                      adminUser != null);
        assertTrue("Admin is instanceof Admin",                 adminUser instanceof Admin);

        // Wrong-credential guard
        User badLogin = authService.login("admin@civiceye.com", "wrongpassword");
        assertFalse("Wrong password returns null",              badLogin != null);

        // Bail early if login itself is broken — rest of test has no meaning
        if (citizenUser == null || adminUser == null) {
            System.out.println("\n[ABORT] Authentication failed — cannot continue E2E test.");
            printSummary();
            System.exit(1);
        }

        Citizen citizen = (Citizen) citizenUser;
        Admin   admin   = (Admin)   adminUser;
        System.out.printf("         Citizen '%s' (id=%d)%n",
                citizen.getName(), citizen.getUserId());

        // ─────────────────────────────────────────────────────────────
        section("STEP 2: Issue Submission");
        // ─────────────────────────────────────────────────────────────

        String testCategory = "Roads";
        String testLocation = "[E2E-TEST] Automated Test Location \u2014 Phase 7";
        String testDesc     =
            "[E2E-TEST] Automated test issue submitted by EndToEndTest.java. "
          + "This record is removed automatically in the cleanup step. "
          + "If you see this in the database, the cleanup step may have failed.";

        Issue submitted = issueService.submitIssue(
                citizen.getUserId(), testCategory, testLocation, testDesc);

        assertTrue("submitIssue returns a non-null Issue",        submitted != null);
        assertTrue("Submitted issue has DB-generated ID > 0",
                submitted != null && submitted.getIssueId() > 0);
        assertTrue("Submitted issue status defaults to Pending",
                submitted != null
                && AppConstants.STATUS_PENDING.equals(submitted.getStatus()));
        assertTrue("Submitted issue category matches input",
                submitted != null && testCategory.equals(submitted.getCategory()));
        assertTrue("Submitted issue location matches input",
                submitted != null && testLocation.equals(submitted.getLocation()));

        if (submitted != null) {
            testIssueId = submitted.getIssueId();
            System.out.println("         \u2192 Created Issue #" + testIssueId);
        }

        // Validate rejection of a bad form
        Issue badSubmit = issueService.submitIssue(citizen.getUserId(), "", "loc", "too short");
        assertFalse("submitIssue rejects blank category",        badSubmit != null);

        // ─────────────────────────────────────────────────────────────
        section("STEP 3: Issue Visibility");
        // ─────────────────────────────────────────────────────────────

        // Citizen sees their own issue
        List<Issue> citizenList = issueService.getIssuesByReporter(citizen.getUserId());
        assertTrue("getIssuesByReporter returns non-empty list",  !citizenList.isEmpty());
        boolean inCitizenList = citizenList.stream()
                .anyMatch(i -> i.getIssueId() == testIssueId);
        assertTrue("New issue appears in citizen's issue list",   inCitizenList);

        // Admin sees all issues including the new one
        List<Issue> allIssues = issueService.getAllIssues();
        assertTrue("getAllIssues returns non-empty list",          !allIssues.isEmpty());
        boolean inAdminList = allIssues.stream()
                .anyMatch(i -> i.getIssueId() == testIssueId);
        assertTrue("New issue appears in admin all-issues view",  inAdminList);

        System.out.printf("         Citizen has %d issue(s)  |  Total in DB: %d%n",
                citizenList.size(), allIssues.size());

        // ─────────────────────────────────────────────────────────────
        section("STEP 4: Status-Update Workflow (Authorisation + Transitions)");
        // ─────────────────────────────────────────────────────────────

        if (testIssueId < 1) {
            System.out.println("[SKIP] No test issue available \u2014 skipping status tests.");
            skipped += 8;
        } else {
            // 5a. Citizen tries to update status \u2014 must be rejected
            String citizenAttempt = issueService.updateStatus(
                    testIssueId, AppConstants.STATUS_IN_PROGRESS, citizen);
            assertNotNull("Citizen blocked from updating status (auth rule)", citizenAttempt);

            // 5b. Admin: Pending \u2192 In Progress
            String err1 = issueService.updateStatus(
                    testIssueId, AppConstants.STATUS_IN_PROGRESS, admin);
            assertNull("Admin: Pending \u2192 In Progress succeeds (no error)",    err1);

            // 5c. Verify the change persisted in MySQL
            Issue after1 = issueDAO.getIssueById(testIssueId);
            assertTrue("MySQL confirms status = 'In Progress' after step 5b",
                    after1 != null
                    && AppConstants.STATUS_IN_PROGRESS.equals(after1.getStatus()));

            // 5d. Admin: In Progress \u2192 Resolved
            String err2 = issueService.updateStatus(
                    testIssueId, AppConstants.STATUS_RESOLVED, admin);
            assertNull("Admin: In Progress \u2192 Resolved succeeds (no error)",   err2);

            // 5e. Verify persistence again
            Issue after2 = issueDAO.getIssueById(testIssueId);
            assertTrue("MySQL confirms status = 'Resolved' after step 5d",
                    after2 != null
                    && AppConstants.STATUS_RESOLVED.equals(after2.getStatus()));

            // 5f. Attempt illegal backward transition: Resolved \u2192 Pending
            String err3 = issueService.updateStatus(
                    testIssueId, AppConstants.STATUS_PENDING, admin);
            assertNotNull("Resolved \u2192 Pending blocked (transition rule)",     err3);

            // 5g. Verify status did NOT change despite the illegal attempt
            Issue after3 = issueDAO.getIssueById(testIssueId);
            assertTrue("MySQL still shows 'Resolved' after failed backward transition",
                    after3 != null
                    && AppConstants.STATUS_RESOLVED.equals(after3.getStatus()));

            // 5h. Attempt same-status update: Resolved \u2192 Resolved
            String err4 = issueService.updateStatus(
                    testIssueId, AppConstants.STATUS_RESOLVED, admin);
            assertNotNull("Same-status update blocked (no-op rule)",               err4);
        }

        // ─────────────────────────────────────────────────────────────
        section("STEP 5: Citizen Re-Queries and Sees Updated Status");
        // ─────────────────────────────────────────────────────────────

        if (testIssueId > 0) {
            List<Issue> refreshed = issueService.getIssuesByReporter(citizen.getUserId());
            Issue resolvedIssue = refreshed.stream()
                    .filter(i -> i.getIssueId() == testIssueId)
                    .findFirst().orElse(null);
            assertTrue("Refreshed citizen list contains the test issue", resolvedIssue != null);
            assertTrue("Citizen sees final status = 'Resolved' after admin update",
                    resolvedIssue != null
                    && AppConstants.STATUS_RESOLVED.equals(resolvedIssue.getStatus()));
        }

        // ─────────────────────────────────────────────────────────────
        section("STEP 6: Cleanup (DB left unchanged by test)");
        // ─────────────────────────────────────────────────────────────

        if (testIssueId > 0) {
            boolean deleted = issueDAO.deleteIssue(testIssueId);
            assertTrue("Test issue #" + testIssueId + " deleted from DB",  deleted);
            System.out.println("         Issue #" + testIssueId + " removed.");

            // Verify the delete
            Issue shouldBeNull = issueDAO.getIssueById(testIssueId);
            assertFalse("getIssueById returns null after delete",  shouldBeNull != null);
        }

        // ─────────────────────────────────────────────────────────────
        printSummary();
        System.exit(failed == 0 ? 0 : 1);
    }

    // ── Assertion helpers ─────────────────────────────────────────────

    private static void assertTrue(String label, boolean cond) {
        if (cond) { System.out.println("[PASS] " + label); passed++; }
        else      { System.out.println("[FAIL] " + label); failed++; }
    }
    private static void assertFalse(String label, boolean cond) { assertTrue(label, !cond); }
    private static void assertNull(String label, Object v)      { assertTrue(label, v == null); }
    private static void assertNotNull(String label, Object v)   { assertTrue(label, v != null); }

    // ── Formatting helpers ────────────────────────────────────────────

    private static void banner(String title) {
        System.out.println("=================================================================");
        System.out.println("  " + title);
        System.out.println("=================================================================");
    }
    private static void section(String title) {
        System.out.println("\n\u2500\u2500 " + title
                         + " " + "\u2500".repeat(Math.max(0, 62 - title.length())));
    }
    private static void printSummary() {
        System.out.println("\n=================================================================");
        System.out.printf("  Results:  %d passed  |  %d failed  |  %d skipped%n",
                passed, failed, skipped);
        System.out.println("=================================================================");
    }
}
