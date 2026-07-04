package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
 * ReportNameGenerator handles everything related to where report files are saved
 * and what they're named.
 *
 * Report files are named using a timestamp so that repeated scans never overwrite
 * each other. Each scan produces uniquely named files like:
 *   report_2026-07-01_14-30-00.txt
 *   report_2026-07-01_14-30-00.csv
 *
 * The timestamp is generated once per scan (in ReportFactory) and shared across
 * all report generators so the .txt and .csv files from the same scan always
 * have matching timestamps — they're easy to pair up in the output folder.
 *
 * The default output directory is ~/FileAnalyzer/reports, which translates to:
 *   Windows: C:\Users\YourName\FileAnalyzer\reports
 *   Linux/Mac: /home/yourname/FileAnalyzer/reports
 */
public class ReportNameGenerator {

    // The format pattern for timestamps used in report filenames.
    // "yyyy-MM-dd_HH-mm-ss" produces strings like "2026-07-01_14-30-00".
    // Colons are replaced with dashes because colons are illegal in Windows filenames.
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /*
     * Returns the default output directory path as a string.
     *
     * System.getProperty("user.home") returns the current user's home directory,
     * which works on Windows, Linux, and macOS without any hardcoded paths.
     * Paths.get() then appends "FileAnalyzer/reports" to it cleanly using
     * the correct path separator for the current OS.
     *
     * The directory is not created here — that happens in generate() when a report
     * is actually about to be written.
     */
    public static String defaultOutputDirectory(){
        return Paths.get(System.getProperty("user.home"), "FileAnalyzer", "reports").toString();
    }

    /*
     * Captures the current date and time as a formatted string for use in filenames.
     *
     * Called once per scan in ReportFactory so that all report files from the same
     * scan share the same timestamp. If each report generated its own timestamp,
     * the .txt and .csv files might end up with slightly different names if the
     * clock ticked between generating them.
     *
     * Example output: "2026-07-01_14-30-00"
     */
    public static String generateTimestamp(){
        return LocalDateTime.now().format(FORMATTER);
    }

    /*
     * Builds the full output path for a report file and ensures the directory exists.
     *
     * Combines the output directory, the shared timestamp, and the file extension
     * to produce a path like:
     *   C:\Users\You\FileAnalyzer\reports\report_2026-07-01_14-30-00.txt
     *
     * Files.createDirectories() creates the entire directory path if it doesn't
     * already exist — including any missing parent directories. If the directory
     * already exists, it does nothing (no error). This means the user never has
     * to manually create the output folder before running the app.
     *
     * toAbsolutePath().normalize() cleans up the path before creating directories,
     * resolving any relative parts (like "~" shortcuts) and redundant separators.
     *
     * Throws RuntimeException (not a checked exception) if the directory can't be
     * created, so callers don't need to handle it explicitly — it bubbles up to
     * the top-level error handler in AnalysisRunner.
     */
    public static Path generate(String outputDirectory, String timestamp, String extension){
        Path directory = Paths.get(outputDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException(
                    "could not create output directory '" + directory + "': " + e.getMessage());
        }
        return directory.resolve("report_" + timestamp + "." + extension);
    }
}