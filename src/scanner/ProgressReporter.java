package scanner;

import util.SizeFormat;

/*
 * ProgressReporter draws a live, updating progress line in the terminal
 * while a scan or hash operation is running.
 *
 * Instead of printing a new line for every file (which would flood the terminal
 * with thousands of lines), it overwrites the same line in place using "\r"
 * (carriage return), which moves the cursor back to the start of the current line
 * without moving to a new line. The next print then overwrites what was there before.
 *
 * It's used in two places:
 *   - DirectoryScanner calls update() while walking the directory tree.
 *   - DuplicateFileAnalyzer calls updateHashing() while computing file hashes.
 *
 * To avoid redrawing too frequently (which would flicker and slow things down),
 * updates are throttled — the line only actually redraws every 250ms,
 * regardless of how often update() is called.
 *
 * Colors and styles use ANSI escape codes, which are special character sequences
 * that modern terminals interpret as formatting instructions rather than text.
 * For example, "\u001B[32m" tells the terminal to switch text color to green.
 */
public class ProgressReporter {

    // The spinner is an array of Braille characters that cycle one at a time,
    // creating the illusion of a spinning animation on a single character.
    private static final String[] SPINNER_FRAMES = {"⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"};

    // How often the progress line actually redraws, in milliseconds.
    // 250ms = 4 redraws per second — fast enough to feel live, slow enough not to flicker.
    private static final long REFRESH_INTERVAL_MS = 250;

    // Paths longer than this get truncated with "..." at the front to keep the line short.
    private static final int MAX_PATH_LENGTH = 50;

    // ANSI escape codes for colors and styles.
    // \u001B[ is the escape sequence prefix — everything after it until "m" is the formatting instruction.
    // RESET clears all formatting back to the terminal's default.
    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String DIM     = "\u001B[2m";    // Dimmed/faded text
    private static final String CYAN    = "\u001B[36m";   // Used for the spinner
    private static final String GREEN   = "\u001B[32m";   // Used for file/folder counts
    private static final String YELLOW  = "\u001B[33m";   // Used for total size
    private static final String MAGENTA = "\u001B[35m";   // Used for speed (files/s)

    private final long startTime;      // When this reporter was created — used to calculate elapsed time.
    private long lastRenderTime = 0;   // When we last drew the progress line — used for throttling.
    private int spinnerIndex = 0;      // Which frame of the spinner animation we're currently on.
    private int lastLineLength = 0;    // How long the last drawn line was (visible characters only).

    public ProgressReporter(){
        startTime = System.currentTimeMillis();
    }

    /*
     * Called by DirectoryScanner after processing each file during the directory walk.
     *
     * Checks if enough time has passed since the last redraw (250ms throttle).
     * If yes, builds the scan progress line and draws it.
     * If no, returns immediately — the call is essentially a no-op.
     *
     * This method is NOT synchronized because DirectoryScanner is single-threaded.
     */
    public void update(long filesScanned, long foldersScanned, long totalSize, String currentPath){
        long now = System.currentTimeMillis();
        if(now - lastRenderTime < REFRESH_INTERVAL_MS){
            return;
        }
        lastRenderTime = now;
        display(buildLine(filesScanned, foldersScanned, totalSize, currentPath, now));
    }

    /*
     * Called by DuplicateFileAnalyzer while hashing files for duplicate detection.
     *
     * Same throttle logic as update(), but shows hashing-specific info:
     * how many files have been hashed so far out of the total to hash.
     *
     * This method IS synchronized because DuplicateFileAnalyzer hashes files
     * in parallel using multiple threads, all of which call this method concurrently.
     * Without synchronization, two threads could corrupt the lastRenderTime or
     * lastLineLength values by reading and writing them at the same time.
     */
    public synchronized void updateHashing(long hashed, long total, String currentPath){
        long now = System.currentTimeMillis();
        if(now - lastRenderTime < REFRESH_INTERVAL_MS){
            return;
        }
        lastRenderTime = now;
        display(buildHashingLine(hashed, total, currentPath, now));
    }

