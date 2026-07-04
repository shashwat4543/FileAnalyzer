package analyzer;

import model.FileInfo;
import util.FileCategory;

import java.util.ArrayList;
import java.util.HashMap;

/*
 * CategoryAnalyzer groups files into broad categories (Images, Documents, Video, etc.)
 * and calculates how much total disk space each category occupies.
 *
 * It doesn't decide which category a file belongs to — that logic lives in FileCategory.
 * CategoryAnalyzer just loops through every file, asks FileCategory what category it is,
 * and adds the file's size to that category's running total.
 *
 * The result is a map like:
 *   "Images"      → 2,450,000,000 bytes
 *   "Documents"   → 340,000,000 bytes
 *   "Video"       → 15,200,000,000 bytes
 *   "Others"      → 890,000,000 bytes
 *
 * This is useful for understanding at a glance what types of files are taking up the most space.
 */
public class CategoryAnalyzer {

    // Maps category name → total size in bytes for all files in that category.
    private final HashMap<String, Long> categorySize = new HashMap<>();

    /*
     * Loops through every file and adds its size to the appropriate category total.
     *
     * getOrDefault(category, 0L) returns the current total for that category,
     * or 0 if this is the first file in that category. We then add the file's size
     * and store the updated total back in the map.
     */
    public void analyze(ArrayList<FileInfo> files){
        for(FileInfo file : files){
            String category = FileCategory.getFileCategory(file.getExtension());
            categorySize.put(category, categorySize.getOrDefault(category, 0L) + file.getSize());
        }
    }

    public HashMap<String, Long> getCategorySize(){
        return categorySize;
    }
}