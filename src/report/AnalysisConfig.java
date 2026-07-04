package report;

import util.ReportNameGenerator;

/*
 * AnalysisConfig holds every choice the user made on the command line,
 * translated into a set of simple boolean flags and settings.
 *
 * CliParser creates and populates this object after reading the user's flags.
 * It's then passed to AnalysisRunner → ReportFactory, which reads it to decide
 * which analyzers to run and which report files to write.
 *
 * Think of it as a checklist:
 *   - Which analyzers should run?       (extensionAnalysis, categoryAnalysis, etc.)
 *   - Which report files should be written?  (txtReport, csvReport)
 *   - Any special display options?      (allExtensions, benchmark)
 *   - Where should report files go?     (outputDirectory)
 *
 * All booleans default to false. CliParser only sets them to true when the
 * corresponding flag is present. This means an unconfigured AnalysisConfig
 * runs nothing — which is safe, nothing happens by accident.
 */
public class AnalysisConfig {

    // --- Analyzer toggles ---
    // Each flag controls whether the corresponding analyzer runs during analysis.
    private boolean extensionAnalysis;   // Run ExtensionAnalyzer  (--extensions or --full)
    private boolean categoryAnalysis;    // Run CategoryAnalyzer   (--categories or --full)
    private boolean largestFiles;        // Run LargestFileAnalyzer (--largest or --full)
    private boolean duplicateDetection;  // Run DuplicateFileAnalyzer (--duplicates or --full)

    // --- Report output toggles ---
    // Controls which report files are written after the analysis completes.
    private boolean txtReport;   // Write a .txt report  (--txt or --full)
    private boolean csvReport;   // Write a .csv report  (--csv or --full)

    // --- Display and behavior options ---
    private boolean benchmark;       // Print timing for each stage after running (--benchmark)
    private boolean allExtensions;   // Show all extensions instead of just top 10 (--all-extensions)

    // --- Output location ---
    // Defaults to ~/FileAnalyzer/reports so reports always have somewhere to go
    // even if the user never specifies --output.
    private String outputDirectory = ReportNameGenerator.defaultOutputDirectory();

    public boolean isExtensionAnalysis(){
        return extensionAnalysis;
    }
    public void setExtensionAnalysis(boolean extensionAnalysis) {
        this.extensionAnalysis = extensionAnalysis;
    }

    public boolean isCategoryAnalysis() {
        return categoryAnalysis;
    }
    public void setCategoryAnalysis(boolean categoryAnalysis) {
        this.categoryAnalysis = categoryAnalysis;
    }

    public boolean isLargestFiles() {
        return largestFiles;
    }
    public void setLargestFiles(boolean largestFiles){
        this.largestFiles = largestFiles;
    }

    public boolean isDuplicateDetection() {
        return duplicateDetection;
    }
    public void setDuplicateDetection(boolean duplicateDetection)
    { this.duplicateDetection = duplicateDetection;
    }

    public boolean isTxtReport() {
        return txtReport;
    }
    public void setTxtReport(boolean txtReport){
        this.txtReport = txtReport;
    }

    public boolean isCsvReport(){
        return csvReport;
    }
    public void setCsvReport(boolean csvReport){
        this.csvReport = csvReport;
    }

    public boolean isBenchmark() {
        return benchmark;
    }
    public void setBenchmark(boolean benchmark) {
        this.benchmark = benchmark;
    }

    public boolean isAllExtensions() {
        return allExtensions;
    }
    public void setAllExtensions(boolean allExtensions) {
        this.allExtensions = allExtensions;
    }

    public String getOutputDirectory(){
        return outputDirectory;
    }
    public void setOutputDirectory(String outputDirectory){
        this.outputDirectory = outputDirectory;
    }
}