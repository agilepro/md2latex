package com.purplehillsbooks.md2latex;

import java.util.List;

/**
 * Where {@link MarkdownLoader} sends the Markua constructs it recognises.
 *
 * <p>The reader knows how to <em>find</em> Markua: which lines open a blurb, which run of {@code
 * A>} prefixes belongs together, where a fenced code block suspends interpretation, and which
 * braces in a line are an index marker rather than ordinary prose. It does not know what any of
 * that should become. That is the sink's job, and it is the whole of the difference between the two
 * targets:
 *
 * <ul>
 *   <li>{@link LatexMarkerSink} rewrites blurbs into HTML marker divs and index markers into marker
 *       spans, which CommonMark parses into nodes that {@link LatexVisitor} turns into LaTeX.
 *   <li>{@link DocusaurusSink} rewrites blurbs into {@code :::} admonitions and drops index
 *       markers, because the output is Markdown that Docusaurus will read directly.
 * </ul>
 *
 * <p>A sink is stateful - {@link DocusaurusSink} carries quotation context across a paragraph - so
 * one instance belongs to one document.
 */
public interface MarkuaSink {

    /**
     * One blurb, aside or admonition, as the reader found it.
     *
     * @param kind the class name: {@code warning}, {@code aside}, {@code tip} and so on
     * @param title an explicit title, or null to let the sink choose one from the kind
     * @param untitled true for a blurb that deliberately carries no heading at all, which is
     *     different from one whose title is merely unstated
     */
    record Blurb(String kind, String title, boolean untitled) {}

    /** Lines that open the block. May be empty. */
    List<String> beginBlock(Blurb blurb);

    /** Lines that close the block last opened. May be empty. */
    List<String> endBlock(Blurb blurb);

    /**
     * Rewrites one line of content sitting inside an open block, after inline scanning. Only a sink
     * that needs a per-line prefix - a Markdown blockquote, say - has anything to do here.
     */
    default String contentLine(Blurb open, String line) {
        return line;
    }

    /** A blank line, where a paragraph ends and any inline context should be reset. */
    default void paragraphBreak() {}

    /** Replacement text for one inline {@code {i: "term"}} marker. */
    String indexMarker(String term);

    /** Replacement for an HTML comment, which is how both targets spell their escape hatch. */
    default String comment(String html) {
        return html;
    }

    /**
     * Replacement for the destination of an inline link or image. The surrounding {@code ](} and
     * {@code )} and any link title are copied verbatim either way.
     */
    default String linkDestination(String destination, boolean image) {
        return destination;
    }

    /** A run of ordinary prose from within a line. */
    default String prose(String text) {
        return text;
    }

    /**
     * Text copied through untouched - a code span, a URL, an HTML tag. A sink whose {@link #prose}
     * depends on what came before uses this to keep that context current.
     */
    default void verbatim(String text) {}
}
