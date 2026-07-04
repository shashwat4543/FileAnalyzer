package app;

import analyzer.CategoryAnalyzer;
import analyzer.DuplicateFileAnalyzer;
import analyzer.ExtensionAnalyzer;
import analyzer.LargestFileAnalyzer;
import report.AnalysisConfig;
import report.ConsoleReport;
import report.CsvReport;
import report.ReportData;
import report.ReportFactory;
import report.TextReport;
import scanner.DirectoryScanner;
import util.BenchmarkManager;

import java.io.File;

/*
 * AnalysisRunner is the engine of the app — it runs one complete scan cycle.
 *
 * Before this class existed, this logic lived directly in Main. The problem was
 * that the interactive shell also needed to do the exact same thing every time
 * the user typed a command. Rather than copy-pasting the same code in two places,
 * it was pulled out into this single static method that both Main and InteractiveShell
 * can call. This way, a one-shot CLI scan and an interactive shell scan behave
 * identically — they both go through this same code path.
 *
 * The flow every time run() is called:
 *   1. Scan the directory tree and collect all files.
 *   2. Run the selected analyzers on those files (in parallel where possible).
 *   3. Generate reports — always to the console, and optionally to .txt and .csv files.
 *   4. Print benchmark timings if the user asked for them.
 */
public class AnalysisRunner {

    /*
     * Runs a full scan + analysis + report cycle for the given directory and config.
     *
     * @param root   The directory to scan. Already validated by CliParser before this is called.
     * @param config Holds all the user's choices — which analyzers to run, which reports to write,
     *               whether to show benchmarks, etc.
     */
    public static void run(File root, AnalysisConfig config) {

        // Record the start time so we can calculate total runtime at the end
        // if the user passed --benchmark.
        long totalStart = System.nanoTime();

        // Walk the entire directory tree and collect every file's details.
        // ZIP files are automatically opened and their contents are included.
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.scan(root);

        // If absolutely no files were found (e.g. an empty folder),
        // let the user know before continuing — the rest of the app handles
        // empty lists fine, but this message avoids a confusing blank output.
        if(scanner.getTotalFiles() == 0){
            System.out.println("Note: no files were found in '" + root.getPath() + "'");
        }

        // Create one instance of each analyzer.
        // ReportFactory decides which ones actually run based on the config flags.
        ExtensionAnalyzer extensionAnalyzer = new ExtensionAnalyzer();
        CategoryAnalyzer categoryAnalyzer = new CategoryAnalyzer();
        LargestFileAnalyzer largestFileAnalyzer = new LargestFileAnalyzer();
        DuplicateFileAnalyzer duplicateFileAnalyzer = new DuplicateFileAnalyzer();

        // ReportFactory runs the chosen analyzers (in parallel for speed) and
        // packs all their results into a single ReportData object.
        // Every report generator reads from this same object, so there's
        // one source of truth for all the analysis results.
        ReportData data = ReportFactory.create(scanner, extensionAnalyzer, categoryAnalyzer,
                largestFileAnalyzer, duplicateFileAnalyzer, config);

        // Generate reports and record how long it takes for the benchmark table.
        // ConsoleReport always runs — it prints to the terminal.
        // TextReport and CsvReport only run if the user requested them via flags.
        BenchmarkManager.time("Report Generation", () -> {
            new ConsoleReport().generateReport(data);
            if(config.isTxtReport()){
                new TextReport().generateReport(data);
            }
            if(config.isCsvReport()){
                new CsvReport().generateReport(data);
            }
        });

        // Print the timing table if the user passed --benchmark.
        // We pass the total elapsed time from the very start so the table
        // can show both individual stage times and the overall runtime.
        if(config.isBenchmark()){
            BenchmarkManager.print(System.nanoTime() - totalStart);
        }
    }
}