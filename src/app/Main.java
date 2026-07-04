package app;

import report.AnalysisConfig;

import java.io.File;

public class Main {

    /*
     * This is the very first method Java runs when you launch the app.
     *
     * It does one simple thing: decide which mode to run in.
     *
     * MODE 1 - Interactive Shell (no arguments):
     *   When you double-click the exe or just type "FileAnalyzer" in the terminal
     *   with nothing after it, there's no directory to scan yet. Instead of just
     *   printing help and closing, we open a persistent prompt (like a mini terminal)
     *   where you can type scan commands one by one without relaunching the app.
     *   This is handled by InteractiveShell.
     *
     * MODE 2 - One-shot CLI (arguments provided):
     *   When you type something like "FileAnalyzer C:\Users\You\Documents --quick",
     *   we parse the path and flags, run the scan once, print the results, and exit.
     *   This is the classic command-line tool behavior.
     *
     * The try-catch at the top level is a safety net — if something completely
     * unexpected goes wrong anywhere in the app, it prints a clean error message
     * instead of dumping a confusing Java crash report at the user.
     */
    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                // No arguments = almost certainly launched by double-clicking the exe.
                // Open the interactive shell so the window stays open and useful.
                InteractiveShell.run();
                return;
            }

            // Arguments were provided = normal one-shot usage from an existing terminal.
            // Parse the directory path and flags, run the full analysis, then exit.
            File root = CliParser.parsePath(args);
            AnalysisConfig config = CliParser.parseConfig(args);
            AnalysisRunner.run(root, config);

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}