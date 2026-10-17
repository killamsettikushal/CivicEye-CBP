
package civiceye.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Password hashing and verification utilities for CivicEye.
 *
 * <h3>Algorithm</h3>
 * <pre>
 *   storedHash = SHA-256( password + salt )   [UTF-8, lowercase hex output]
 *   salt       = 16 cryptographically random bytes encoded as 32 lowercase hex chars
 * </pre>
 *
 * <p>The salt is generated once per user and stored in the {@code users.salt} column
 * alongside the hash.  Because SHA-256 is used with a per-user salt (not HMAC-based
 * or adaptive), this is appropriate for a college demonstration project.
 * Production systems should prefer BCrypt or Argon2.
 *
 * <p>This class has no external dependencies beyond {@code java.security}.
 */
public final class PasswordUtils {

    // Prevent instantiation
    private PasswordUtils() {}

    // Single SecureRandom instance — thread-safe after construction
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // -----------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------

    /**
     * Generates a new random salt.
     *
     * @return a lowercase hex string of length {@code SALT_BYTE_LENGTH * 2}
     *         (default 32 characters for 16 bytes)
     */
    public static String generateSalt() {
        byte[] saltBytes = new byte[AppConstants.SALT_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(saltBytes);
        return bytesToHex(saltBytes);
    }

    /**
     * Computes SHA-256( password + salt ) and returns the result as
     * a 64-character lowercase hex string.
     *
     * @param password plaintext password (not null)
     * @param salt     hex salt string returned by {@link #generateSalt()} (not null)
     * @return 64-char lowercase hex hash
     * @throws RuntimeException if SHA-256 is unavailable (should never happen in Java SE)
     */
    public static String hashPassword(String password, String salt) {
        if (password == null) throw new IllegalArgumentException("password must not be null");
        if (salt     == null) throw new IllegalArgumentException("salt must not be null");

        try {
            MessageDigest digest  = MessageDigest.getInstance(AppConstants.HASH_ALGORITHM);
            String        combined = password + salt;
            byte[]        hashBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE specification — this cannot happen
            throw new RuntimeException("SHA-256 is unavailable on this JVM", e);
        }
    }

    /**
     * Verifies a plaintext password against a stored hash and its salt.
     *
     * <p>The comparison is case-insensitive to tolerate any case differences
     * between hex strings produced by different code paths.
     *
     * @param plaintext  the password entered by the user
     * @param storedHash the hash value retrieved from the database
     * @param salt       the salt value retrieved from the database
     * @return {@code true} if the password matches; {@code false} otherwise
     */
    public static boolean verifyPassword(String plaintext, String storedHash, String salt) {
        if (plaintext  == null || plaintext.isEmpty())  return false;
        if (storedHash == null || storedHash.isEmpty()) return false;
        if (salt       == null || salt.isEmpty())       return false;

        String computed = hashPassword(plaintext, salt);
        return computed.equalsIgnoreCase(storedHash);
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes source bytes
     * @return lowercase hex representation (length = bytes.length * 2)
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
