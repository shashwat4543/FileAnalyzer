package analyzer;

import model.FileInfo;
import scanner.ProgressReporter;
import util.HashExclusion;
import util.HashUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/*
 * DuplicateFileAnalyzer finds files that are exact copies of each other
 * and calculates how much disk space those duplicates are wasting.
 *
 * Finding duplicates by comparing every file to every other file would be
 * incredibly slow — comparing 100,000 files that way means 5 billion comparisons.
 * Instead, we use a three-tier funnel that eliminates non-duplicates as cheaply
 * as possible at each stage, so only a tiny fraction of files ever reach the
 * expensive full-hash stage:
 *
 *   TIER 1 — Size filter:
 *     Two files can only be duplicates if they're the exact same size.
 *     Group all files by size. Any group with only one file is immediately
 *     eliminated — no comparison needed. This alone removes the vast majority
 *     of files from consideration.
 *
 *   TIER 2 — Quick hash (first 8KB):
 *     For files that share a size, read just the first 8KB of each and hash it.
 *     Files that differ in the first 8KB are definitely not duplicates and get
 *     eliminated here without reading the rest of the file. This is the biggest
 *     speed win for large files (videos, archives, disk images) where reading
 *     the full file would be expensive.
 *
 *   TIER 3 — Full SHA-256 hash:
 *     Only files that matched on both size AND quick hash reach this stage.
 *     We read the entire file and compute a full SHA-256 hash. Files with
 *     matching full hashes are confirmed duplicates.
 *
 * Both hashing stages run in parallel using a 4-thread pool to take advantage
 * of SSDs that can handle concurrent reads efficiently.
 *
 * Files inside ZIP archives and files in system directories (Windows, node_modules, etc.)
 * are excluded from duplicate detection — you can't open an archive entry directly,
 * and system files are rarely the source of wasted duplicate space.
 */
public class DuplicateFileAnalyzer {

    // Number of threads used for parallel hashing.
    // 4 threads is a good balance — enough to saturate an SSD without overwhelming
    // the system or running into too much thread overhead.
    private static final int THREAD_COUNT = 4;

    // Maps SHA-256 hash → list of files that all share that hash (confirmed duplicates).
    private final HashMap<String, List<FileInfo>> duplicateGroups = new HashMap<>();

    // Total bytes across all files in all duplicate groups (including one "original" per group).
    private long duplicateSize = 0;

    // Bytes that could be freed by keeping one copy of each duplicate group and deleting the rest.
    private long wastedSpace = 0;

    /*
     * A simple container that pairs a FileInfo with the hash computed for it.
     * Used to carry results back from the parallel hashing threads to the
     * single-threaded grouping logic that runs after all threads finish.
     *
     * Keeping hashing parallel but grouping single-threaded avoids any
     * concurrent modification of the HashMaps used for grouping.
     */
    private static class HashedFile {
        final FileInfo fileInfo;
        final String hash;
        HashedFile(FileInfo fileInfo, String hash){
            this.fileInfo = fileInfo;
            this.hash = hash;
        }
    }

