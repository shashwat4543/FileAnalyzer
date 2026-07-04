package model;

import java.io.File;

/*
 * FileInfo holds everything the app knows about a single file.
 *
 * It's a simple data container — no logic, just fields and getters.
 * Every file the app encounters (whether on disk or inside a ZIP archive)
 * gets wrapped in one of these and added to the master files list that
 * all the analyzers work from.
 *
 * There are two constructors because files come from two different sources:
 *
 *   1. Regular files on disk:
 *      Created with a real File object so DuplicateFileAnalyzer can open and hash them.
 *      fromArchive is set to false.
 *
 *   2. Files inside a ZIP archive:
 *      Created without a File object (null) because the file only exists inside the ZIP,
 *      not as a standalone file on disk. fromArchive is set to true.
 *      DuplicateFileAnalyzer checks this flag and skips archive entries during hashing,
 *      since you can't open a file that only lives inside a ZIP.
 */
public class FileInfo {
    private String name;        // Just the filename, e.g. "report.pdf"
    private String path;        // Full path on disk, e.g. "C:\Docs\report.pdf"
    // For archive entries: "C:\archive.zip!/folder/report.pdf"
    private long size;          // File size in bytes
    private String extension;   // Lowercased extension without the dot, e.g. "pdf"
    private File file;          // The actual File object — null for archive entries
    private boolean fromArchive; // True if this file lives inside a ZIP, false if it's on disk

    /*
     * Constructor for regular files found on disk during directory scanning.
     * The File object is kept so DuplicateFileAnalyzer can read and hash the file later.
     */
    public FileInfo(File file, String name, String path, long size, String extension){
        this.file = file;
        this.name = name;
        this.path = path;
        this.size = size;
        this.extension = extension;
        this.fromArchive = false;
    }

    /*
     * Constructor for files found inside a ZIP archive.
     * No File object is provided because the file can't be accessed directly on disk.
     * fromArchive is set to true so the rest of the app knows not to try to open this file.
     */
    public FileInfo(String name, String path, long size, String extension){
        this.file = null;
        this.name = name;
        this.path = path;
        this.size = size;
        this.extension = extension;
        this.fromArchive = true;
    }

    public File getFile()           { return file; }
    public String getExtension()    { return extension; }
    public long getSize()           { return size; }
    public String getPath()         { return path; }
    public String getName()         { return name; }
    public boolean isFromArchive()  { return fromArchive; }
}