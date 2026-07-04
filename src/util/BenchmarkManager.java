package util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/*
 * BenchmarkManager records how long each stage of the analysis takes
 * and prints a formatted timing table when the user passes --benchmark.
 *
 * It's a static utility class — no instances are created. Any part of the app
 * can call record() or time() to log a duration, and print() at the end
 * displays everything in a clean table.
 *
 * Stages currently tracked:
 *   Directory Scan       — time spent walking the folder tree (excluding ZIP scanning)
 *   ZIP Scan             — time spent opening and reading ZIP archives
 *   Extension Analysis   — time spent counting file extensions
 *   Category Analysis    — time spent grouping files by category
 *   Largest File Analysis — time spent finding the top 5 largest files
 *   Duplicate Detection  — time spent hashing files for duplicate detection
 *   Report Generation    — time spent writing all reports
 *
 * ConcurrentHashMap is used instead of a regular HashMap because multiple analyzer
 * threads can call record() at the same time (they run in parallel in ReportFactory).
 * ConcurrentHashMap handles concurrent writes safely without needing explicit locking.
 */
public class BenchmarkManager {

    // Stores stage label → duration in nanoseconds.
    // ConcurrentHashMap is thread-safe for concurrent puts from multiple analyzer threads.
    private static final Map<String, Long> durations = new ConcurrentHashMap<>();

    // The order in which stages are printed in the benchmark table.
    // Using a fixed array rather than iterating the map ensures the stages always
    // appear in a logical top-to-bottom order (scan first, reports last),
    // regardless of which order they actually finished in.
    private static final String[] DISPLAY_ORDER = {
            "Directory Scan", "ZIP Scan", "Extension Analysis", "Category Analysis",
            "Largest File Analysis", "Duplicate Detection", "Report Generation"
    };

    /*
     * Records a duration for a named stage.
     * Called directly by DirectoryScanner for scan-related timings,
     * since it tracks ZIP and directory scan time separately using nanoTime().
     *
     * @param label         The name of the stage (must match an entry in DISPLAY_ORDER to appear in the table).
     * @param durationNanos The elapsed time in nanoseconds.
     */
    public static void record(String label, long durationNanos){
        durations.put(label, durationNanos);
    }

    /*
     * Runs a task, measures how long it takes, and records the result.
     *
     * This is a convenience wrapper so callers don't have to manually
     * capture start/end times and call record() themselves. Used by
     * ReportFactory to time each analyzer and AnalysisRunner to time
     * report generation.
     *
     * Example usage:
     *   BenchmarkManager.time("Extension Analysis", () -> extAnalyzer.analyze(files));
     *
     * @param label The stage name to record the timing under.
     * @param task  The code to run and time, passed as a lambda.
     */
    public static void time(String label, Runnable task){
        long start = System.nanoTime();
        task.run();
        record(label, System.nanoTime() - start);
    }

    /*
     * Prints the benchmark timing table to the terminal.
     *
     * Only stages that were actually recorded appear in the table —
     * if the user ran with --quick (no duplicate detection), "Duplicate Detection"
     * simply won't be in the durations map and is skipped.
     *
     * The total runtime is printed separately at the bottom.
     * It's passed in from the caller (AnalysisRunner) rather than computed here
     * because we want the total to include everything from start to finish,
     * including time spent before the first BenchmarkManager.time() call.
     *
     * "%-22s" in the format string left-aligns the label in a 22-character wide column
     * so all the timing values line up in a neat column on the right.
     *
     * Example output:
     *   ========== Benchmark ==========
     *
     *   Directory Scan         : 00:00:02.341
     *   ZIP Scan               : 00:00:00.087
     *   Extension Analysis     : 00:00:00.012
     *   Duplicate Detection    : 00:00:14.203
     *   Report Generation      : 00:00:00.004
     *
     *   --------------------------------
     *   Total Runtime          : 00:00:16.648
     */
    public static void print(long totalDurationNanos){
        System.out.println("\n========== Benchmark ==========\n");
        for(String label : DISPLAY_ORDER){
            if(durations.containsKey(label)){
                System.out.printf("%-22s : %s%n", label, format(durations.get(label)));
            }
        }
        System.out.println("\n--------------------------------");
        System.out.printf("%-22s : %s%n", "Total Runtime", format(totalDurationNanos));
    }

    /*
     * Converts a nanosecond duration into a human-readable HH:MM:SS.mmm string.
     *
     * We use nanoseconds internally (System.nanoTime()) for the most precise
     * measurements possible, then convert to milliseconds for display since
     * sub-millisecond precision isn't meaningful for stage timings.
     *
     * Example: 16,648,000,000 ns → "00:00:16.648"
     */
    private static String format(long nanos){
        long millis = nanos / 1_000_000;
        long hours   = millis / 3_600_000;
        long minutes = (millis % 3_600_000) / 60_000;
        long seconds = (millis % 60_000) / 1000;
        long remainderMillis = millis % 1000;
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, remainderMillis);
    }
}