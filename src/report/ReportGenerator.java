package report;

/*
 * ReportGenerator is the common interface that all report classes implement.
 *
 * There are three report classes: ConsoleReport, TextReport, and CsvReport.
 * Each one produces output in a different format but they all do the same
 * fundamental thing — take a ReportData object and turn it into a report.
 *
 * By making them all implement this interface, the rest of the app can treat
 * them interchangeably. For example, if you ever wanted to add a new report
 * format (e.g. HTML, JSON, XML), you'd just create a new class that implements
 * this interface and add it to AnalysisRunner — nothing else needs to change.
 *
 * Currently AnalysisRunner calls each report class directly rather than
 * through this interface, but the interface still serves as a contract:
 * it guarantees every report class has a generateReport method and accepts
 * a ReportData, making the design consistent and easy to extend.
 */
public interface ReportGenerator {

    /*
     * Generates a report from the given analysis results.
     * What "generate" means depends on the implementation:
     *   - ConsoleReport prints to the terminal.
     *   - TextReport writes a .txt file to disk.
     *   - CsvReport writes a .csv file to disk.
     */
    void generateReport(ReportData data);
}