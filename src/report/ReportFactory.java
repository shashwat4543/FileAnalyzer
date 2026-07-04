package report;

import analyzer.CategoryAnalyzer;
import analyzer.DuplicateFileAnalyzer;
import analyzer.ExtensionAnalyzer;
import analyzer.LargestFileAnalyzer;
import model.FileInfo;
import scanner.DirectoryScanner;
import util.BenchmarkManager;
import util.ReportNameGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
 * ReportFactory runs all the selected analyzers and packages their results
 * into a single ReportData object that every report generator can read from.
 *
 * Why run analyzers in parallel?
 *   ExtensionAnalyzer, CategoryAnalyzer, and LargestFileAnalyzer all do independent
 *   work — none of them depend on each other's results. Running them one after another
 *   would waste time. By submitting them to a thread pool, they all run at the same time
 *   and the total wait time is roughly equal to the slowest one, not the sum of all three.
 *
 * DuplicateFileAnalyzer also runs in the same pool. It does its own internal parallel
 *   hashing (using its own thread pool), so it's already doing concurrent work inside.
 *   Submitting it here just means it starts at the same time as the other analyzers
 *   rather than waiting for them to finish first.
 *
 * After all analyzers finish, their results are collected into a ReportData object.
 * ReportData is then passed to ConsoleReport, TextReport, and CsvReport — they all
 * read from the same object so there's one source of truth for the entire analysis.
 */
public class ReportFactory {

    /*
     * Runs the selected analyzers in parallel and returns a fully populated ReportData.
     *
     * Only analyzers that the user actually requested (via flags in AnalysisConfig) are run.
     * For example, if the user passed --quick, isDuplicateDetection() returns false and
     * DuplicateFileAnalyzer is never submitted to the thread pool.
     *
     * The pattern used here:
     *   1. Submit each enabled analyzer as a task to the thread pool → get a Future back.
     *   2. Call future.get() on each Future to wait for it to finish.
     *   3. Shut down the thread pool.
     *   4. Collect results from each analyzer into ReportData.
     *
     * future.get() blocks until that task completes, so by the time we reach
     * the result-collection section, all analyzers are guaranteed to be done.
     */
    public static ReportData create(DirectoryScanner scanner, ExtensionAnalyzer extAnalyzer,
                                    CategoryAnalyzer catAnalyzer, LargestFileAnalyzer largeAnalyzer,
                                    DuplicateFileAnalyzer duplicateAnalyzer, AnalysisConfig config){

        ArrayList<FileInfo> files = scanner.getFiles();

        // A fixed thread pool with 4 threads — one per analyzer at most.
        // "Fixed" means if all 4 analyzers are submitted, they all start immediately.
        // If fewer are submitted, the unused threads simply stay idle.
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        // Submit each enabled analyzer as a background task.
        // BenchmarkManager.time() wraps the analyze() call to record how long it takes,
        // so the --benchmark output can show individual timing for each stage.
        if(config.isExtensionAnalysis()){
            futures.add(executor.submit(() ->
                    BenchmarkManager.time("Extension Analysis", () -> extAnalyzer.analyze(files))));
        }
        if(config.isCategoryAnalysis()){
            futures.add(executor.submit(() ->
                    BenchmarkManager.time("Category Analysis", () -> catAnalyzer.analyze(files))));
        }
        if(config.isLargestFiles()){
            futures.add(executor.submit(() ->
                    BenchmarkManager.time("Largest File Analysis", () -> largeAnalyzer.analyze(files))));
        }
        if(config.isDuplicateDetection()){
            futures.add(executor.submit(() ->
                    BenchmarkManager.time("Duplicate Detection", () -> duplicateAnalyzer.analyze(files))));
        }

        // Wait for every submitted analyzer to finish before we collect results.
        // If any task threw an exception internally, future.get() will re-throw it here
        // wrapped in an ExecutionException.
        try {
            for(Future<?> future : futures){
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        // All tasks are done — shut down the thread pool to release its resources.
        executor.shutdown();

        // Populate ReportData with scan stats and analyzer results.
        // Fields that weren't requested (analyzer not run) are left null,
        // and the report generators check for null before printing each section.
        ReportData data = new ReportData();
        data.setTotalFile(scanner.getTotalFiles());
        data.setTotalFolders(scanner.getTotalFolders());
        data.setTotalSize(scanner.getTotalSize());
        data.setSkippedFiles(scanner.getSkippedFiles());
        data.setOutputDirectory(config.getOutputDirectory());
        data.setReportTimestamp(ReportNameGenerator.generateTimestamp());
        data.setAllExtensions(config.isAllExtensions());

        if(config.isExtensionAnalysis()){
            data.setExtensionCount(extAnalyzer.getExtensionCount());
            // topExtensions is the pre-sorted top 10 list — stored separately
            // so reports don't have to re-sort the map every time they need the top 10.
            data.setTopExtensions(extAnalyzer.getTopTenExtension());
        }
        if(config.isCategoryAnalysis()){
            data.setCategorySize(catAnalyzer.getCategorySize());
        }
        if(config.isLargestFiles()){
            data.setTopFiles(largeAnalyzer.getTopFiles());
        }
        if(config.isDuplicateDetection()){
            data.setDuplicateGroups(duplicateAnalyzer.getDuplicateGroups());
            data.setWastedSpace(duplicateAnalyzer.getWastedSpace());
            data.setTotalDuplicateSize(duplicateAnalyzer.getDuplicateSize());
        }

        return data;
    }
}