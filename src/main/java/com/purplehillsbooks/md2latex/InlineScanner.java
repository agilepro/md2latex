package com.purplehillsbooks.md2latex;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits one line of Markdown into the runs a {@link MarkuaSink} cares about, and reassembles
 * whatever the sink hands back.
 *
 * <p>Everything here exists because a line of Markdown is not uniformly prose. A sink that curls
 * quotation marks must not curl the ones holding an HTML attribute together; a sink that reads runs
 * of hyphens as dashes must not touch the hyphens in a URL; and an index marker inside a code span
 * is a literal, not a marker. So the line is cut into segments and each is offered to the sink
 * under the name of what it actually is:
 *
 * <ul>
 *   <li><b>prose</b> - ordinary text, the only run a sink may rewrite freely
 *   <li><b>verbatim</b> - code spans, backslash escapes, HTML tags, autolinks and link titles,
 *       which are copied through but still reported so the sink can keep its inline context current
 *   <li><b>comments</b>, <b>index markers</b> and <b>link destinations</b> - the three constructs
 *       the two targets genuinely disagree about
 * </ul>
 *
 * <p>This runs only on lines outside fenced code. Inside a fence nothing is interpreted at all, and
 * {@link MarkdownLoader} never gets this far.
 */
final class InlineScanner {

    /** Inline Markua index marker: {@code {i: "term"}}. */
    private static final Pattern INDEX_MARKER =
            Pattern.compile("\\{\\s*i\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([^}]*?))\\s*}");

    /**
     * A link reference definition, whose destination is a URL rather than prose.
     *
     * <p>A footnote definition looks almost identical - {@code [^1]: text} - but what follows the
     * colon is prose and must be scanned like any other line, so a label beginning with {@code ^}
     * is excluded.
     */
    private static final Pattern REFERENCE_DEFINITION =
            Pattern.compile("^(\\s{0,3}\\[(?!\\^)[^\\]]+]:\\s*)(\\S+)(.*)$");

    /**
     * A line made only of the characters that draw a horizontal rule, a table's delimiter row or a
     * setext heading underline. Deliberately a superset of all three: any line whose entire content
     * is hyphens, pipes and colons is structure rather than prose, whichever of them it is.
     */
    private static final Pattern STRUCTURAL = Pattern.compile("^[\\s|:-]*-[\\s|:-]*$");

    private final String line;
    private final MarkuaSink sink;
    private final StringBuilder out;
    private final StringBuilder prose = new StringBuilder();

    /** Open square brackets, each remembering whether a {@code !} made it an image. */
    private final Deque<Boolean> brackets = new ArrayDeque<>();

    private int i;

    private InlineScanner(String line, MarkuaSink sink) {
        this.line = line;
        this.sink = sink;
        this.out = new StringBuilder(line.length() + 32);
    }

