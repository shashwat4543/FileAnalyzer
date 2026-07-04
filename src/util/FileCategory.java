package util;

import java.util.Set;

/*
 * FileCategory maps a file extension to a broad human-readable category.
 *
 * Rather than showing users a raw list of hundreds of extensions, CategoryAnalyzer
 * uses this class to group them into meaningful buckets like "Images", "Video",
 * "Documents", etc. This gives a quick overview of what types of content are
 * taking up space without overwhelming the user with details.
 *
 * Each category is defined as a Set of extensions. Set.of() creates an immutable
 * set, which is ideal here — these mappings never change at runtime, and Set
 * lookup (contains()) is O(1) regardless of how many extensions are in the set.
 *
 * To add a new extension to an existing category, just add it to the relevant Set.
 * To add a new category entirely, create a new Set and add a new if-block in
 * getFileCategory() before the final "Others" return.
 *
 * Files that don't match any known category fall into "Others".
 * Files with no extension at all get "No extension".
 */
public class FileCategory {

    private static final Set<String> IMAGES    = Set.of("png","jpg","gif","bmp","webp","jpeg");
    private static final Set<String> DOCUMENTS = Set.of("txt","pdf","doc","docx","ppt","pptx");
    private static final Set<String> AUDIO     = Set.of("mp3","wav","aac");
    private static final Set<String> VIDEO     = Set.of("mp4","mkv","avi","mov");
    private static final Set<String> ARCHIVE   = Set.of("zip","rar","7z","tar","gz");
    private static final Set<String> CODE      = Set.of("java","py","cpp","c","js","html","css","go");

    /*
     * Returns the category name for the given file extension.
     *
     * The extension passed in should already be lowercase (DirectoryScanner and
     * ZipScanner both lowercase extensions before storing them), so no conversion
     * is needed here.
     *
     * The checks are ordered from most to least common — Images and Documents first,
     * Others last as a catch-all. The order doesn't affect correctness (each category's
     * extensions are unique) but it's a minor efficiency consideration.
     *
     * Returns:
     *   "No extension" — if the extension is null or empty
     *   "Images"       — for png, jpg, gif, bmp, webp, jpeg
     *   "Documents"    — for txt, pdf, doc, docx, ppt, pptx
     *   "Archive"      — for zip, rar, 7z, tar, gz
     *   "Audio"        — for mp3, wav, aac
     *   "Video"        — for mp4, mkv, avi, mov
     *   "Source Code"  — for java, py, cpp, c, js, html, css, go
     *   "Others"       — for anything not in the above categories
     */
    public static String getFileCategory(String extension){
        if(extension == null || extension.isEmpty()){
            return "No extension";
        }
        if(IMAGES.contains(extension))    return "Images";
        if(DOCUMENTS.contains(extension)) return "Documents";
        if(ARCHIVE.contains(extension))   return "Archive";
        if(AUDIO.contains(extension))     return "Audio";
        if(VIDEO.contains(extension))     return "Video";
        if(CODE.contains(extension))      return "Source Code";
        return "Others";
    }
}