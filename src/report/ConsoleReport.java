package report;

import model.FileInfo;
import util.SizeFormat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * ConsoleReport prints the analysis results directly to the terminal.
 *
 * It always runs after every scan — even if the user didn't request any
 * report files, they always see the results on screen. It's the primary
 * way the user sees what the app found.
 *
 * Each section (summary, categories, extensions, largest files, duplicates)
 * is printed by a dedicated method so the code stays readable and each
 * section is easy to find and modify independently.
 *
 * Sections that weren't analyzed (null in ReportData) are silently skipped —
 * the user only sees sections relevant to the flags they passed.
 */
public class ConsoleReport implements ReportGenerator {

    /*
     * Prints the summary section — always shown regardless of which flags were used.
     * Gives the user an instant overview of what was found:
     * total files, folders, combined size, and how many items were skipped
     * due to permission restrictions.
     */
    public void printSummary(ReportData data){
        System.out.println("\n=======SUMMARY=======\n");
        System.out.println("Files : " + data.getTotalFile());
        System.out.println("Folders : " + data.getTotalFolders());
        System.out.println("Total Size : " + SizeFormat.format(data.getTotalSize()));
        System.out.println("Skipped Files : " + data.getSkippedFiles());
    }

    /*
     * Prints the extension report — either the full map or just the top 10,
     * depending on whether the user passed --all-extensions.
     *
     * The 'all' parameter controls the section header text so it's clear
     * to the user whether they're seeing all extensions or just the top 10.
     *
     * The method accepts a Map<String, Integer> (rather than the specific HashMap or List
     * types stored in ReportData) so it works for both cases:
     *   - Full map:  passed directly as a HashMap.
     *   - Top 10:    the List<Map.Entry> is converted to a LinkedHashMap before being
     *                passed here, so the sorted order is preserved during iteration.
     *                (A regular HashMap doesn't guarantee iteration order.)
     */
    public void printExtensionReport(Map<String, Integer> extensions, boolean all){
        System.out.println(all ? "\n=======EXTENSION REPORT=======\n" : "\n=======EXTENSION REPORT (TOP 10)=======\n");
        for(Map.Entry<String, Integer> entry : extensions.entrySet()){
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }

    /*
     * Prints the category report — shows each file category and how much
     * total disk space all files in that category occupy combined.
     * Sizes are formatted as human-readable strings (KB, MB, GB) via SizeFormat.
     */
    public void printCategoryReport(HashMap<String, Long> categoryMap){
        System.out.println("\n=======CATEGORY REPORT=======\n");
        for(Map.Entry<String, Long> entry : categoryMap.entrySet()){
            System.out.println(entry.getKey() + "->" + SizeFormat.format(entry.getValue()));
        }
    }

    /*
     * Prints the top 5 largest files found during the scan.
     * Each line shows the filename and its size in human-readable format.
     */
    public void printLargestFiles(List<FileInfo> files){
        System.out.println("\n=======LARGEST FILES=======\n");
        for(FileInfo item : files){
            System.out.println(item.getName() + " -> " + SizeFormat.format(item.getSize()));
        }
    }

    /*
     * Prints the duplicate file summary — shows how many duplicate groups were found,
     * the total space they occupy, and how much space could be freed by deleting extras.
     *
     * The full file-by-file listing is intentionally omitted from console output.
     * On large drives with many duplicates, listing every file would flood the terminal
     * and push the summary off screen. The complete listing is available in the .txt report.
     */
    public void printDuplicateFiles(ReportData data){
        System.out.println("\n=======Duplicate Files========\n");
        System.out.println("Duplicate Groups : " + data.getDuplicateGroups().size());
        System.out.println("Total Duplicate Size : " + SizeFormat.format(data.getTotalDuplicateSize()));
        System.out.println("Wasted Space : " + SizeFormat.format(data.getWastedSpace()));
    }

    /*
     * The main entry point called by AnalysisRunner.
     * Always prints the summary first, then conditionally prints each analysis section
     * only if the corresponding data is present (non-null) in ReportData.
     *
     * For the extension section: if --all-extensions was passed, we print the full
     * extensionCount map directly. Otherwise we convert the pre-sorted topExtensions list
     * into a LinkedHashMap to preserve the ranking order during iteration, then print that.
     */
    @Override
    public void generateReport(ReportData data) {
        printSummary(data);

        if(data.getCategorySize() != null){
            printCategoryReport(data.getCategorySize());
        }

        if(data.getTopExtensions() != null){
            if(data.isAllExtensions()){
                // Print the full map — no sorting needed, user wants everything.
                printExtensionReport(data.getExtensionCount(), true);
            } else {
                // Convert the sorted List<Map.Entry> to a LinkedHashMap to preserve
                // the top-10 ranking order when iterating in printExtensionReport.
                Map<String, Integer> top = new LinkedHashMap<>();
                for(Map.Entry<String, Integer> entry : data.getTopExtensions()){
                    top.put(entry.getKey(), entry.getValue());
                }
                printExtensionReport(top, false);
            }
        }

        if(data.getTopFiles() != null){
            printLargestFiles(data.getTopFiles());
        }

        if(data.getDuplicateGroups() != null){
            printDuplicateFiles(data);
        }
    }
}