    /*
     * Runs the full three-tier duplicate detection on the given file list.
     *
     * Results are stored in the instance fields (duplicateGroups, duplicateSize, wastedSpace)
     * and retrieved via the getters below. The fields are cleared at the start so this
     * method can safely be called more than once on the same instance.
     */
    public void analyze(ArrayList<FileInfo> files) {

        duplicateGroups.clear();
        duplicateSize = 0;
        wastedSpace = 0;

        // TIER 1: Group all files by size.
        // Only groups with 2 or more files can possibly contain duplicates.
        HashMap<Long, List<FileInfo>> sizeGroups = new HashMap<>();
        for (FileInfo file : files) {
            sizeGroups.computeIfAbsent(file.getSize(), k -> new ArrayList<>()).add(file);
        }

        // Build the candidate list: files that passed the size filter
        // and are eligible for hashing (not from an archive, not hidden,
        // not in an excluded system directory).
        List<FileInfo> candidates = new ArrayList<>();
        for (List<FileInfo> sizeGroup : sizeGroups.values()) {
            if (sizeGroup.size() < 2)
                continue;

            for (FileInfo fileInfo : sizeGroup) {
                if (fileInfo.isFromArchive())
                    continue;  // Can't open files that live inside a ZIP.
                if (fileInfo.getFile().isHidden())
                    continue;  // Skip hidden files — usually system or temp files.
                if (HashExclusion.isExcluded(fileInfo.getFile()))
                    continue;  // Skip files in excluded directories (Windows, node_modules, etc.)
                candidates.add(fileInfo);
            }
        }

        // If no candidates survived the filters, there's nothing to hash.
        if (candidates.isEmpty())
            return;

        ProgressReporter progressReporter = new ProgressReporter();

        // TIER 2: Quick hash — read only the first 8KB of each candidate file.
        // Hashing all candidates in parallel using the shared thread pool.
        List<HashedFile> quickHashed = computeHashes(
                candidates, candidates.size(), progressReporter,
                fileInfo -> HashUtil.getQuickHash(fileInfo.getFile()));

        // Group quick-hash results by size first, then by quick hash.
        // We need the size grouping here because two files could coincidentally
        // have the same quick hash but different sizes — they're not duplicates.
        HashMap<Long, HashMap<String, List<FileInfo>>> quickGroupsBySize = new HashMap<>();
        for (HashedFile hashedFile : quickHashed) {
            if (hashedFile.hash == null)
                continue;  // Hash failed (e.g. file was deleted mid-scan) — skip it.
            quickGroupsBySize
                    .computeIfAbsent(hashedFile.fileInfo.getSize(), k -> new HashMap<>())
                    .computeIfAbsent(hashedFile.hash, k -> new ArrayList<>())
                    .add(hashedFile.fileInfo);
        }

        // Only files that matched another file on BOTH size AND quick hash
        // move forward to the expensive full-hash stage.
        List<FileInfo> fullHashCandidates = new ArrayList<>();
        for (HashMap<String, List<FileInfo>> quickGroups : quickGroupsBySize.values()) {
            for (List<FileInfo> quickGroup : quickGroups.values()) {
                if (quickGroup.size() >= 2) {
                    fullHashCandidates.addAll(quickGroup);
                }
            }
        }

        // TIER 3: Full SHA-256 hash — read the entire file.
        // Only runs on the small subset that survived both the size and quick-hash filters.
        List<HashedFile> fullHashed = computeHashes(
                fullHashCandidates, fullHashCandidates.size(), progressReporter,
                fileInfo -> HashUtil.getSHA256(fileInfo.getFile()));

        // Group full-hash results by hash.
        // Files with matching full hashes are confirmed exact duplicates.
        HashMap<String, List<FileInfo>> hashGroups = new HashMap<>();
        for (HashedFile hashedFile : fullHashed) {
            if (hashedFile.hash == null)
                continue;
            hashGroups.computeIfAbsent(hashedFile.hash, k -> new ArrayList<>()).add(hashedFile.fileInfo);
        }

        // Build the final duplicate groups and calculate size stats.
        for (Map.Entry<String, List<FileInfo>> entry : hashGroups.entrySet()) {
            List<FileInfo> group = entry.getValue();

            if (group.size() < 2)
                continue;  // Only one file with this hash — not a duplicate.

            duplicateGroups.put(entry.getKey(), group);

            long fileSize = group.get(0).getSize();

            // duplicateSize = total space occupied by ALL copies in the group.
            duplicateSize += fileSize * group.size();

            // wastedSpace = space that could be freed by keeping just one copy.
            // e.g. 3 identical 10MB files → 10MB kept, 20MB wasted.
            wastedSpace += fileSize * (group.size() - 1);
        }

        progressReporter.finish("Duplicate detection completed");
    }

    /*
     * Hashes a list of files in parallel using a fixed thread pool and returns
     * a list of HashedFile results — one per input file, in completion order.
     *
     * Each file is submitted as a separate Callable task. All tasks run concurrently
     * across THREAD_COUNT threads. We wait for every task to finish (future.get())
     * before returning, so the caller always gets complete results.
     *
     * The hashFunction parameter is a lambda that tells this method whether to
     * use getQuickHash or getSHA256 — so the same parallel infrastructure is
     * reused for both hashing stages without duplicating any threading code.
     *
     * AtomicLong is used for processedCount because multiple threads increment
     * it at the same time. Regular int/long isn't thread-safe for concurrent
     * increments — two threads could read the same value, both add 1, and write
     * back the same result, effectively losing one increment. AtomicLong's
     * incrementAndGet() does the read-add-write as a single uninterruptible operation.
     */
    private List<HashedFile> computeHashes(List<FileInfo> files, long total,
                                           ProgressReporter progressReporter,
                                           HashFunction hashFunction) {
        if (files.isEmpty())
            return new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<HashedFile>> futures = new ArrayList<>();
        AtomicLong processedCount = new AtomicLong(0);

        for (FileInfo fileInfo : files) {
            Callable<HashedFile> task = () -> {
                String hash = hashFunction.apply(fileInfo);
                long processed = processedCount.incrementAndGet();
                progressReporter.updateHashing(processed, total, fileInfo.getPath());
                return new HashedFile(fileInfo, hash);
            };
            futures.add(executor.submit(task));
        }

        // Wait for every task to finish before returning.
        // future.get() blocks until that specific task is done.
        List<HashedFile> results = new ArrayList<>();
        try {
            for (Future<HashedFile> future : futures) {
                results.add(future.get());
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        executor.shutdown();

        return results;
    }

    /*
     * A simple functional interface (single-method interface) that represents
     * "a function that takes a FileInfo and returns a hash string".
     *
     * This lets us pass either HashUtil.getQuickHash or HashUtil.getSHA256
     * as a parameter to computeHashes(), so both hashing stages can reuse
     * the same parallel threading infrastructure with just a different hash function.
     *
     * The @FunctionalInterface annotation tells Java (and anyone reading the code)
     * that this interface is intentionally designed to be used as a lambda.
     */
    @FunctionalInterface
    private interface HashFunction {
        String apply(FileInfo fileInfo);
    }

    public long getWastedSpace() {
        return wastedSpace;
    }
    public long getDuplicateSize(){
        return duplicateSize;
    }
    public HashMap<String, List<FileInfo>> getDuplicateGroups() {
        return duplicateGroups;
    }
    public boolean hasDuplicates() {
        return !duplicateGroups.isEmpty();
    }
}