package scanner;

import model.FileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/*
 * ZipScanner opens a ZIP archive and returns its contents as a list of FileInfo objects.
 *
 * This lets the rest of the app treat files inside ZIPs exactly the same as regular
 * files on disk — they show up in extension counts, category totals, largest-file lists,
 * and size calculations just like any other file.
 *
 * One important distinction: files inside a ZIP get a FileInfo created with the
 * archive constructor (no File object), which sets fromArchive = true. This flag
 * is checked by DuplicateFileAnalyzer to skip archive entries during hashing,
 * since you can't open and hash a file that only exists inside a ZIP.
 *
 * The path format used for archive entries is:
 *   C:\path\to\archive.zip!/folder/file.txt
 * The "!/" separator is the standard convention for referencing files inside archives,
 * borrowed from Java's JAR URL syntax.
 */
public class ZipScanner {

    /*
     * Opens the given ZIP file and returns a FileInfo for each file entry inside it.
     * Directory entries inside the ZIP are skipped — we only care about actual files.
     *
     * If the ZIP can't be opened (corrupted, not a real ZIP, permission denied),
     * the error is printed and an empty list is returned so the scan can continue.
     * A bad ZIP never stops the rest of the directory scan from completing.
     *
     * ZipFile is opened in a try-with-resources block so it's automatically closed
     * when we're done, even if an exception occurs midway through reading.
     */
    public static List<FileInfo> scan(File archiveFile){
        List<FileInfo> entries = new ArrayList<>();

        try(ZipFile zipFile = new ZipFile(archiveFile)){
            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

            while(zipEntries.hasMoreElements()){
                ZipEntry entry = zipEntries.nextElement();

                // Skip directory entries — they have no content, just a path.
                if(entry.isDirectory()) continue;

                String entryName = entry.getName();

                // entryName is the full path inside the ZIP, e.g. "folder/subfolder/file.txt".
                // We extract just the filename (the part after the last "/") for display purposes.
                // If there's no slash, the file is at the root of the ZIP and entryName is already the filename.
                int lastSlash = entryName.lastIndexOf('/');
                String name = lastSlash == -1 ? entryName : entryName.substring(lastSlash + 1);

                // Build a virtual path using "!/" to show where inside the archive this file lives.
                // Example: "C:\Downloads\archive.zip!/docs/readme.txt"
                String path = archiveFile.getPath() + "!/" + entryName;

                long size = entry.getSize();
                String extension = extractExtension(name);

                // Use the archive constructor — no File object since this file only
                // exists inside the ZIP, not as a standalone file on disk.
                entries.add(new FileInfo(name, path, size, extension));
            }
        } catch (Exception e) {
            // Print a notice but don't crash — corrupted or unreadable ZIPs are common
            // (e.g. files in the Recycle Bin that happen to have a .zip extension).
            System.out.println("\nCould not read archive: " + archiveFile.getPath());
        }

        return entries;
    }

    /*
     * Extracts the file extension from a filename — same logic as DirectoryScanner.
     * Duplicated here so ZipScanner stays self-contained and independent.
     */
    private static String extractExtension(String filename){
        int dot = filename.lastIndexOf('.');
        if(dot == -1) return "";
        return filename.substring(dot + 1).toLowerCase();
    }
}