package app;

/*
 * CliParseException is a custom error that gets thrown when the user types
 * something invalid — like a wrong flag, a missing path, or conflicting options.
 *
 * Why do we need a custom exception instead of just printing an error and calling System.exit()?
 *
 * In one-shot CLI mode (e.g. "FileAnalyzer C:\Docs --badFlag"), calling System.exit() is fine
 * because the app is done anyway — there's nothing else to do.
 *
 * But in interactive shell mode, System.exit() would close the entire window, which is
 * terrible UX. If the user just mistyped a flag, we want to print the error and loop back
 * to the prompt so they can try again — not kill the app entirely.
 *
 * By throwing this exception instead of calling System.exit(), we let the caller decide
 * what to do with the error:
 *   - CliParser.parsePath() / parseConfig() (used by Main) catches it and calls System.exit().
 *   - InteractiveShell catches it, prints the message, and loops back to the prompt.
 *
 * The 'showHelp' flag tells the caller whether to also print the full help text alongside
 * the error message. For example, an unknown flag warrants showing help, but "directory
 * does not exist" doesn't — the user just needs to fix the path.
 */
public class CliParseException extends RuntimeException {

    // If true, the caller should print the help text after showing the error message.
    public final boolean showHelp;

    public CliParseException(String message, boolean showHelp) {
        super(message);
        this.showHelp = showHelp;
    }
}