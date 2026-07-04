package analyzer;

import model.FileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * ExtensionAnalyzer counts how many files exist for each file extension.
 *
 * The result is a map like:
 *   "jpg"          → 1,240
 *   "pdf"          → 87
 *   "mp4"          → 34
 *   "No extension" → 12
 *
 * It stores the complete count for every extension found, but also provides
 * a convenient method to get just the top 10 most common ones — which is what
 * the reports display by default to avoid flooding the output with hundreds of lines.
 * The full map is always available if the user passes --all-extensions.
 */
public class ExtensionAnalyzer {

    // Maps extension → number of files with that extension.
    // "No extension" is used as the key for files that have no dot in their name.
    private final HashMap<String, Integer> extensionCount = new HashMap<>();

    /*
     * Loops through every file and increments the count for its extension.
     *
     * Files with no extension (e.g. "Makefile", "LICENSE") are grouped under
     * the key "No extension" rather than an empty string, so they show up
     * clearly in the report instead of appearing as a blank label.
     */
    public void analyze(ArrayList<FileInfo> files){
        for(FileInfo file : files){
            String ext = file.getExtension();
            String key = (ext == null || ext.isEmpty()) ? "No extension" : ext;
            extensionCount.put(key, extensionCount.getOrDefault(key, 0) + 1);
        }
    }

    /*
     * Returns the complete extension count map — every extension found, with no limit.
     * Used when the user passes --all-extensions.
     */
    public HashMap<String, Integer> getExtensionCount() {
        return extensionCount;
    }

    /*
     * Returns only the 10 most common extensions, sorted from most to least frequent.
     *
     * How it works:
     *   1. Turn the map into a stream of key-value entries.
     *   2. Sort them by value (count) in descending order — highest count first.
     *   3. Take only the first 10.
     *   4. Collect them back into a List.
     *
     * We return a List of Map.Entry objects (rather than a new map) because a List
     * preserves the sorted order. A HashMap doesn't guarantee any order, so converting
     * back to a map would lose the ranking.
     *
     * This is what the reports display by default — showing all extensions on a large
     * drive could mean hundreds of lines, so top 10 keeps the output readable.
     */
    public List<Map.Entry<String, Integer>> getTopTenExtension(){
        return extensionCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toList());
    }
}