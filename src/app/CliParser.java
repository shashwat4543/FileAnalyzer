package app;

import report.AnalysisConfig;
import util.ReportNameGenerator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * CliParser reads and validates everything the user types on the command line.
 *
 * A command looks like this:
 *   FileAnalyzer <directory> [flags...]
 *   e.g. FileAnalyzer C:\Users\You\Documents --quick --csv
 *
 * This class has two jobs:
 *   1. Read the directory path and make sure it's valid.
 *   2. Read the flags and build an AnalysisConfig that tells the rest of the app what to do.
 *
 * Each job comes in two flavors:
 *   - The regular version (parsePath / parseConfig): used by Main for one-shot CLI usage.
 *     On error, it prints the message and calls System.exit() to close the app.
 *   - The "OrThrow" version (parsePathOrThrow / parseConfigOrThrow): used by InteractiveShell.
 *     On error, it throws a CliParseException instead of exiting, so the shell can catch it,
 *     print the error, and loop back to the prompt without closing the window.
 *
 * HelpRequested and VersionRequested are tiny signal classes at the bottom of this file.
 * They're thrown instead of returning a value when the user asks for --help or --version,
 * because those aren't errors — they're intentional requests that need special handling
 * by whoever called the parser.
 */
public class CliParser {

    // Every flag the app understands. If the user types anything not in this list,
    // they get an "unknown option" error. Keeping the list here in one place makes
    // it easy to add new flags in the future.
    private static final Set<String> KNOWN_FLAGS = Set.of(
            "--quick", "--full", "--duplicates", "--extensions",
            "--categories", "--largest", "--txt", "--csv",
            "--output", "--benchmark", "--help", "-h", "--all-extensions"
    );

    /*
     * One-shot CLI version of path parsing.
     * Used by Main when the app is launched with arguments from an existing terminal.
     * On any error, prints the message and exits the process immediately.
     */
    public static File parsePath(String[] args){
        try {
            return parsePathOrThrow(args);

        } catch (HelpRequested e){
            printHelp();
            System.exit(0);
            return null; // unreachable — Java requires a return statement after try-catch

        } catch (VersionRequested e){
            printVersion();
            System.exit(0);
            return null; // unreachable

        } catch (CliParseException e){
            System.out.println(e.getMessage());
            if (e.showHelp) printHelp();
            System.exit(1);
            return null; // unreachable
        }
    }

    /*
     * One-shot CLI version of config parsing.
     * Same pattern as parsePath above — exits the process on any error.
     */
    public static AnalysisConfig parseConfig(String[] args){
        try {
            return parseConfigOrThrow(args);

        } catch (CliParseException e){
            System.out.println(e.getMessage());
            if (e.showHelp) printHelp();
            System.exit(1);
            return null; // unreachable
        }
    }

    /*
     * Interactive shell version of path parsing.
     * Never calls System.exit() — throws exceptions instead so the shell
     * can handle them without closing the window.
     *
     * Checks in order:
     *   1. No args, or --help/-h → throw HelpRequested (not an error, just a request).
     *   2. --version → throw VersionRequested (same idea).
     *   3. First arg starts with "-" → error (user forgot to put the directory first).
     *   4. Path doesn't exist → error.
     *   5. Path is a file, not a folder → error.
     *   6. Path can't be read (permission denied) → error.
     *
     * We use toAbsolutePath().normalize() to clean up the path before checking it.
     * This resolves relative paths like "." or ".." and removes redundant slashes,
     * so error messages always show the full, clean path.
     */
    public static File parsePathOrThrow(String[] args){
        if(args.length == 0 || args[0].equals("--help") || args[0].equals("-h")){
            throw new HelpRequested();
        }

        if(args[0].equals("--version")){
            throw new VersionRequested();
        }

        if(args[0].startsWith("-")){
            throw new CliParseException("Error: expected a directory path as the first argument", true);
        }

        Path root = Paths.get(args[0]).toAbsolutePath().normalize();

        if(!Files.exists(root)){
            throw new CliParseException("Error: directory '" + root + "' does not exist", false);
        }
        if(!Files.isDirectory(root)){
            throw new CliParseException("Error: '" + root + "' is a file, not a directory", false);
        }
        if(!Files.isReadable(root)){
            throw new CliParseException("Error: permission denied reading directory '" + root + "'", false);
        }

        return root.toFile();
    }

