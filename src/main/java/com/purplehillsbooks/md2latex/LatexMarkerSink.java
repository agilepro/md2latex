package com.purplehillsbooks.md2latex;

import java.util.List;

/**
 * The sink for the LaTeX target: it rewrites Markua into HTML markers and leaves everything else
 * exactly as the author wrote it.
 *
 * <p>Nothing here produces LaTeX. The markers are chosen so that CommonMark parses them into nodes
 * - an {@code HtmlBlock} for a blurb, an {@code HtmlInline} for an index marker - which {@link
 * LatexVisitor} then recognises while walking the tree. Going through the parser rather than
 * emitting LaTeX straight away is what lets the body of a blurb stay ordinary Markdown, fenced code
 * and all.
 *
 * <p>Prose is returned untouched. Quotation marks, dashes and unusual characters are decided later,
 * during the tree walk, where the surrounding structure is known and a code span can be told from a
 * paragraph with certainty.
 */
public final class LatexMarkerSink implements MarkuaSink {

    static final String END_MARKER = "<div data-adm=\"end\"></div>";

    @Override
    public List<String> beginBlock(Blurb blurb) {
        StringBuilder b = new StringBuilder("<div data-adm=\"begin\" data-kind=\"");
        b.append(attr(blurb.kind())).append('"');
        if (blurb.title() != null && !blurb.title().isBlank()) {
            b.append(" data-title=\"").append(attr(blurb.title())).append('"');
        } else if (blurb.untitled()) {
            b.append(" data-untitled=\"1\"");
        }
        return List.of("", b.append("></div>").toString(), "");
    }

    @Override
    public List<String> endBlock(Blurb blurb) {
        return List.of("", END_MARKER, "");
    }

    /**
     * A paired {@code <span>} rather than a self-closing one on purpose: a self-closing tag alone
     * on a line would become a CommonMark HTML <em>block</em>, and the {@code \index} whatsit would
     * then land in vertical mode where a page break can strand it on the wrong page. The paired
     * form is never a block start, so the entry always sits inside a paragraph.
     */
    @Override
    public String indexMarker(String term) {
        return "<span data-index=\"" + attr(term.trim()) + "\"></span>";
    }

    static String attr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    static String unattr(String s) {
        return s.replace("&quot;", "\"").replace("&lt;", "<").replace("&amp;", "&");
    }
}
