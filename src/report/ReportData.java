package report;

import model.FileInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * ReportData is the single container that holds every piece of information
 * produced by a scan and analysis cycle.
 *
 * After ReportFactory finishes running all the analyzers, it packs all their
 * results into one ReportData object. That object is then passed to every report
 * generator (ConsoleReport, TextReport, CsvReport) so they all read from the
 * same data — there's one source of truth and nothing gets computed twice.
 *
 * Fields that weren't requested by the user (because the corresponding analyzer
 * wasn't run) are left as null. Every report generator checks for null before
 * printing a section, so unused sections are simply skipped.
 *
 * For example, if the user ran with --quick (no duplicate detection):
 *   duplicateGroups → null  (skipped in all reports)
 *   wastedSpace     → 0
 *   topFiles        → populated (largest files were analyzed)
 */
public class ReportData {

    // --- Scan stats (always populated, regardless of which flags were used) ---
    private int totalFile;
    private int totalFolders;
    private long totalSize;
    private int skippedFiles;       // Files/folders that couldn't be read due to permissions.

    // --- Duplicate detection results (null if --duplicates was not requested) ---
    private HashMap<String, List<FileInfo>> duplicateGroups; // SHA-256 hash → list of identical files
    private long totalDuplicateSize; // Total bytes across all copies in all duplicate groups.
    private long wastedSpace;        // Bytes that could be freed by deleting extra copies.

    // --- Extension analysis results (null if --extensions was not requested) ---
    private HashMap<String, Integer> extensionCount; // Full map: every extension → file count.
    private List<Map.Entry<String, Integer>> topExtensions; // Pre-sorted top 10, ready for display.

    // --- Category analysis results (null if --categories was not requested) ---
    private HashMap<String, Long> categorySize; // Category name → total bytes in that category.

    // --- Largest file results (null if --largest was not requested) ---
    private List<FileInfo> topFiles; // Top 5 largest files, sorted largest first.

    // --- Report output settings ---
    private String outputDirectory;   // Where .txt and .csv reports are written.
    private String reportTimestamp;   // Timestamp used in the report filename (e.g. "2026-07-01_14-30-00").
    private boolean allExtensions;    // True if --all-extensions was passed; false shows only top 10.

    public void setSkippedFiles(int skippedFiles) {
        this.skippedFiles = skippedFiles;
    }
    public void setTopFiles(List<FileInfo> topFiles){
        this.topFiles = topFiles;
    }
    public void setCategorySize(HashMap<String, Long> categorySize) {
        this.categorySize = categorySize;
    }
    public void setExtensionCount(HashMap<String, Integer> extensionCount) {
        this.extensionCount = extensionCount;
    }
    public void setTopExtensions(List<Map.Entry<String,Integer>> topExtensions) {
        this.topExtensions = topExtensions;
    }
    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }
    public void setTotalFolders(int totalFolders) {
        this.totalFolders = totalFolders;
    }
    public void setTotalFile(int totalFile) {
        this.totalFile = totalFile;
    }
    public void setDuplicateGroups(HashMap<String, List<FileInfo>> duplicateGroups) {
        this.duplicateGroups = duplicateGroups;
    }
    public void setWastedSpace(long wastedSpace) {
        this.wastedSpace = wastedSpace;
    }
    public void setTotalDuplicateSize(long totalDuplicateSize) {
        this.totalDuplicateSize = totalDuplicateSize;
    }
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }
    public void setReportTimestamp(String reportTimestamp) {
        this.reportTimestamp = reportTimestamp;
    }
    public void setAllExtensions(boolean allExtensions){
        this.allExtensions = allExtensions;
    }

    public int getSkippedFiles() {
        return skippedFiles;
    }
    public List<FileInfo> getTopFiles(){
        return topFiles;
    }
    public HashMap<String, Long> getCategorySize(){
        return categorySize;
    }
    public HashMap<String, Integer> getExtensionCount(){
        return extensionCount;
    }
    public List<Map.Entry<String,Integer>> getTopExtensions() {
        return topExtensions;
    }
    public long getTotalSize(){
        return totalSize;
    }
    public int getTotalFolders() {
        return totalFolders;
    }
    public int getTotalFile() {
        return totalFile;
    }
    public HashMap<String, List<FileInfo>> getDuplicateGroups() {
        return duplicateGroups;
    }
    public long getWastedSpace() {
        return wastedSpace;
    }
    public long getTotalDuplicateSize(){
        return totalDuplicateSize;
    }
    public String getOutputDirectory() {
        return outputDirectory;
    }
    public String getReportTimestamp() {
        return reportTimestamp;
    }
    public boolean isAllExtensions(){
        return allExtensions;
    }
}