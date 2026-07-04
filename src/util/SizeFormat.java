package util;

/*
 * SizeFormat converts a raw byte count into a human-readable size string.
 *
 * Raw byte numbers are hard to read at a glance — "3,610,000,000 bytes" is less
 * useful than "3.36 GB". This utility formats any byte value into the most
 * appropriate unit so the output is always easy to understand.
 *
 * Used everywhere a file or total size is displayed — in ConsoleReport,
 * TextReport, CsvReport, and ProgressReporter.
 */
public class SizeFormat {

    /*
     * Formats a byte count into a human-readable string with the appropriate unit.
     *
     * The thresholds use powers of 1024 (not 1000) because file sizes on disk
     * are measured in binary units:
     *   1 KB = 1,024 bytes
     *   1 MB = 1,048,576 bytes  (1024 × 1024)
     *   1 GB = 1,073,741,824 bytes  (1024 × 1024 × 1024)
     *
     * Examples:
     *   500          → "500B"
     *   2,048        → "2.00 KB"
     *   1,572,864    → "1.50 MB"
     *   3,610,000,000 → "3.36 GB"
     *
     * Sizes are formatted to 2 decimal places for KB, MB, and GB so the
     * output is consistent and precise enough to be useful without being cluttered.
     */
    public static String format(long bytes){
        if(bytes < 1024)
            return bytes + "B";

        if(bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);

        if(bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024));

        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}