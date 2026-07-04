package report;

import util.ReportNameGenerator;
import util.SizeFormat;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/*
 * CsvReport writes the analysis results to a .csv (comma-separated values) file.
 *
 * CSV is designed for structured data that can be opened in Excel, Google Sheets,
 * or imported into other tools for further analysis. Unlike TextReport which is
 * designed for human reading, CsvReport is designed for data processing.
 *
 * Currently two sections are written to the CSV:
 *   1. Category report  — category name and total size.
 *   2. Extension report — extension name and file count (top 10 or all).
 *
 * Each section has its own header row (e.g. "Category,Size(Bytes)") so the
 * data is self-describing when opened in a spreadsheet.
 *
 * Only runs if the user passed --csv or --full. If neither was passed,
 * AnalysisRunner skips this class entirely and no file is created.
 */
public class CsvReport implements ReportGenerator {

    /*
     * Generates the .csv report and writes it to disk.
     *
     * Same BufferedWriter + try-with-resources pattern as TextReport —
     * buffered for performance, auto-closed when done even if an error occurs.
     *
     * A blank line is written between sections to make the file easier to read
     * when opened as plain text, while still being valid CSV for spreadsheet apps
     * (they simply show an empty row between the two tables).
     *
     * Each section is only written if its data is non-null in ReportData —
     * so if the user ran with --categories but not --extensions, only the
     * category section appears in the CSV.
     */
    @Override
    public void generateReport(ReportData data) {
        try {
            Path outputPath = ReportNameGenerator.generate(
                    data.getOutputDirectory(), data.getReportTimestamp(), "csv");

            try(BufferedWriter writer = Files.newBufferedWriter(outputPath)){

                // Category section — written only if category analysis was run.
                // Format: one row per category with its name and total size.
                if(data.getCategorySize() != null){
                    writer.write("Category,Size(Bytes)");
                    writer.newLine();

                    for(Map.Entry<String, Long> entry : data.getCategorySize().entrySet()){
                        writer.write(entry.getKey() + "," + SizeFormat.format(entry.getValue()));
                        writer.newLine();
                    }

                    // Blank line between sections for visual separation.
                    writer.newLine();
                }

                // Extension section — written only if extension analysis was run.
                // Writes either the full extension map or just the top 10,
                // depending on whether --all-extensions was passed.
                if(data.getTopExtensions() != null){
                    writer.write("Extension,Count");
                    writer.newLine();

                    if(data.isAllExtensions()){
                        // Write every extension found — no limit.
                        for(Map.Entry<String, Integer> entry : data.getExtensionCount().entrySet()){
                            writer.write(entry.getKey() + "," + entry.getValue());
                            writer.newLine();
                        }
                    } else {
                        // Write only the top 10 most common extensions.
                        // topExtensions is already sorted by count descending.
                        for(Map.Entry<String, Integer> entry : data.getTopExtensions()){
                            writer.write(entry.getKey() + "," + entry.getValue());
                            writer.newLine();
                        }
                    }
                }

                System.out.println("CSV report written to " + outputPath);
            }

        } catch (Exception e) {
            // Don't crash the app if the report can't be written.
            // Console output was already shown — a failed file write is not fatal.
            System.out.println("Error: could not write CSV report (" + e.getMessage() + ")");
        }
    }
}