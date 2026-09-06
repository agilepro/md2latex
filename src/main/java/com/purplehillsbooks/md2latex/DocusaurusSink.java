package com.purplehillsbooks.md2latex;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The sink for the Docusaurus target: it rewrites Markua into the Markdown a Docusaurus site reads
 * directly.
 *
 * <p>Unlike {@link LatexMarkerSink} this one produces the finished thing rather than markers for a
 * later pass, because the output is Markdown again and most of the source already is Markdown. Only
 * four kinds of thing actually change:
 *
 * <ul>
 *   <li><b>Blurbs</b> become {@code :::} admonitions. Markua names ten kinds and Docusaurus renders
 *       five, so the extra ones are folded onto the nearest match and keep their own name as the
 *       admonition's title - an {@code X>} exercise becomes {@code :::info[Exercise]} rather than
 *       silently turning into a plain info box.
 *   <li><b>Index markers</b> disappear. A website has no index to put them in, and leaving the
 *       braces would hand MDX something that looks like an expression.
 *   <li><b>The escape hatch</b> is honoured the other way round: {@code <!-- latex: ... -->} is
 *       dropped here and {@code <!-- docusaurus: ... -->} is unwrapped, so a document can carry a
 *       line for each target and neither sees the other's.
 *   <li><b>Typography</b> is settled: straight quotes are curled and runs of hyphens are read as
 *       dashes, exactly as they are for the book, so the two outputs read the same. The characters
 *       themselves are Unicode, which is all a browser needs, so the table behind this is a
 *       fraction of the size of the LaTeX one. See {@link DocusaurusCharacters}.
 * </ul>
 *
 * <p>Everything else - headings, lists, tables, footnotes, task lists, fenced code, links - is
 * already valid Docusaurus Markdown and is passed through untouched, which is why the generated
 * file differs from its source by a small and readable diff.
 */
public final class DocusaurusSink implements MarkuaSink {

    /** Decides what a link or image destination becomes once the file has moved. */
    @FunctionalInterface
    public interface LinkResolver {
        String resolve(String destination, boolean image);
    }

    /** A Docusaurus admonition type, and the title to give it when the kind has no native one. */
    private record Admonition(String type, String title) {}

    /**
     * Markua and Docusaurus kinds mapped onto the five types Docusaurus renders.
     *
     * <p>Where a kind has no equivalent, the nearest box is used and the kind's own name becomes
     * the title. That keeps the distinction the author drew visible on the page instead of quietly
     * collapsing {@code discussion}, {@code exercise} and {@code question} into one thing.
     */
    private static final Map<String, Admonition> KINDS =
            Map.ofEntries(
                    Map.entry("note", new Admonition("note", null)),
                    Map.entry("tip", new Admonition("tip", null)),
                    Map.entry("info", new Admonition("info", null)),
                    Map.entry("warning", new Admonition("warning", null)),
                    Map.entry("danger", new Admonition("danger", null)),
                    // Docusaurus 2 spelled today's warning "caution".
                    Map.entry("caution", new Admonition("warning", null)),
                    Map.entry("aside", new Admonition("note", "Aside")),
                    Map.entry("blurb", new Admonition("note", null)),
                    Map.entry("discussion", new Admonition("info", "Discussion")),
                    Map.entry("error", new Admonition("danger", "Error")),
                    Map.entry("exercise", new Admonition("info", "Exercise")),
                    Map.entry("information", new Admonition("info", null)),
                    Map.entry("question", new Admonition("info", "Question")));

    /** Raw lines meant only for LaTeX, and only for Docusaurus. */
    private static final String LATEX_COMMENT = "latex:";

    private static final String DOCUSAURUS_COMMENT = "docusaurus:";

    private final LinkResolver links;
    private final Quotes quotes = new Quotes();

    public DocusaurusSink() {
        this((destination, image) -> destination);
    }

    public DocusaurusSink(LinkResolver links) {
        this.links = links;
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    @Override
    public List<String> beginBlock(Blurb blurb) {
        if (isCentred(blurb)) {
            // The one construct with no Markdown equivalent. The align attribute
            // is old HTML, but it is the only spelling that works both as raw
            // HTML in a CommonMark file and as JSX in an MDX one; a style
            // attribute would have to be a string in one and an object in the
            // other.
            return List.of("", "<div align=\"center\">", "");
        }
        if (isQuoted(blurb)) {
            return List.of("");
        }
        Admonition admonition = admonitionFor(blurb);
        String title = blurb.title() != null ? blurb.title() : admonition.title();
        StringBuilder open = new StringBuilder(":::").append(admonition.type());
        if (title != null && !title.isBlank()) {
            open.append('[').append(escapeTitle(title)).append(']');
        }
        return List.of("", open.toString(), "");
    }

    @Override
    public List<String> endBlock(Blurb blurb) {
        if (isCentred(blurb)) {
            return List.of("", "</div>", "");
        }
        if (isQuoted(blurb)) {
            return List.of("");
        }
        return List.of("", ":::", "");
    }

    /**
     * A blockquote needs its marker on every line, including the blank ones, or it ends early.
     *
     * <p>This is what an untitled blurb becomes. Docusaurus has no admonition without a heading -
     * {@code :::note} always prints the word "note" - and a blockquote is the honest equivalent:
     * text set apart from the flow, carrying no label.
     */
    @Override
    public String contentLine(Blurb open, String line) {
        if (!isQuoted(open)) {
            return line;
        }
        return line.isBlank() ? ">" : "> " + line;
    }

    private static boolean isCentred(Blurb blurb) {
        return blurb != null && MarkdownLoader.KIND_CENTER.equalsIgnoreCase(blurb.kind());
    }

    private static boolean isQuoted(Blurb blurb) {
        return blurb != null && blurb.untitled() && !isCentred(blurb);
    }

    private static Admonition admonitionFor(Blurb blurb) {
        String kind = blurb == null ? "note" : blurb.kind().toLowerCase(Locale.ROOT);
        Admonition known = KINDS.get(kind);
        // An unrecognised class is somebody's own; keep the name they gave it.
        return known != null ? known : new Admonition("note", capitalize(kind));
    }

    /** A closing bracket would end the title early, so it is escaped. */
    private static String escapeTitle(String title) {
        return title.replace("]", "\\]");
    }

    // ------------------------------------------------------------------
    // Inline
    // ------------------------------------------------------------------

    /** A website has no index, so the marker leaves nothing behind. */
    @Override
    public String indexMarker(String term) {
        return "";
    }

    @Override
    public String comment(String html) {
        String inner = html.substring(4, html.length() - 3).strip();
        if (startsWithIgnoreCase(inner, LATEX_COMMENT)) {
            return "";
        }
        if (startsWithIgnoreCase(inner, DOCUSAURUS_COMMENT)) {
            return inner.substring(DOCUSAURUS_COMMENT.length()).strip();
        }
        return html;
    }

    @Override
    public String linkDestination(String destination, boolean image) {
        return links.resolve(destination, image);
    }

    @Override
    public String prose(String text) {
        // Dashes first: Quotes reads an em dash as an opening context, so it has
        // to be looking at the dash rather than at the hyphens that spelled it.
        return DocusaurusCharacters.translate(
                quotes.directional(Dashes.convert(text, Dashes.Style.UNICODE)));
    }

    @Override
    public void verbatim(String text) {
        quotes.passThrough(text);
    }

    @Override
    public void paragraphBreak() {
        quotes.startBlock();
    }

    private static boolean startsWithIgnoreCase(String s, String prefix) {
        return s.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
