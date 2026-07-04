package report;

import model.FileInfo;
import util.ReportNameGenerator;
import util.SizeFormat;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/*
 * TextReport writes a human-readable .txt file containing the full analysis results.
 *
 * It's the most complete of the three report formats:
 *   - ConsoleReport shows a condensed summary (no duplicate file listing).
 *   - TextReport shows everything, including the full path of every duplicate file.
 *   - CsvReport is structured for spreadsheet use, not human reading.
 *
 * The file is written to the output directory with a timestamp in the filename
 * (e.g. "report_2026-07-01_14-30-00.txt") so repeated scans never overwrite each other.
 *
 * Only runs if the user passed --txt or --full. If neither was passed,
 * AnalysisRunner skips this class entirely and no file is created.
 */
public class TextReport implements ReportGenerator {

    /*
     * Generates the .txt report and writes it to disk.
     *
     * BufferedWriter is used instead of a plain FileWriter because it buffers
     * writes in memory and flushes them to disk in larger chunks — much faster
     * than writing one character or line at a time for large reports.
     *
     * The try-with-resources block (try(BufferedWriter writer = ...)) ensures
     * the file is always properly closed when we're done, even if an exception
     * occurs midway through writing. This prevents file corruption and resource leaks.
     *
     * Each section is only written if its data is non-null in ReportData,
     * meaning only sections the user actually requested are included in the file.
     */
    @Override
    public void generateReport(ReportData data) {
        try {
            Path outputPath = ReportNameGenerator.generate(
                    data.getOutputDirectory(), data.getReportTimestamp(), "txt");

            try(BufferedWriter writer = Files.newBufferedWriter(outputPath)){

                // Header and scan summary — always written regardless of which flags were used.
                writer.write("======FILE REPORT======");
                writer.newLine();
                writer.newLine();
                writer.write("Total Files : " + data.getTotalFile());
                writer.newLine();
                writer.write("Total Folders : " + data.getTotalFolders());
                writer.newLine();
                writer.write("Total Size : " + SizeFormat.format(data.getTotalSize()));
                writer.newLine();
                writer.write("Skipped Files : " + data.getSkippedFiles());
                writer.newLine();
                writer.newLine();

                // Extension section — written only if extension analysis was run.
                // Header changes based on whether --all-extensions was passed.
                if(data.getTopExtensions() != null){
                    writer.write(data.isAllExtensions()
                            ? "======EXTENSION REPORT======"
                            : "======EXTENSION REPORT (TOP 10)======");
                    writer.newLine();

                    if(data.isAllExtensions()){
                        // Write the full extension map — every extension found.
                        for(Map.Entry<String, Integer> entry : data.getExtensionCount().entrySet()){
                            writer.write(entry.getKey() + " -> " + entry.getValue());
                            writer.newLine();
                        }
                    } else {
                        // Write only the top 10 most common extensions.
                        // topExtensions is already sorted by count descending.
                        for(Map.Entry<String, Integer> entry : data.getTopExtensions()){
                            writer.write(entry.getKey() + " -> " + entry.getValue());
                            writer.newLine();
                        }
                    }
                    writer.newLine();
                    writer.newLine();
                }

                // Category section — written only if category analysis was run.
                if(data.getCategorySize() != null){
                    writer.write("======CATEGORY REPORT======");
                    writer.newLine();
                    for(Map.Entry<String, Long> entry : data.getCategorySize().entrySet()){
                        writer.write(entry.getKey() + " -> " + SizeFormat.format(entry.getValue()));
                        writer.newLine();
                    }
                    writer.newLine();
                }

                // Largest files section — written only if largest-file analysis was run.
                if(data.getTopFiles() != null){
                    writer.write("======LARGEST FILES======");
                    writer.newLine();
                    for(FileInfo file : data.getTopFiles()){
                        writer.write(file.getName() + " -> " + SizeFormat.format(file.getSize()));
                        writer.newLine();
                    }
                    writer.newLine();
                }

                // Duplicate files section — written only if duplicate detection was run.
                // Unlike ConsoleReport, the txt report includes the full path of every
                // duplicate file in every group, so the user can actually find and delete them.
                if(data.getDuplicateGroups() != null){
                    writer.write("======DUPLICATE FILES======");
                    writer.newLine();
                    writer.write("Duplicate Groups : " + data.getDuplicateGroups().size());
                    writer.newLine();
                    writer.write("Total Duplicate Size : " + SizeFormat.format(data.getTotalDuplicateSize()));
                    writer.newLine();
                    writer.write("Wasted Space : " + SizeFormat.format(data.getWastedSpace()));
                    writer.newLine();

                    // Write each duplicate group with a number and the full path of every file in it.
                    // Full paths (not just filenames) are used so the user knows exactly where each file is.
                    int group = 1;
                    for(List<FileInfo> files : data.getDuplicateGroups().values()){
                        writer.write("Duplicate Group " + group++);
                        writer.newLine();
                        for(FileInfo file : files){
                            writer.write(file.getPath());
                            writer.newLine();
                        }
                        writer.newLine();
                    }
                }

                System.out.println("Text report written to " + outputPath);
            }

        } catch (Exception e) {
            // Don't crash the app if the report can't be written.
            // The user already saw the console output — a failed file write
            // is unfortunate but not fatal.
            System.out.println("Error: could not write text report (" + e.getMessage() + ")");
        }
    }
}