    /** Rewrites one line by offering each of its runs to the sink. */
    static String rewrite(String line, MarkuaSink sink) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        return new InlineScanner(line, sink).run();
    }

    private String run() {
        // A rule, a table's delimiter row or a setext underline is punctuation
        // doing structural work. It holds no prose, and reading its hyphens as
        // dashes would turn a table into a paragraph.
        if (STRUCTURAL.matcher(line).matches()) {
            verbatim(line);
            return out.toString();
        }

        // A reference definition is a label and a URL, with no prose in it at all.
        Matcher reference = REFERENCE_DEFINITION.matcher(line);
        if (reference.matches()) {
            verbatim(reference.group(1));
            out.append(sink.linkDestination(reference.group(2), false));
            verbatim(reference.group(3));
            return out.toString();
        }

        while (i < line.length()) {
            char c = line.charAt(i);

            if (c == '\\' && i + 1 < line.length()) {
                // An escaped character is literal, including an escaped brace,
                // which is how an author writes a marker that is not one.
                flush();
                verbatim(line.substring(i, i + 2));
                i += 2;
            } else if (c == '`') {
                codeSpan();
            } else if (c == '<') {
                angle();
            } else if (c == '[') {
                openBracket();
            } else if (c == ']') {
                closeBracket();
            } else if (c == '{' && indexMarker()) {
                continue;
            } else {
                prose.append(c);
                i++;
            }
        }
        flush();
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Segment kinds
    // ------------------------------------------------------------------

    /**
     * Copies a whole inline code span. An unterminated run of backticks is prose, which is what
     * CommonMark makes of it too.
     */
    private void codeSpan() {
        int start = i;
        while (i < line.length() && line.charAt(i) == '`') {
            i++;
        }
        int length = i - start;
        int close = closingBacktickRun(i, length);
        if (close < 0) {
            prose.append(line, start, i);
            return;
        }
        flush();
        verbatim(line.substring(start, close + length));
        i = close + length;
    }

    /**
     * An HTML comment, tag or autolink. A bare {@code <} that starts none of those - {@code a < b}
     * - is ordinary prose, so the shape is checked before anything is claimed.
     */
    private void angle() {
        if (line.startsWith("<!--", i)) {
            int end = line.indexOf("-->", i + 4);
            if (end >= 0) {
                flush();
                out.append(sink.comment(line.substring(i, end + 3)));
                i = end + 3;
                return;
            }
        }
        int close = line.indexOf('>', i + 1);
        if (close > i && looksLikeMarkup(i + 1)) {
            flush();
            verbatim(line.substring(i, close + 1));
            i = close + 1;
            return;
        }
        prose.append('<');
        i++;
    }

    /** True when what follows the {@code <} could open a tag, a closing tag or an autolink. */
    private boolean looksLikeMarkup(int at) {
        if (at >= line.length()) {
            return false;
        }
        char c = line.charAt(at);
        if (c == '/' || c == '!' || c == '?') {
            return true;
        }
        if (!Character.isLetter(c)) {
            return false;
        }
        // An autolink carries a scheme; a tag carries a name. Both are safe to
        // copy through, and neither may contain a space before its '>'.
        return line.indexOf(' ', at) < 0 || line.indexOf(' ', at) > line.indexOf('>', at);
    }

    private void openBracket() {
        boolean image = i > 0 && line.charAt(i - 1) == '!';
        brackets.push(image);
        prose.append('[');
        i++;
    }

    /**
     * A closing bracket followed by {@code (} introduces an inline destination, which is a path or
     * a URL rather than prose. Anything else is just a bracket.
     */
    private void closeBracket() {
        boolean image = !brackets.isEmpty() && brackets.pop();
        if (i + 1 >= line.length() || line.charAt(i + 1) != '(') {
            prose.append(']');
            i++;
            return;
        }
        int close = matchingParen(i + 1);
        if (close < 0) {
            prose.append(']');
            i++;
            return;
        }
        flush();
        verbatim("](");
        emitDestination(line.substring(i + 2, close), image);
        verbatim(")");
        i = close + 1;
    }

    /**
     * Splits {@code path "title"} and offers only the path to the sink. A destination in angle
     * brackets may hold spaces, so it is taken whole.
     */
    private void emitDestination(String inside, boolean image) {
        int at = 0;
        while (at < inside.length() && Character.isWhitespace(inside.charAt(at))) {
            at++;
        }
        int end;
        if (at < inside.length() && inside.charAt(at) == '<') {
            end = inside.indexOf('>', at);
            end = end < 0 ? inside.length() : end + 1;
        } else {
            end = at;
            while (end < inside.length() && !Character.isWhitespace(inside.charAt(end))) {
                end++;
            }
        }
        verbatim(inside.substring(0, at));
        out.append(sink.linkDestination(inside.substring(at, end), image));
        verbatim(inside.substring(end));
    }

    /** Handles an index marker at the cursor. Returns false when the braces are ordinary text. */
    private boolean indexMarker() {
        Matcher m = INDEX_MARKER.matcher(line);
        if (!m.find(i) || m.start() != i) {
            return false;
        }
        flush();
        out.append(sink.indexMarker(firstNonNull(m.group(1), m.group(2), m.group(3))));
        i = m.end();
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void flush() {
        if (prose.isEmpty()) {
            return;
        }
        out.append(sink.prose(prose.toString()));
        prose.setLength(0);
    }

    private void verbatim(String text) {
        if (text.isEmpty()) {
            return;
        }
        out.append(text);
        sink.verbatim(text);
    }

    /** Index of the next run of exactly {@code length} backticks, or -1. */
    private int closingBacktickRun(int from, int length) {
        for (int at = from; at < line.length(); at++) {
            if (line.charAt(at) != '`') {
                continue;
            }
            int end = at;
            while (end < line.length() && line.charAt(end) == '`') {
                end++;
            }
            if (end - at == length) {
                return at;
            }
            at = end - 1;
        }
        return -1;
    }

    /**
     * Index of the {@code )} closing the {@code (} at {@code open}, allowing for nesting, or -1.
     */
    private int matchingParen(int open) {
        int depth = 0;
        for (int at = open; at < line.length(); at++) {
            char c = line.charAt(at);
            if (c == '\\') {
                at++;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return at;
                }
            }
        }
        return -1;
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return "";
    }
}
