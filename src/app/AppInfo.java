package app;

/*
 * AppInfo holds the app's identity constants in one place.
 *
 * Keeping these here means if you ever need to update the version number,
 * app name, or author, you change it in exactly one file and it automatically
 * reflects everywhere — currently used in CliParser's --version output
 * and InteractiveShell's welcome banner.
 */
public class AppInfo {
    public static final String NAME = "File Analyzer";
    public static final String VERSION = "1.0.0";
    public static final String JAVA_VERSION = "17";
    public static final String AUTHOR = "Shashwat";
}