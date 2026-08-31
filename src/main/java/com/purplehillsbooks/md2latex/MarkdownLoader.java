package com.purplehillsbooks.md2latex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the two pieces of Docusaurus/Jekyll syntax that CommonMark knows
 * nothing about, before the text reaches the parser:
 *
 * <ul>
 *   <li>YAML front matter delimited by {@code ---}, which is stripped and
 *       returned as metadata (we need {@code sidebar_position} to order
 *       chapters, so it is parsed rather than merely discarded).</li>
 *   <li>Admonition blocks of the form {@code :::tip[Title] ... :::}, which are
 *       rewritten into HTML marker divs. CommonMark parses those as HtmlBlock
 *       nodes, and the body between them still parses as ordinary Markdown.</li>
 * </ul>
 *
 * <p>Both transformations are line oriented and skip fenced code regions, so a
 * literal {@code :::} inside a code block survives untouched.
 *
 * <p>Because both shift line numbers, a map back to the original file is kept
 * so that error messages can point at the line the author actually wrote.
 */
public final class MarkdownLoader {

    /** Opening fence, e.g. {@code :::tip} or {@code :::warning[Careful]}. */
    private static final Pattern ADMONITION_OPEN =
            Pattern.compile("^\\s{0,3}:::\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*(?:\\[(.*?)])?\\s*$");

    /** Closing fence: three or more colons and nothing else. */
    private static final Pattern ADMONITION_CLOSE =
            Pattern.compile("^\\s{0,3}:::+\\s*$");

    /** Fenced code delimiter, backtick or tilde. */
    private static final Pattern CODE_FENCE =
            Pattern.compile("^\\s{0,3}(`{3,}|~{3,})(.*)$");

    private MarkdownLoader() {
    }

    /**
     * The result of preprocessing: extracted metadata, the rewritten body, the
     * original file split into lines, and a map from body line to source line.
     */
    public record Result(
            Map<String, String> frontMatter,
            String body,
            List<String> originalLines,
            int[] lineMap) {

        public String title() {
            return frontMatter.get("title");
        }

        /**
         * Docusaurus sidebar_position, used only by the scaffolder to guess a
         * chapter order. Returns {@link Integer#MAX_VALUE} when absent so
         * unpositioned files sort last rather than first.
         */
        public int sidebarPosition() {
            String v = frontMatter.get("sidebar_position");
            if (v == null) {
                return Integer.MAX_VALUE;
            }
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }

        /**
         * Maps a 0-based line index in the preprocessed body back to a 1-based
         * line number in the original file, or 0 when the line was synthesized.
         */
        public int originalLine(int bodyLineIndex) {
            if (bodyLineIndex < 0 || bodyLineIndex >= lineMap.length) {
                return 0;
            }
            return lineMap[bodyLineIndex];
        }

        /** The text of a 1-based original line, or null if out of range. */
        public String sourceLine(int originalLine) {
            if (originalLine < 1 || originalLine > originalLines.size()) {
                return null;
            }
            return originalLines.get(originalLine - 1);
        }
    }

    public static Result process(String source) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> all = List.of(normalized.split("\n", -1));

        Map<String, String> frontMatter = new LinkedHashMap<>();
        int start = extractFrontMatter(all, frontMatter);

        StringBuilder body = new StringBuilder();
        List<Integer> origins = new ArrayList<>();
        rewriteAdmonitions(all, start, body, origins);

        int[] lineMap = new int[origins.size()];
        for (int i = 0; i < origins.size(); i++) {
            lineMap[i] = origins.get(i);
        }
        return new Result(frontMatter, body.toString(), all, lineMap);
    }

    /**
     * Parses leading YAML front matter into {@code meta}.
     *
     * <p>Only flat {@code key: value} pairs are understood, which is all a
     * Docusaurus header contains. Keys are trimmed, because hand-written front
     * matter is frequently indented even though that is not strictly valid.
     *
     * @return index of the first line after the front matter
     */
    private static int extractFrontMatter(List<String> lines, Map<String, String> meta) {
        if (lines.isEmpty() || !lines.get(0).trim().equals("---")) {
            return 0;
        }
        int close = -1;
        for (int i = 1; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("---")) {
                close = i;
                break;
            }
        }
        if (close < 0) {
            // Unterminated front matter; treat the document as having none.
            return 0;
        }
        for (int i = 1; i < close; i++) {
            String line = lines.get(i);
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (!key.isEmpty()) {
                meta.put(key, value);
            }
        }
        return close + 1;
    }

    /**
     * Rewrites {@code :::kind[Title]} / {@code :::} pairs into marker divs,
     * recording the original line behind every emitted line.
     * Nesting is not supported; a nested opener is treated as literal text.
     */
    private static void rewriteAdmonitions(List<String> lines, int start,
                                           StringBuilder out, List<Integer> origins) {
        boolean inCode = false;
        String fenceMarker = null;
        boolean inAdmonition = false;
        int lastOriginal = start + 1;

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            int original = i + 1;
            lastOriginal = original;

            Matcher fence = CODE_FENCE.matcher(line);
            if (fence.matches()) {
                String marker = fence.group(1);
                if (!inCode) {
                    inCode = true;
                    fenceMarker = marker.substring(0, 1);
                } else if (marker.startsWith(fenceMarker)) {
                    inCode = false;
                    fenceMarker = null;
                }
                emit(out, origins, line, original);
                continue;
            }

            if (!inCode) {
                if (!inAdmonition) {
                    Matcher open = ADMONITION_OPEN.matcher(line);
                    if (open.matches()) {
                        emit(out, origins, "", original);
                        emit(out, origins, beginMarker(open.group(1), open.group(2)), original);
                        emit(out, origins, "", original);
                        inAdmonition = true;
                        continue;
                    }
                } else if (ADMONITION_CLOSE.matcher(line).matches()) {
                    emit(out, origins, "", original);
                    emit(out, origins, END_MARKER, original);
                    emit(out, origins, "", original);
                    inAdmonition = false;
                    continue;
                }
            }
            emit(out, origins, line, original);
        }

        if (inAdmonition) {
            // Unterminated admonition; close it so the LaTeX stays balanced.
            emit(out, origins, "", lastOriginal);
            emit(out, origins, END_MARKER, lastOriginal);
            emit(out, origins, "", lastOriginal);
        }
    }

    private static void emit(StringBuilder out, List<Integer> origins,
                             String line, int originalLine) {
        out.append(line).append('\n');
        origins.add(originalLine);
    }

    static final String END_MARKER = "<div data-adm=\"end\"></div>";

    private static String beginMarker(String kind, String title) {
        StringBuilder b = new StringBuilder("<div data-adm=\"begin\" data-kind=\"");
        b.append(attr(kind)).append('"');
        if (title != null && !title.isBlank()) {
            b.append(" data-title=\"").append(attr(title)).append('"');
        }
        return b.append("></div>").toString();
    }

    private static String attr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    static String unattr(String s) {
        return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&amp;", "&");
    }
}
