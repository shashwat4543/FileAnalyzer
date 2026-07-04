package scanner;

import model.FileInfo;
import util.BenchmarkManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/*
 * DirectoryScanner walks an entire directory tree and collects information
 * about every file it finds — name, path, size, and extension.
 *
 * How the traversal works:
 *   Instead of using recursion (which can crash with a StackOverflowError on very
 *   deep folder structures), it uses an explicit Stack. It starts by pushing the root
 *   directory onto the stack, then repeatedly pops a directory, lists its contents,
 *   and pushes any subdirectories it finds back onto the stack. This continues until
 *   the stack is empty, meaning every folder in the entire tree has been visited.
 *
 * ZIP files:
 *   When a ZIP file is encountered, it's handed off to ZipScanner which opens it and
 *   returns its contents as individual FileInfo entries. Those entries are added to the
 *   same files list as regular files, so the rest of the app treats them identically.
 *   ZIP scanning time is tracked separately from regular scanning for the --benchmark output.
 *
 * Skipped files:
 *   Files and folders that can't be read (due to permissions) are counted and their
 *   paths are recorded, but the scan continues — one unreadable folder never stops
 *   the rest of the scan from completing.
 */
public class DirectoryScanner {

    // Only ZIP archives are supported for scanning right now.
    // To add support for more archive types (e.g. RAR, 7z), add their extensions
    // here and create a corresponding scanner class for them.
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of("zip");

    private int skippedFiles;
    private ArrayList<String> skippedPaths = new ArrayList<>();
    private int totalFiles;
    private int totalFolders;
    private long totalSize;
    private ArrayList<FileInfo> files = new ArrayList<>();
    private final ProgressReporter progressReporter = new ProgressReporter();

    // Tracks time spent specifically on ZIP scanning so it can be reported
    // separately from regular directory scanning in the benchmark table.
    private long zipScanNanos = 0;

    /*
     * Walks the entire directory tree rooted at 'root' and populates the files list.
     *
     * For each item found:
     *   - Directory  → check if readable, then push onto the stack to visit later.
     *   - ZIP file   → open with ZipScanner and add all its entries to the files list.
     *   - Other file → wrap in a FileInfo and add to the files list.
     *   - Unreadable → count as skipped, record the path, move on.
     *
     * After every item, the progress reporter is updated so the terminal shows
     * a live count of files, folders, and total size as the scan progresses.
     */
    public void scan(File root){
        long scanStart = System.nanoTime();

        Stack<File> stack = new Stack<>();
        stack.push(root);

        while(!stack.isEmpty()){
            File current = stack.pop();
            File[] contents = current.listFiles();

            // listFiles() returns null (not an empty array) when the OS refuses
            // to list a directory's contents due to permission restrictions.
            // We treat this the same as a skipped file and keep going.
            if(contents == null) {
                skippedFiles++;
                skippedPaths.add(current.getAbsolutePath());
                continue;
            }

            for(File item : contents){
                if(item.isDirectory()){
                    if(!item.canRead()){
                        skippedFiles++;
                        skippedPaths.add(item.getAbsolutePath());
                        continue;
                    }
                    totalFolders++;
                    stack.push(item);
                }
                else{
                    if(!item.canRead()){
                        skippedFiles++;
                        skippedPaths.add(item.getAbsolutePath());
                        continue;
                    }

                    String name = item.getName();
                    String path = item.getPath();
                    long size = item.length();
                    String extension = extractExtension(name);

                    if(ARCHIVE_EXTENSIONS.contains(extension)){
                        // Time ZIP scanning separately so it shows as its own
                        // entry in the benchmark table, distinct from directory scanning.
                        long zipStart = System.nanoTime();
                        List<FileInfo> archiveEntries = ZipScanner.scan(item);
                        zipScanNanos += System.nanoTime() - zipStart;

                        // We add the sizes of the ZIP's individual entries to totalSize,
                        // not the size of the ZIP file itself. This gives a more accurate
                        // picture of how much actual content is inside.
                        for(FileInfo entryInfo : archiveEntries){
                            totalFiles++;
                            totalSize += entryInfo.getSize();
                            files.add(entryInfo);
                        }
                    } else {
                        totalFiles++;
                        totalSize += size;
                        files.add(new FileInfo(item, name, path, size, extension));
                    }
                }
                progressReporter.update(totalFiles, totalFolders, totalSize, item.getPath());
            }
        }
        progressReporter.finish();

        // Subtract ZIP scanning time from the total so the two durations are
        // reported as separate, non-overlapping entries in the benchmark table.
        long totalScanNanos = System.nanoTime() - scanStart;
        BenchmarkManager.record("Directory Scan", totalScanNanos - zipScanNanos);
        BenchmarkManager.record("ZIP Scan", zipScanNanos);
    }

    /*
     * Extracts the file extension from a filename by finding the last dot.
     * Returns an empty string if there's no dot (no extension).
     * Always returns lowercase so extensions are grouped consistently
     * regardless of how they're capitalised on disk.
     *
     * Examples:
     *   "report.PDF"     → "pdf"
     *   "archive.tar.gz" → "gz"   (only the final extension)
     *   "Makefile"       → ""     (no extension)
     */
    private String extractExtension(String filename){
        int dot = filename.lastIndexOf('.');
        if(dot == -1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }

    public int getTotalFiles(){
        return totalFiles;
    }
    public ArrayList<String> getSkippedPaths() {
        return skippedPaths;
    }
    public int getSkippedFiles(){
        return skippedFiles;
    }
    public int getTotalFolders() {
        return totalFolders;
    }
    public long getTotalSize() {
        return totalSize;
    }
    public ArrayList<FileInfo> getFiles() {
        return files;
    }
}