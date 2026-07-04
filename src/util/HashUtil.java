package util;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/*
 * HashUtil computes SHA-256 hashes of files.
 *
 * A hash is a fixed-length fingerprint of a file's contents. SHA-256 always
 * produces a 64-character hex string regardless of how big the file is.
 * Two files with identical contents will always produce the exact same hash,
 * and two files with different contents will almost certainly produce different
 * hashes (the chance of a collision is astronomically small).
 *
 * This makes hashing the most reliable way to confirm whether two files are
 * truly identical without comparing them byte by byte.
 *
 * There are two hashing methods here:
 *
 *   getQuickHash() — reads only the first 8KB of the file.
 *     Fast, cheap, and good enough for the first filter pass. If two files
 *     differ in the first 8KB, they're definitely not duplicates — no need
 *     to read the rest. If they match, we can't be sure yet, so we move to
 *     the full hash. Great for large files (videos, archives) where reading
 *     the whole file just to rule it out would be expensive.
 *
 *   getSHA256() — reads the entire file.
 *     Definitive confirmation. If two files have the same full SHA-256 hash,
 *     they are confirmed exact duplicates. Only called on files that already
 *     passed both the size filter and the quick hash filter, so by the time
 *     we're here, the candidate pool is already very small.
 *
 * Both methods return null on any failure (file not found, permission error,
 * algorithm not available) so callers can check for null and skip the file
 * rather than crashing the entire scan.
 */
public class HashUtil {

    // How many bytes to read for the quick hash — 8KB is one standard disk sector read,
    // fast to load and enough to differentiate most files that differ near the start.
    private static final int QUICK_HASH_BYTES = 8192;

    /*
     * Computes a SHA-256 hash of the entire file contents.
     *
     * Reads the file in 8KB chunks and feeds each chunk into the MessageDigest
     * as it's read. This means even a 10GB file can be hashed without loading
     * it all into memory at once — only 8KB is in memory at any given time.
     *
     * After all bytes are processed, digest.digest() finalises the hash and
     * returns it as a raw byte array, which toHex() converts to a readable
     * hex string like "a3f1b2c4...".
     *
     * BufferedInputStream wraps the FileInputStream to add an internal read buffer,
     * reducing the number of actual system calls to disk and speeding up the read.
     *
     * Returns null if the file can't be read or the SHA-256 algorithm isn't available
     * (the latter is essentially impossible on any modern JVM, but handled for safety).
     */
    public static String getSHA256(File file){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[8192];
            int bytesRead;
            while((bytesRead = bis.read(buffer)) != -1){
                digest.update(buffer, 0, bytesRead);
            }
            bis.close();
            return toHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            return null; // SHA-256 not available — won't happen on any modern JVM.
        } catch (FileNotFoundException e) {
            return null; // File was deleted or moved between scanning and hashing.
        } catch (IOException e) {
            return null; // Permission error or disk issue mid-read.
        }
    }

    /*
     * Computes a SHA-256 hash of just the first 8KB of the file.
     *
     * Same approach as getSHA256 but reads at most QUICK_HASH_BYTES bytes.
     * For files smaller than 8KB, the entire file is read — the result is
     * identical to the full hash in that case, which is correct behavior.
     *
     * We check bytesRead > 0 before calling digest.update() to handle the
     * edge case of a zero-byte file (no bytes to hash).
     *
     * Returns null on any failure, same as getSHA256.
     */
    public static String getQuickHash(File file){
        try{
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[QUICK_HASH_BYTES];
            int bytesRead = bis.read(buffer, 0, QUICK_HASH_BYTES);
            bis.close();

            if(bytesRead > 0){
                digest.update(buffer, 0, bytesRead);
            }

            return toHex(digest.digest());

        } catch (NoSuchAlgorithmException e) {
            return null;
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /*
     * Converts a raw byte array into a lowercase hex string.
     *
     * SHA-256 produces 32 bytes. Each byte is converted to a 2-character hex
     * value using "%02x" (lowercase hex, zero-padded to 2 digits).
     * The result is a 64-character string like "a3f1b2c4d5e6...".
     *
     * This is the standard way to display hash values — human-readable and
     * easy to compare visually or store as a map key.
     */
    private static String toHex(byte[] hash){
        StringBuilder sb = new StringBuilder();
        for(byte b : hash){
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}