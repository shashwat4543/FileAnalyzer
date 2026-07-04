package analyzer;

import model.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;

/*
 * LargestFileAnalyzer finds the single largest file and the top 5 largest files
 * across the entire scanned directory.
 *
 * Finding the largest file is straightforward — just loop through and track the biggest.
 *
 * Finding the top 5 uses a min-heap (PriorityQueue) rather than sorting the entire list.
 * Here's why that matters:
 *
 *   Sorting approach: sort all N files by size, take the first 5. Cost: O(N log N).
 *   Heap approach: maintain a heap of at most 5 entries. Cost: O(N log 5) ≈ O(N).
 *
 * On a directory with 100,000 files, sorting all of them just to find 5 is wasteful.
 * The heap approach processes every file exactly once and keeps memory usage constant
 * at 5 entries regardless of how many files there are.
 */
public class LargestFileAnalyzer {
    private FileInfo largestFile;
    private ArrayList<FileInfo> topFiles;

    /*
     * Finds the largest file and the top 5 largest files in the given list.
     *
     * largestFile starts as the first file in the list (or null if the list is empty)
     * and gets replaced whenever a bigger file is found. After the loop it holds
     * the single biggest file seen.
     *
     * findFiveLargestFile is called separately to get the top 5 using the heap approach.
     */
    public void analyze(ArrayList<FileInfo> files){
        largestFile = files.isEmpty() ? null : files.get(0);
        for(FileInfo file : files){
            if(file.getSize() > largestFile.getSize()){
                largestFile = file;
            }
        }
        topFiles = findFiveLargestFile(files);
    }

    /*
     * Finds the 5 largest files using a min-heap (smallest-on-top priority queue).
     *
     * The idea: keep a heap that holds at most 5 files at a time, always with
     * the smallest of the 5 sitting at the top.
     *
     *   For each file:
     *     1. Add it to the heap.
     *     2. If the heap now has more than 5 entries, remove the smallest one (the top).
     *
     * After processing all files, the heap contains exactly the 5 largest files —
     * because every time a smaller file was pushed in and made the heap exceed 5,
     * the smallest was removed, leaving only the largest ones behind.
     *
     * Finally, we drain the heap into a list and reverse it so the largest file
     * comes first (heap drains smallest-first by default).
     *
     * Example with 8 files of sizes [10, 3, 7, 15, 2, 9, 4, 6]:
     *   After processing all: heap contains [6, 7, 9, 10, 15]
     *   After reversing:      result is     [15, 10, 9, 7, 6]
     */
    private ArrayList<FileInfo> findFiveLargestFile(ArrayList<FileInfo> files){

        // Min-heap: the smallest file by size sits at the top and gets removed first.
        PriorityQueue<FileInfo> heap = new PriorityQueue<>(Comparator.comparingLong(FileInfo::getSize));

        for(FileInfo file : files){
            heap.offer(file);
            if(heap.size() > 5){
                // We've exceeded 5 entries — remove the smallest to keep only the top 5.
                heap.poll();
            }
        }

        // Drain the heap into a list. Heap drains in ascending order (smallest first).
        ArrayList<FileInfo> result = new ArrayList<>();
        while(!heap.isEmpty()){
            result.add(heap.poll());
        }

        // Reverse so the largest file is at index 0 — matches the order shown in reports.
        Collections.reverse(result);
        return result;
    }

    public FileInfo getLargestFile()      { return largestFile; }
    public ArrayList<FileInfo> getTopFiles() { return topFiles; }
}