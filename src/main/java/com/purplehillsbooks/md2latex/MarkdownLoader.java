package com.purplehillsbooks.md2latex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Markua, and hands what it finds to a {@link MarkuaSink}.
 *
 * <p>This is the front end both targets share. It deals with everything CommonMark knows nothing
 * about, before the text reaches a parser or a writer:
 *
 * <ul>
 *   <li>YAML front matter delimited by {@code ---}, which is stripped and returned as metadata.
 *   <li>Blurbs, in the fenced form ({@code {blurb, class: warning} ... {/blurb}}) and the older
 *       line-prefix form ({@code A>}, {@code W>}, {@code T>} and friends).
 *   <li>Docusaurus admonitions of the form {@code :::tip[Title] ... :::}, which Markua does not
 *       define but which a folder of source may well already contain.
 *   <li>Inline index markers, {@code {i: "tribe"}}.
 * </ul>
 *
 * <p>All of these are line oriented and skip fenced code regions, so a literal {@code :::} or
 * {@code A>} inside a code block survives untouched. Within a line, {@link InlineScanner} takes
 * care of the finer distinctions - a marker inside a code span is a literal, a hyphen inside a URL
 * is a hyphen. An <em>indented</em> code block is not detected, so a literal {@code {i: ...}} that
 * must survive should be written in a fenced block.
 *
 * <p>Because these transformations shift line numbers, a map back to the original file is kept so
 * that error messages can point at the line the author actually wrote.
 */
public final class MarkdownLoader {

    /** Opening fence, e.g. {@code :::tip} or {@code :::warning[Careful]}. */
    private static final Pattern ADMONITION_OPEN =
            Pattern.compile("^\\s{0,3}:::\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*(?:\\[(.*?)])?\\s*$");

    /** Closing fence: three or more colons and nothing else. */
    private static final Pattern ADMONITION_CLOSE = Pattern.compile("^\\s{0,3}:::+\\s*$");

