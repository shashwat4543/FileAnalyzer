# File Analyzer

A fast, cross-platform command-line tool that scans a directory and gives you a
complete picture of what's inside — file counts, sizes, extensions, categories,
largest files, and duplicate detection.

---

## Features

- Scans entire directory trees recursively, including files inside ZIP archives
- Counts files by extension and shows the top 10 most common
- Groups files into categories (Images, Documents, Video, Audio, Source Code, etc.) and shows space used per category
- Finds the 5 largest files
- Detects duplicate files using a fast three-tier algorithm (size → quick hash → full SHA-256)
- Live progress bar with spinner, file count, speed, elapsed time, and current path
- Writes detailed `.txt` and `.csv` reports with timestamps so repeated scans never overwrite each other
- Interactive shell mode when launched with no arguments — type scan commands without relaunching the app
- Benchmark mode to see how long each stage took
- No Java installation required on target machines — the installer bundles everything

---

📖 **[Full Documentation](DOCUMENTATION.md)** — architecture, flag details, analyzer internals, real-world behavior, and future developments.

---

## Installation

Download and run the installer directly:

**[⬇ Download FileAnalyzer-1.0.0.exe](https://github.com/shashwat4543/FileAnalyzer/releases/download/v1.0.0/FileAnalyzer-1.0.0.exe)**

The installer will place the app in `C:\Program Files\FileAnalyzer\`.
After installation, add it to your PATH:

1. Press `Windows + R` → type `sysdm.cpl` → Enter
2. `Advanced` → `Environment Variables`
3. Under **System variables** → find `Path` → `Edit` → `New`
4. Add `C:\Program Files\FileAnalyzer`
5. Click OK on all dialogs

Open a new terminal and you're ready to go.

---

## Usage

### One-shot mode (from any terminal)

```
FileAnalyzer <directory> [options]
```

**Examples:**

```bash
# Run a full analysis on your Documents folder
FileAnalyzer C:\Users\You\Documents

# Quick scan — extensions, categories, largest files (no duplicate detection)
FileAnalyzer C:\Users\You\Documents --quick

# Duplicate detection only
FileAnalyzer C:\Users\You\Documents --duplicates

# Full analysis and save reports to a custom folder
FileAnalyzer C:\Users\You\Documents --full --output C:\MyReports

# Show all extensions instead of just top 10
FileAnalyzer C:\Users\You\Documents --extensions --all-extensions

# Show how long each stage took
FileAnalyzer C:\Users\You\Documents --quick --benchmark
```

### Interactive mode

Launch `FileAnalyzer` with no arguments (or double-click the exe) to open the interactive shell:

```
File Analyzer v1.0.0 - interactive mode
Type a directory path (with optional flags) to scan it, 'help' for options, or 'exit' to quit.

FileAnalyzer> C:\Users\You\Documents --quick
FileAnalyzer> "C:\path with spaces\My Files" --full
FileAnalyzer> help
FileAnalyzer> exit
```

Paths with spaces must be wrapped in quotes. Both single (`'`) and double (`"`) quotes work.

---

## Options

| Flag | Description |
|---|---|
| `--quick` | Extension, category, and largest-file analysis. Skips duplicate detection. |
| `--full` | Runs every analyzer and writes both `.txt` and `.csv` reports. |
| `--duplicates` | Runs duplicate detection (can be combined with other flags). |
| `--extensions` | Runs extension analysis. |
| `--categories` | Runs category analysis. |
| `--largest` | Finds the 5 largest files. |
| `--all-extensions` | Shows all extensions instead of just the top 10. |
| `--txt` | Writes a `.txt` report. |
| `--csv` | Writes a `.csv` report. |
| `--output <dir>` | Directory where reports are saved. Default: `~/FileAnalyzer/reports`. |
| `--benchmark` | Prints a timing table after the scan showing how long each stage took. |
| `--version` | Shows version information. |
| `--help`, `-h` | Shows the help message. |

**Default behavior** (no flags): equivalent to `--full` — runs everything and writes both reports.

**Conflicting flags:**
- `--quick` and `--full` cannot be used together.
- `--quick` and `--duplicates` cannot be used together (`--quick` explicitly skips duplicate detection).

---

## Output

### Console

Always printed after every scan. Shows:
- **Summary** — total files, folders, size, and skipped items
- **Category Report** — space used per file category
- **Extension Report** — top 10 (or all) extensions by file count
- **Largest Files** — top 5 files by size
- **Duplicate Summary** — number of duplicate groups, total duplicate size, and wasted space

### Text Report (`.txt`)

Written when `--txt` or `--full` is passed. Contains everything the console shows,
plus the full path of every file in every duplicate group so you can find and delete them.

Saved to: `~/FileAnalyzer/reports/report_YYYY-MM-DD_HH-mm-ss.txt`

### CSV Report (`.csv`)

Written when `--csv` or `--full` is passed. Contains the category and extension data
in structured format for use in Excel, Google Sheets, or other data tools.

Saved to: `~/FileAnalyzer/reports/report_YYYY-MM-DD_HH-mm-ss.csv`

---

## How Duplicate Detection Works

Duplicate detection uses a three-tier funnel to stay fast even on large directories:

1. **Size filter** — Files can only be duplicates if they're the exact same size.
   Any file with a unique size is immediately eliminated. This removes the vast majority of files.

2. **Quick hash** — For same-size files, only the first 8KB is read and hashed.
   Files that differ in the first 8KB are ruled out without reading the rest.
   This is especially fast for large files like videos and archives.

3. **Full SHA-256 hash** — Only files that matched on both size and quick hash
   are fully hashed. Matching full hashes confirm exact duplicates.

Both hashing stages run in parallel across 4 threads for speed.

Files inside ZIP archives and files in system directories (`Windows`, `node_modules`,
`.git`, `Program Files`, etc.) are excluded from duplicate detection automatically.

---

## ZIP Archive Support

ZIP files are automatically opened during scanning. Files inside ZIPs are counted
and included in extension, category, and size analysis just like regular files.

Archive entries are excluded from duplicate detection since they can't be
opened directly on disk.

---

## Skipped Files

Files and folders that can't be read due to permission restrictions are counted
as "Skipped Files" in the summary and excluded from all analysis.
The scan always continues — one unreadable folder never stops the rest of the scan.

---

## Report Files

Reports are saved to `~/FileAnalyzer/reports/` by default.

On Windows this is `C:\Users\YourName\FileAnalyzer\reports\`.

Each report filename includes a timestamp (e.g. `report_2026-07-01_14-30-00.txt`)
so repeated scans on the same directory never overwrite previous results.

Use `--output <directory>` to save reports to a different location.

---

## For Developers

This section is only needed if you want to modify the source code or build the installer yourself. If you just want to use the app, download the exe above.

**Requirements:** Java 17+, IntelliJ IDEA (or any Java IDE)

### Build the jar

1. Clone the repo: `git clone https://github.com/shashwat4543/FileAnalyzer.git`
2. Open in IntelliJ IDEA.
3. `File` → `Project Structure` → `Artifacts` → `+` → `JAR` → `From modules with dependencies`
4. Set Main Class to `app.Main`, click OK → Apply.
5. `Build` → `Build Artifacts` → `Build`.
6. The jar appears in `out/artifacts/FileAnalyzer_jar/FileAnalyzer.jar`.

### Build the Windows installer

Requires JDK 17+ and WiX Toolset installed.

```cmd
"C:\Program Files\Java\jdk-17\bin\jpackage.exe" ^
  --input "out/artifacts/FileAnalyzer_jar" ^
  --name FileAnalyzer ^
  --main-jar FileAnalyzer.jar ^
  --main-class app.Main ^
  --type exe ^
  --app-version 1.0.0 ^
  --vendor "Shashwat" ^
  --win-console ^
  --win-menu ^
  --win-shortcut ^
  --dest "out/installer"
```

---

## Project Structure

```
src/
├── app/
│   ├── Main.java               Entry point — routes to CLI or interactive shell
│   ├── AnalysisRunner.java     Runs one full scan + analysis + report cycle
│   ├── InteractiveShell.java   Persistent prompt for interactive use
│   ├── CliParser.java          Parses and validates command-line arguments
│   ├── CliParseException.java  Custom exception for parse errors (shell-safe)
│   └── AppInfo.java            App name, version, author constants
├── scanner/
│   ├── DirectoryScanner.java   Walks the directory tree and collects files
│   ├── ZipScanner.java         Opens ZIP archives and returns their contents
│   └── ProgressReporter.java   Live terminal progress bar with ANSI colors
├── model/
│   └── FileInfo.java           Data object representing a single file
├── analyzer/
│   ├── CategoryAnalyzer.java      Groups files by category and totals their size
│   ├── ExtensionAnalyzer.java     Counts files by extension
│   ├── LargestFileAnalyzer.java   Finds the top 5 largest files (min-heap)
│   └── DuplicateFileAnalyzer.java Parallel duplicate detection with three-tier hashing
├── report/
│   ├── ReportGenerator.java    Interface implemented by all report classes
│   ├── ReportData.java         Holds all analysis results passed to report generators
│   ├── AnalysisConfig.java     Holds the user's flag choices
│   ├── ReportFactory.java      Runs analyzers in parallel and builds ReportData
│   ├── ConsoleReport.java      Prints results to the terminal
│   ├── TextReport.java         Writes a .txt report file
│   └── CsvReport.java          Writes a .csv report file
└── util/
    ├── HashUtil.java            SHA-256 and quick (8KB) hash computation
    ├── HashExclusion.java       Decides which directories to skip during hashing
    ├── FileCategory.java        Maps file extensions to category names
    ├── SizeFormat.java          Formats byte counts as KB/MB/GB strings
    ├── BenchmarkManager.java    Records and prints stage timing (--benchmark)
    └── ReportNameGenerator.java Generates timestamped report filenames
```

---

## Version

**1.0.0** — Built with Java 17. Author: Shashwat.
