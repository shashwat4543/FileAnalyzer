package app;

import report.AnalysisConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/*
 * InteractiveShell turns the app into a persistent prompt — like a mini terminal.
 *
 * When the user launches FileAnalyzer with no arguments (e.g. by double-clicking
 * the exe), instead of printing help and immediately closing the window, we drop
 * into this shell. The user can then type scan commands one after another without
 * relaunching the app each time.
 *
 * It works exactly like a normal terminal session:
 *   - Type a directory path (with optional flags) and press Enter to run a scan.
 *   - Type "help" to see all available flags.
 *   - Type "exit" or "quit" to close the app.
 *   - Type "cls" or "clear" to wipe the screen.
 *
 * Example session:
 *   FileAnalyzer> C:\Users\You\Documents --quick
 *   FileAnalyzer> "C:\path with spaces\My Files" --full
 *   FileAnalyzer> help
 *   FileAnalyzer> exit
 *
 * If the user types something wrong (bad path, unknown flag), the shell prints
 * the error and loops back to the prompt — it never crashes or closes the window.
 */
public class InteractiveShell {

    /*
     * Starts the interactive shell and keeps it running until the user exits.
     *
     * This is a simple loop:
     *   1. Print the "FileAnalyzer>" prompt.
     *   2. Read a line of input from the user.
     *   3. Handle built-in commands (exit, help, clear, version).
     *   4. For anything else, treat it as a scan command and run it.
     *   5. Go back to step 1.
     *
     * We use BufferedReader instead of Scanner here because BufferedReader is
     * better suited for reading line-by-line input in a loop — it handles
     * EOF (Ctrl+Z on Windows) more cleanly.
     */
    public static void run() {
        printBanner();

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("FileAnalyzer> ");
            System.out.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                System.out.println("Input error: " + e.getMessage());
                break;
            }

            // readLine() returns null when the user presses Ctrl+Z (Windows EOF).
            // Treat it the same as typing "exit".
            if (line == null) {
                System.out.println();
                break;
            }

            line = line.trim();

            // Skip blank lines — just show the prompt again.
            if (line.isEmpty()) {
                continue;
            }

            // Check for built-in shell commands first before trying to parse
            // the input as a scan command. We use lowercase comparison so
            // "EXIT", "Exit", and "exit" all work the same way.
            String lower = line.toLowerCase();
            if (lower.equals("exit") || lower.equals("quit")) {
                break;
            }
            if (lower.equals("help") || lower.equals("--help") || lower.equals("-h")) {
                CliParser.printHelp();
                continue;
            }
            if (lower.equals("version") || lower.equals("--version")) {
                CliParser.printVersion();
                continue;
            }
            if (lower.equals("cls") || lower.equals("clear")) {
                clearScreen();
                continue;
            }

            // Not a built-in command — treat the whole line as a scan command.
            // Split it into tokens (handling quoted paths with spaces),
            // then parse and run exactly like a normal CLI invocation.
            String[] args = tokenize(line);

            try {
                File root = CliParser.parsePathOrThrow(args);
                AnalysisConfig config = CliParser.parseConfigOrThrow(args);
                AnalysisRunner.run(root, config);

            } catch (CliParser.HelpRequested e) {
                // User typed "--help" as part of a scan command.
                CliParser.printHelp();

            } catch (CliParser.VersionRequested e) {
                // User typed "--version" as part of a scan command.
                CliParser.printVersion();

            } catch (CliParseException e) {
                // Bad path, unknown flag, conflicting flags, etc.
                // Print the error but keep the shell alive — never exit on a bad command.
                System.out.println(e.getMessage());
                if (e.showHelp) {
                    CliParser.printHelp();
                }

            } catch (Exception e) {
                // Catch-all for anything unexpected (disk error, null pointer, etc.).
                // Again — print and loop, never crash the whole shell.
                System.out.println("Error: " + e.getMessage());
            }

            // Print a blank line after each command for visual breathing room.
            System.out.println();
        }

        System.out.println("Goodbye!");
    }

    /*
     * Prints the welcome message shown once when the shell first opens.
     * Tells the user what version they're running and how to get started.
     */
    private static void printBanner() {
        System.out.println(AppInfo.NAME + " v" + AppInfo.VERSION + " - interactive mode");
        System.out.println("Type a directory path (with optional flags) to scan it, 'help' for options, or 'exit' to quit.");
        System.out.println();
    }

    /*
     * Clears the terminal screen using an ANSI escape code.
     *
     * \u001B[H moves the cursor to the top-left corner.
     * \u001B[2J clears everything on screen.
     *
     * This works in Windows Terminal, Git Bash, and most modern consoles.
     * Older terminals that don't support ANSI codes will just see garbage characters,
     * but that's acceptable since the rest of the app already uses ANSI for colors.
     */
    private static void clearScreen() {
        System.out.print("\u001B[H\u001B[2J");
        System.out.flush();
    }

    /*
     * Splits a line of text into individual tokens (words), just like a real shell would.
     *
     * The tricky part is handling paths that contain spaces, like:
     *   "C:\My Files\Documents" --quick
     *
     * Without quotes, splitting on spaces would break "C:\My Files\Documents" into
     * three separate tokens. With quotes, everything inside the quotes is kept together
     * as one token, and the quote characters themselves are removed.
     *
     * Both double quotes (") and single quotes (') are supported.
     *
     * Examples:
     *   C:\Users\You\Documents --quick
     *   → ["C:\Users\You\Documents", "--quick"]
     *
     *   "C:\My Files\Docs" --output "C:\My Reports"
     *   → ["C:\My Files\Docs", "--output", "C:\My Reports"]
     */
    private static String[] tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = '"';

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == quoteChar) {
                    // Found the closing quote — stop collecting quoted characters.
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                // Found an opening quote — start collecting everything until the matching close quote.
                inQuotes = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                // Space outside quotes = end of current token.
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        // Don't forget the last token if the line doesn't end with a space.
        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }
}