package util;

import java.io.File;
import java.util.Set;

/*
 * HashExclusion decides whether a file should be skipped during duplicate detection.
 *
 * Not every file on a system is worth scanning for duplicates. Certain directories
 * contain thousands of system files, build artifacts, or dependency files that are
 * intentionally identical across machines — flagging them as duplicates would be
 * noisy, misleading, and slow. We exclude entire directory trees to avoid this.
 *
 * Examples of why each directory is excluded:
 *   "windows"             — OS system files. Never duplicates worth deleting.
 *   "program files"       — Installed app files. Identical copies are expected and intentional.
 *   "program files (x86)" — Same as above, for 32-bit apps on 64-bit Windows.
 *   "programdata"         — App data managed by installers. Not user files.
 *   ".git"                — Git stores object files that look like duplicates but aren't.
 *   "node_modules"        — npm packages. Identical files across projects are normal and expected.
 *   "target"              — Java/Maven build output. Regenerated on every build.
 *   "bin"                 — Compiled binaries. Same as target.
 *
 * The check walks UP the directory tree from the file's location.
 * This means a file anywhere inside an excluded directory (even deeply nested)
 * will be correctly excluded — not just files directly inside the named folder.
 */
public class HashExclusion {

    // All directory names are stored lowercase so the comparison in isExcluded()
    // can be case-insensitive without needing to lowercase each entry individually.
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            "windows", "program files", "program files (x86)", "programdata",
            ".git", "node_modules", "target", "bin"
    );

    /*
     * Returns true if the given file lives inside any excluded directory,
     * anywhere up the directory tree.
     *
     * How it works:
     *   Start at the file's parent directory and walk upward toward the drive root.
     *   At each level, check if the directory name (lowercased) matches any entry
     *   in EXCLUDED_DIRECTORIES. If it does, return true immediately.
     *   If we reach the top of the tree (parent == null) without a match, return false.
     *
     * Example:
     *   File: "C:\Users\You\node_modules\lodash\array.js"
     *   Walk: "lodash" → not excluded
     *         "node_modules" → EXCLUDED → return true
     *
     *   File: "C:\Users\You\Documents\report.pdf"
     *   Walk: "Documents" → not excluded
     *         "You" → not excluded
     *         "Users" → not excluded
     *         "C:\" → parent is null → return false
     */
    public static boolean isExcluded(File file){
        File parent = file.getParentFile();
        while(parent != null){
            if(EXCLUDED_DIRECTORIES.contains(parent.getName().toLowerCase())){
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }
}