    /** Fenced code delimiter, backtick or tilde. */
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})(.*)$");

    /** Markua {@code {blurb}} / {@code {aside}} opener, with optional attributes. */
    private static final Pattern MARKUA_BLURB_OPEN =
            Pattern.compile(
                    "^\\s{0,3}\\{\\s*(blurb|aside)\\s*(?:,(.*?))?\\s*}\\s*$",
                    Pattern.CASE_INSENSITIVE);

    /** Markua {@code {/blurb}} / {@code {/aside}} closer. */
    private static final Pattern MARKUA_BLURB_CLOSE =
            Pattern.compile(
                    "^\\s{0,3}\\{\\s*/\\s*(blurb|aside)\\s*}\\s*$", Pattern.CASE_INSENSITIVE);

    /**
     * Markua line-prefix blurb: a capital letter and {@code >} in column one. Column one is
     * required, as Markua requires, so that indented text and code continuation lines are never
     * mistaken for a blurb.
     */
    private static final Pattern MARKUA_PREFIX = Pattern.compile("^([ABCDEIQTWX])>(?: ?(.*))?$");

    /** One {@code key: value} pair from a Markua attribute list. */
    private static final Pattern MARKUA_ATTRIBUTE =
            Pattern.compile("\\s*([A-Za-z][A-Za-z0-9_-]*)\\s*:\\s*(\"[^\"]*\"|'[^']*'|[^,]*)\\s*");

    /** A blurb that carries no heading of its own. */
    static final String KIND_BLURB = "blurb";

    /** A blurb rendered as a centred block rather than a framed one. */
    static final String KIND_CENTER = "center";

    /**
     * Markua's line-prefix blurb letters, mapped to the admonition kind they stand for. {@code B}
     * is a blurb with no heading of its own and {@code C} centres its contents, so neither takes a
     * title.
     */
    private static final Map<Character, String> PREFIX_KINDS =
            Map.of(
                    'A', "aside",
                    'B', KIND_BLURB,
                    'C', KIND_CENTER,
                    'D', "discussion",
                    'E', "error",
                    'I', "information",
                    'Q', "question",
                    'T', "tip",
                    'W', "warning",
                    'X', "exercise");

    private MarkdownLoader() {}

    /**
     * The result of preprocessing: extracted metadata, the rewritten body, the original file split
     * into lines, and a map from body line to source line.
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
         * Docusaurus sidebar_position, used only by the scaffolder to guess a chapter order.
         * Returns {@link Integer#MAX_VALUE} when absent so unpositioned files sort last rather than
         * first.
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
         * Maps a 0-based line index in the preprocessed body back to a 1-based line number in the
         * original file, or 0 when the line was synthesized.
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

    /**
     * Preprocesses for the LaTeX target, which is also all a caller wanting only metadata needs.
     */
    public static Result process(String source) {
        return process(source, new LatexMarkerSink());
    }

    public static Result process(String source, MarkuaSink sink) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        List<String> all = List.of(normalized.split("\n", -1));

        Map<String, String> frontMatter = new LinkedHashMap<>();
        int start = extractFrontMatter(all, frontMatter);

        StringBuilder body = new StringBuilder();
        List<Integer> origins = new ArrayList<>();
        new Rewriter(sink, body, origins).run(all, start);

        int[] lineMap = new int[origins.size()];
        for (int i = 0; i < origins.size(); i++) {
            lineMap[i] = origins.get(i);
        }
        return new Result(frontMatter, body.toString(), all, lineMap);
    }

    /**
     * Parses leading YAML front matter into {@code meta}.
     *
     * <p>Only flat {@code key: value} pairs are understood, which is all a Docusaurus header
     * contains. Keys are trimmed, because hand-written front matter is frequently indented even
     * though that is not strictly valid.
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

    // ------------------------------------------------------------------
    // Body rewriting
    // ------------------------------------------------------------------

    /**
     * The line-by-line read. Held as an object rather than a static method because the pass carries
     * a fair amount of state: whether we are inside fenced code, inside a {@code :::} admonition,
     * inside a fenced Markua blurb, or inside a run of {@code A>} prefixed lines.
     *
     * <p>Nesting is not supported for any of these; an opener seen while a block is already open is
     * treated as literal text.
     */
    private static final class Rewriter {

        private final MarkuaSink sink;
        private final StringBuilder out;
        private final List<Integer> origins;

        /** Inside a fenced code block, and the character the fence was made of. */
        private boolean inCode;

        private String fenceMarker;

        private boolean inAdmonition;
        private boolean inMarkuaBlurb;

        /** The block currently open, whichever of the three forms opened it, or null. */
        private MarkuaSink.Blurb open;

        /** The prefix letter of the run of {@code A>} lines being gathered, or 0. */
        private char prefixLetter;

        /** Inside a fenced code block that is itself inside an {@code A>} run. */
        private boolean prefixCode;

        private String prefixFenceMarker;

        Rewriter(MarkuaSink sink, StringBuilder out, List<Integer> origins) {
            this.sink = sink;
            this.out = out;
            this.origins = origins;
        }

        void run(List<String> lines, int start) {
            int lastOriginal = start + 1;

            for (int i = start; i < lines.size(); i++) {
                String line = lines.get(i);
                int original = i + 1;
                lastOriginal = original;

                Matcher fence = CODE_FENCE.matcher(line);
                boolean isFence = fence.matches();

                // 1. Inside fenced code nothing is interpreted, not even a closer.
                if (inCode) {
                    if (isFence && fence.group(1).startsWith(fenceMarker)) {
                        inCode = false;
                        fenceMarker = null;
                    }
                    emitVerbatim(line, original);
                    continue;
                }

                // 2. A run of A>-prefixed lines continues until a line lacks the prefix.
                if (prefixLetter != 0) {
                    Matcher prefix = MARKUA_PREFIX.matcher(line);
                    if (prefix.matches() && prefix.group(1).charAt(0) == prefixLetter) {
                        emitPrefixContent(text(prefix.group(2)), original);
                        continue;
                    }
                    closePrefixBlurb(original);
                    // Fall through: this line is ordinary content again.
                }

                // 3. Closers for whichever block form is open.
                if (inMarkuaBlurb && MARKUA_BLURB_CLOSE.matcher(line).matches()) {
                    emitEnd(original);
                    inMarkuaBlurb = false;
                    continue;
                }
                if (inAdmonition && ADMONITION_CLOSE.matcher(line).matches()) {
                    emitEnd(original);
                    inAdmonition = false;
                    continue;
                }

                // 4. An opening code fence suspends interpretation until it closes.
                if (isFence) {
                    inCode = true;
                    fenceMarker = fence.group(1).substring(0, 1);
                    emitVerbatim(line, original);
                    continue;
                }

                // 5. Openers, only when nothing else is already open.
                if (!inAdmonition && !inMarkuaBlurb) {
                    Matcher admonition = ADMONITION_OPEN.matcher(line);
                    if (admonition.matches()) {
                        emitBegin(
                                new MarkuaSink.Blurb(
                                        admonition.group(1), admonition.group(2), false),
                                original);
                        inAdmonition = true;
                        continue;
                    }
                    Matcher blurb = MARKUA_BLURB_OPEN.matcher(line);
                    if (blurb.matches()) {
                        openMarkuaBlurb(blurb.group(1), blurb.group(2), original);
                        continue;
                    }
                    Matcher prefix = MARKUA_PREFIX.matcher(line);
                    if (prefix.matches()) {
                        openPrefixBlurb(prefix.group(1).charAt(0), original);
                        emitPrefixContent(text(prefix.group(2)), original);
                        continue;
                    }
                }

                emitContent(line, original);
            }

            // Unterminated blocks are closed so the output stays balanced.
            if (prefixLetter != 0) {
                closePrefixBlurb(lastOriginal);
            }
            if (inAdmonition || inMarkuaBlurb) {
                emitEnd(lastOriginal);
            }
        }

        // --- Markua fenced blurbs -------------------------------------

        /**
         * {@code {blurb}} has no heading; {@code {aside}} and any explicit {@code class:} do. A
         * {@code title:} attribute is not Markua, but it costs nothing to honour and mirrors {@code
         * :::tip[Title]}.
         */
        private void openMarkuaBlurb(String form, String attributes, int original) {
            Map<String, String> attrs = parseAttributes(attributes);
            String kind = attrs.get("class");
            if (kind == null || kind.isBlank()) {
                kind = form.toLowerCase(Locale.ROOT);
            }
            String title = attrs.get("title");
            boolean untitled =
                    title == null
                            && (KIND_BLURB.equalsIgnoreCase(kind)
                                    || KIND_CENTER.equalsIgnoreCase(kind));
            emitBegin(new MarkuaSink.Blurb(kind, title, untitled), original);
            inMarkuaBlurb = true;
        }

        /** Splits {@code class: warning, title: "X"} into a map, keys lower-cased. */
        private static Map<String, String> parseAttributes(String attributes) {
            Map<String, String> attrs = new LinkedHashMap<>();
            if (attributes == null || attributes.isBlank()) {
                return attrs;
            }
            Matcher m = MARKUA_ATTRIBUTE.matcher(attributes);
            int at = 0;
            while (m.find(at) && m.start() == at) {
                String value = m.group(2).trim();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                attrs.put(m.group(1).toLowerCase(Locale.ROOT), value);
                at = m.end();
                if (at < attributes.length() && attributes.charAt(at) == ',') {
                    at++;
                } else {
                    break;
                }
            }
            return attrs;
        }

        // --- Markua line-prefix blurbs --------------------------------

        private void openPrefixBlurb(char letter, int original) {
            String kind = PREFIX_KINDS.get(letter);
            boolean untitled = KIND_BLURB.equals(kind) || KIND_CENTER.equals(kind);
            emitBegin(new MarkuaSink.Blurb(kind, null, untitled), original);
            prefixLetter = letter;
        }

        /**
         * One line of {@code A>} content, with the prefix already stripped. A fenced code block may
         * open inside the run, in which case its contents are passed through untouched until the
         * fence closes.
         */
        private void emitPrefixContent(String content, int original) {
            Matcher fence = CODE_FENCE.matcher(content);
            if (fence.matches()) {
                if (!prefixCode) {
                    prefixCode = true;
                    prefixFenceMarker = fence.group(1).substring(0, 1);
                } else if (fence.group(1).startsWith(prefixFenceMarker)) {
                    prefixCode = false;
                    prefixFenceMarker = null;
                }
                emitVerbatim(content, original);
                return;
            }
            if (prefixCode) {
                emitVerbatim(content, original);
            } else {
                emitContent(content, original);
            }
        }

        private void closePrefixBlurb(int original) {
            emitEnd(original);
            prefixLetter = 0;
            prefixCode = false;
            prefixFenceMarker = null;
        }

        // --- Emitting -------------------------------------------------

        private void emitBegin(MarkuaSink.Blurb blurb, int original) {
            open = blurb;
            for (String line : sink.beginBlock(blurb)) {
                emit(line, original);
            }
        }

        private void emitEnd(int original) {
            MarkuaSink.Blurb blurb = open;
            open = null;
            for (String line : sink.endBlock(blurb)) {
                emit(line, original);
            }
        }

        /** An ordinary line: scanned inline, then wrapped if a block is open around it. */
        private void emitContent(String line, int original) {
            if (line.isBlank()) {
                sink.paragraphBreak();
            }
            emit(wrap(InlineScanner.rewrite(line, sink)), original);
        }

        /**
         * A line inside fenced code: nothing in it is interpreted, but it may still need wrapping.
         */
        private void emitVerbatim(String line, int original) {
            emit(wrap(line), original);
        }

        private String wrap(String line) {
            return open == null ? line : sink.contentLine(open, line);
        }

        private void emit(String line, int originalLine) {
            out.append(line).append('\n');
            origins.add(originalLine);
        }

        /** An absent capture group is an empty line, not a null one. */
        private static String text(String group) {
            return group == null ? "" : group;
        }
    }
}