    /*
     * Interactive shell version of config parsing.
     * Reads all arguments after the directory path and builds an AnalysisConfig.
     * Throws CliParseException on any error instead of calling System.exit().
     *
     * Parsing happens in stages:
     *
     * Stage 1 — Extract --output separately.
     *   --output is special because it takes a value after it (e.g. --output C:\Reports).
     *   We find it by position, grab the next argument as its value, then remove both
     *   from the list so they don't confuse the flag validation in the next stage.
     *   We also make sure --output only appears once and actually has a value after it.
     *
     * Stage 2 — Convert remaining args to a Set.
     *   A Set automatically ignores duplicates (so --csv --csv is harmless) and
     *   makes flag checking instant — flags.contains("--quick") is O(1).
     *
     * Stage 3 — Validate every flag against KNOWN_FLAGS.
     *   Any flag not in the list is rejected with an error and the help text.
     *
     * Stage 4 — Check for conflicting flag combinations.
     *   Some flags contradict each other and are rejected before building the config.
     *
     * Stage 5 — Build the AnalysisConfig.
     *   Each flag that's present sets the corresponding boolean in the config.
     *   If the user didn't pass any analyzer flag at all, we default to --full behavior
     *   (run everything, write both reports). This means running the app with just a
     *   directory path and no flags gives a complete analysis — a sensible default.
     */
    public static AnalysisConfig parseConfigOrThrow(String[] args){
        // Skip the first argument (the directory path) — only flags from here on.
        List<String> rest = new ArrayList<>(Arrays.asList(args).subList(1, args.length));

        // --output can only appear once. Two would be ambiguous — which path wins?
        int outputCount = 0;
        for(String arg : rest){
            if(arg.equals("--output")) outputCount++;
        }
        if(outputCount > 1){
            throw new CliParseException("Error: --output specified more than once", false);
        }

        // Extract --output and its value from the list before converting to a Set.
        // We check that a value actually follows --output and isn't another flag
        // (which would mean the user forgot to provide the path).
        String outputDirectory = ReportNameGenerator.defaultOutputDirectory();
        int outputIndex = rest.indexOf("--output");
        if(outputIndex != -1){
            boolean missingValue = outputIndex + 1 >= rest.size() || rest.get(outputIndex + 1).startsWith("-");
            if(missingValue){
                throw new CliParseException("Error: --output requires a directory path", false);
            }
            outputDirectory = rest.get(outputIndex + 1);
            // Remove both "--output" and its value so they don't show up as unknown flags.
            rest.remove(outputIndex + 1);
            rest.remove(outputIndex);
        }

        Set<String> flags = new HashSet<>(rest);

        // Reject any flag that isn't in our known list.
        for(String flag : flags){
            if(!KNOWN_FLAGS.contains(flag)){
                throw new CliParseException("Error: unknown option '" + flag + "'", true);
            }
        }

        // Catch conflicting combinations before we start building the config.
        if(flags.contains("--quick") && flags.contains("--full")){
            throw new CliParseException("Error: --quick and --full cannot be used together", false);
        }
        if(flags.contains("--quick") && flags.contains("--duplicates")){
            throw new CliParseException("Error: --quick disables duplicate detection, so it conflicts with --duplicates", false);
        }

        AnalysisConfig config = new AnalysisConfig();
        config.setOutputDirectory(outputDirectory);
        config.setBenchmark(flags.contains("--benchmark"));
        config.setAllExtensions(flags.contains("--all-extensions"));

        // If the user didn't pass any analyzer flag, default to --full.
        // This makes "FileAnalyzer C:\Docs" equivalent to "FileAnalyzer C:\Docs --full".
        boolean noAnalyzerFlag = !flags.contains("--quick") && !flags.contains("--full")
                && !flags.contains("--duplicates") && !flags.contains("--extensions")
                && !flags.contains("--categories") && !flags.contains("--largest");

        if(flags.contains("--full") || noAnalyzerFlag){
            config.setExtensionAnalysis(true);
            config.setCategoryAnalysis(true);
            config.setLargestFiles(true);
            config.setDuplicateDetection(true);
            config.setTxtReport(true);
            config.setCsvReport(true);
        }

        // --quick is the fast mode: runs the three cheap analyzers but skips
        // duplicate detection because hashing every file is the slowest part.
        if(flags.contains("--quick")){
            config.setExtensionAnalysis(true);
            config.setCategoryAnalysis(true);
            config.setLargestFiles(true);
            config.setDuplicateDetection(false);
        }

        // Individual flags let the user pick exactly what they want.
        // These stack on top of whatever --quick or --full already enabled.
        if(flags.contains("--duplicates")) config.setDuplicateDetection(true);
        if(flags.contains("--extensions")) config.setExtensionAnalysis(true);
        if(flags.contains("--categories")) config.setCategoryAnalysis(true);
        if(flags.contains("--largest"))    config.setLargestFiles(true);
        if(flags.contains("--txt"))        config.setTxtReport(true);
        if(flags.contains("--csv"))        config.setCsvReport(true);

        return config;
    }

    /*
     * Prints the app name and version info.
     * Called when the user types --version or "version" in the shell.
     */
    static void printVersion(){
        System.out.println(AppInfo.NAME);
        System.out.printf("%-8s: %s%n", "Version", AppInfo.VERSION);
        System.out.printf("%-8s: %s%n", "Java", AppInfo.JAVA_VERSION);
        System.out.printf("%-8s: %s%n", "Author", AppInfo.AUTHOR);
    }

    /*
     * Prints the full usage guide showing every available flag and what it does.
     * Called when the user types --help, -h, "help", or provides an unknown flag.
     */
    static void printHelp(){
        System.out.println("""
                Usage: <directory> [options]

                Options:
                  --quick              Extension, category, and largest-file analysis (no duplicates)
                  --full               Run every analyzer and write both reports
                  --duplicates         Run duplicate detection (in addition to other flags)
                  --extensions         Run extension analysis
                  --categories         Run category analysis
                  --largest            Find the largest files
                  --all-extensions     Show all extensions instead of top 10
                  --txt                Write a .txt report
                  --csv                Write a .csv report
                  --output <directory> Write reports to this directory (default: ~/FileAnalyzer/reports)
                  --benchmark          Print timing for each stage after running
                  --version            Show version information
                  --help, -h           Show this help message

                """);
    }

    /*
     * Thrown when the user asks for --help or passes no arguments in CLI mode.
     * This is not an error — it's an intentional request.
     * Using a dedicated class (instead of a boolean or string) makes the intent
     * clear at the catch site and keeps the control flow readable.
     */
    public static class HelpRequested extends RuntimeException {}

    /*
     * Thrown when the user asks for --version.
     * Same idea as HelpRequested — a signal, not an error.
     */
    public static class VersionRequested extends RuntimeException {}
}