    /*
     * Builds the formatted progress line for directory scanning.
     *
     * The line shows: spinner | "Scanning" label | file count | folder count |
     *                 total size | speed (files/sec) | elapsed time | current path
     *
     * Each piece of information is colored differently so it's easy to scan at a glance.
     * ANSI codes are wrapped around each value — color on before the value, RESET after.
     */
    private String buildLine(long filesScanned, long foldersScanned, long totalSize, String currentPath, long now){
        long elapsedMillis = now - startTime;
        double elapsedSeconds = elapsedMillis / 1000.0;

        // Avoid division by zero at the very start before any time has passed.
        long filesPerSecond = elapsedSeconds > 0 ? (long)(filesScanned / elapsedSeconds) : 0;

        // Advance the spinner to the next frame and wrap around when we reach the end.
        String spinner = SPINNER_FRAMES[spinnerIndex];
        spinnerIndex = (spinnerIndex + 1) % SPINNER_FRAMES.length;

        return String.format(
                "%s%s%s %sScanning%s  %s%,d%s files  %s%,d%s folders  %s%s%s  %s%,d%s files/s  %s%s%s  %s%s%s",
                CYAN, spinner, RESET,
                BOLD, RESET,
                GREEN, filesScanned, RESET,
                GREEN, foldersScanned, RESET,
                YELLOW, SizeFormat.format(totalSize), RESET,
                MAGENTA, filesPerSecond, RESET,
                DIM, formatElapsed(elapsedMillis), RESET,
                DIM, truncatePath(currentPath), RESET
        );
    }

    /*
     * Builds the formatted progress line for duplicate file hashing.
     *
     * Simpler than the scan line — shows: spinner | "Hashing" label |
     * progress (e.g. "1,234/5,678 files") | elapsed time | current file path.
     *
     * Speed isn't shown here because hashing speed varies wildly depending on
     * file size, so a files/sec number would be misleading.
     */
    private String buildHashingLine(long hashed, long total, String currentPath, long now){
        long elapsedMillis = now - startTime;

        String spinner = SPINNER_FRAMES[spinnerIndex];
        spinnerIndex = (spinnerIndex + 1) % SPINNER_FRAMES.length;

        return String.format(
                "%s%s%s %sHashing%s  %s%,d/%,d%s files  %s%s%s  %s%s%s",
                CYAN, spinner, RESET,
                BOLD, RESET,
                GREEN, hashed, total, RESET,
                DIM, formatElapsed(elapsedMillis), RESET,
                DIM, truncatePath(currentPath), RESET
        );
    }

    /*
     * Formats a duration in milliseconds as HH:MM:SS.
     * For example, 90500 ms → "00:01:30".
     */
    private String formatElapsed(long elapsedMillis){
        long totalSeconds = elapsedMillis / 1000;
        long hours   = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    /*
     * Shortens a path to at most MAX_PATH_LENGTH characters.
     * If it's too long, the beginning is replaced with "..." to show it's been cut.
     * The end of the path is kept because the filename and immediate parent folder
     * are more useful to see than the root of the path.
     *
     * Example:
     *   "C:\Users\Lenovo\AppData\Roaming\very\deep\folder\file.txt"
     *   → "...Roaming\very\deep\folder\file.txt"
     */
    private String truncatePath(String path){
        if(path == null) return "";
        if(path.length() <= MAX_PATH_LENGTH) return path;
        return "..." + path.substring(path.length() - MAX_PATH_LENGTH);
    }

    /*
     * Draws the progress line in place by printing "\r" before the text.
     * "\r" moves the cursor to the start of the current line without going to a new line,
     * so the next print overwrites whatever was there before.
     *
     * The padding is the tricky part: if the new line is shorter than the previous one,
     * the leftover characters from the old line would still be visible at the end.
     * We fix this by appending spaces equal to the difference in length, effectively
     * erasing the old line's tail.
     *
     * IMPORTANT: we use visibleLength() to measure line length, not line.length().
     * ANSI escape codes add invisible characters that count toward length() but
     * take up zero space on screen. Using line.length() for padding would add too few
     * spaces and leave ghost characters behind.
     *
     * This method is synchronized so it's safe to call from multiple threads
     * (used by the parallel hashing in DuplicateFileAnalyzer).
     */
    protected synchronized void display(String line){
        int visibleLength = visibleLength(line);
        int padding = Math.max(0, lastLineLength - visibleLength);
        System.out.print("\r" + line + " ".repeat(padding));
        lastLineLength = visibleLength;
    }

    /*
     * Calculates the visible length of a string by stripping out all ANSI escape codes first.
     * ANSI codes match the pattern \u001B[ followed by numbers/semicolons, ending with "m".
     * After stripping them, what's left is only the characters the user actually sees.
     */
    private int visibleLength(String line){
        return line.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }

    /*
     * Called when a scan finishes. Clears the progress line and prints
     * a green checkmark with "Scan completed" on a fresh line.
     *
     * We clear the line first (display("")) before printing the final message
     * so there are no leftover progress characters visible on the same line.
     */
    public void finish(){
        display("");
        System.out.print("\r");
        System.out.println(GREEN + BOLD + "✔ Scan completed" + RESET);
    }

    /*
     * Same as finish() but with a custom message.
     * Used by DuplicateFileAnalyzer to print "✔ Duplicate detection completed"
     * instead of "✔ Scan completed".
     */
    public void finish(String message){
        display("");
        System.out.print("\r");
        System.out.println(GREEN + BOLD + "✔ " + message + RESET);
    }
}