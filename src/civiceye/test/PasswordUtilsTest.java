
package civiceye.test;

import civiceye.util.PasswordUtils;

/**
 * Standalone verification test for PasswordUtils.
 * Run with:
 *   java -cp "out:lib/*" civiceye.test.PasswordUtilsTest
 *
 * This test does NOT require a database connection.
 */
public class PasswordUtilsTest {

    public static void main(String[] args) {
        System.out.println("=== CivicEye Phase 2 — PasswordUtils Verification ===\n");

        int passed = 0;
        int failed = 0;

        // -------------------------------------------------------
        // Test 1 : Admin hash matches insert_sample_data.sql
        // -------------------------------------------------------
        String adminSalt     = "a1b2c3d4e5f60001";
        String adminPassword = "Admin@123";
        String adminExpected = "01b79d242da80052277081ae43731cb97c916a0c1c68120358279dc98a278864";
        String adminActual   = PasswordUtils.hashPassword(adminPassword, adminSalt);

        if (adminActual.equalsIgnoreCase(adminExpected)) {
            System.out.println("[PASS] Admin hash matches SQL sample data");
            passed++;
        } else {
            System.out.println("[FAIL] Admin hash MISMATCH");
            System.out.println("  Expected : " + adminExpected);
            System.out.println("  Actual   : " + adminActual);
            failed++;
        }

        // -------------------------------------------------------
        // Test 2 : Citizen hash matches insert_sample_data.sql
        // -------------------------------------------------------
        String citizenSalt     = "f0e1d2c3b4a50002";
        String citizenPassword = "Citizen@123";
        String citizenExpected = "1960dc3f041e1f50112ae112ace5bca50e628784061c74cc41b91eb053af057c";
        String citizenActual   = PasswordUtils.hashPassword(citizenPassword, citizenSalt);

        if (citizenActual.equalsIgnoreCase(citizenExpected)) {
            System.out.println("[PASS] Citizen hash matches SQL sample data");
            passed++;
        } else {
            System.out.println("[FAIL] Citizen hash MISMATCH");
            System.out.println("  Expected : " + citizenExpected);
            System.out.println("  Actual   : " + citizenActual);
            failed++;
        }

        // -------------------------------------------------------
        // Test 3 : verifyPassword returns true for correct password
        // -------------------------------------------------------
        boolean adminVerify = PasswordUtils.verifyPassword(adminPassword, adminExpected, adminSalt);
        if (adminVerify) {
            System.out.println("[PASS] verifyPassword correct-password → true");
            passed++;
        } else {
            System.out.println("[FAIL] verifyPassword correct-password returned false");
            failed++;
        }

        // -------------------------------------------------------
        // Test 4 : verifyPassword returns false for wrong password
        // -------------------------------------------------------
        boolean wrongVerify = PasswordUtils.verifyPassword("wrongpass", adminExpected, adminSalt);
        if (!wrongVerify) {
            System.out.println("[PASS] verifyPassword wrong-password → false");
            passed++;
        } else {
            System.out.println("[FAIL] verifyPassword wrong-password returned true (SECURITY BUG!)");
            failed++;
        }

        // -------------------------------------------------------
        // Test 5 : verifyPassword returns false for null inputs
        // -------------------------------------------------------
        boolean nullVerify = PasswordUtils.verifyPassword(null, adminExpected, adminSalt);
        if (!nullVerify) {
            System.out.println("[PASS] verifyPassword null-password → false");
            passed++;
        } else {
            System.out.println("[FAIL] verifyPassword null-password returned true");
            failed++;
        }

        // -------------------------------------------------------
        // Test 6 : generateSalt produces 32-char hex string
        // -------------------------------------------------------
        String salt1 = PasswordUtils.generateSalt();
        String salt2 = PasswordUtils.generateSalt();
        boolean saltLengthOk = (salt1.length() == 32 && salt2.length() == 32);
        boolean saltsUnique  = !salt1.equals(salt2);

        if (saltLengthOk) {
            System.out.println("[PASS] generateSalt length = 32 chars");
            passed++;
        } else {
            System.out.println("[FAIL] generateSalt wrong length: " + salt1.length());
            failed++;
        }

        if (saltsUnique) {
            System.out.println("[PASS] generateSalt produces unique values");
            passed++;
        } else {
            System.out.println("[FAIL] generateSalt produced duplicate values — check SecureRandom!");
            failed++;
        }

        // -------------------------------------------------------
        // Test 7 : Round-trip — hash then verify with generated salt
        // -------------------------------------------------------
        String freshSalt  = PasswordUtils.generateSalt();
        String freshPass  = "TestPass@99";
        String freshHash  = PasswordUtils.hashPassword(freshPass, freshSalt);
        boolean roundTrip = PasswordUtils.verifyPassword(freshPass, freshHash, freshSalt);
        if (roundTrip) {
            System.out.println("[PASS] Round-trip hash+verify with fresh salt");
            passed++;
        } else {
            System.out.println("[FAIL] Round-trip hash+verify failed");
            failed++;
        }

        // -------------------------------------------------------
        // Summary
        // -------------------------------------------------------
        System.out.println("\n--- Results: " + passed + " passed, " + failed + " failed ---");
        System.exit(failed == 0 ? 0 : 1);
    }
}
