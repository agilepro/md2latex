package com.purplehillsbooks.md2latex;

import java.nio.file.Path;

/**
 * One thing wrong with a source file, located as precisely as we can manage.
 *
 * <p>An {@link Severity#ERROR} means the generated LaTeX would not compile, or
 * would silently lose content; the build stops. A {@link Severity#WARNING} is
 * reported but does not stop anything.
 *
 * @param severity   whether this stops the build
 * @param file       the Markdown file at fault
 * @param line       1-based line in the original file, or 0 when unknown
 * @param column     1-based column, or 0 when unknown
 * @param sourceLine the offending line's text, for an excerpt; may be null
 * @param message    what is wrong
 * @param hint       how to fix it; may be null
 */
public record Problem(
        Severity severity,
        Path file,
        int line,
        int column,
        String sourceLine,
        String message,
        String hint) {

    public enum Severity {
        ERROR, WARNING
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /**
     * Renders the problem the way a compiler would: location, message, an
     * excerpt of the offending line, and a caret under the column.
     */
    public String format() {
        StringBuilder b = new StringBuilder();
        b.append(file.getFileName());
        if (line > 0) {
            b.append(':').append(line);
            if (column > 0) {
                b.append(':').append(column);
            }
        }
        b.append(": ").append(severity == Severity.ERROR ? "error" : "warning")
         .append(": ").append(message);

        if (sourceLine != null && !sourceLine.isBlank()) {
            String excerpt = sourceLine.strip();
            int shift = sourceLine.length() - sourceLine.stripLeading().length();
            boolean truncated = false;
            if (excerpt.length() > 100) {
                excerpt = excerpt.substring(0, 100);
                truncated = true;
            }
            b.append("\n    ").append(excerpt).append(truncated ? " ..." : "");
            int caret = column - 1 - shift;
            if (column > 0 && caret >= 0 && caret <= excerpt.length()) {
                b.append("\n    ").append(" ".repeat(caret)).append('^');
            }
        }
        if (hint != null && !hint.isBlank()) {
            b.append("\n    hint: ").append(hint);
        }
        return b.toString();
    }
}
