package com.purplehillsbooks.md2latex;

import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a {@link LatexVisitor} needs that is not the node tree itself:
 * sectioning depth, code rendering style, the directories used to rewrite
 * relative image paths, the footnote table, and the collector that turns
 * trouble into located {@link Problem}s.
 *
 * <p>One context is built per source file, because image paths resolve
 * relative to the file that referenced them. The problem list is supplied by
 * the caller so it can be shared across every file in a run.
 */
public final class RenderContext {

    /** H1..H6 mapped onto LaTeX sectioning commands for {@code book}. */
    private static final String[] BOOK_HEADINGS = {
            "chapter", "section", "subsection", "subsubsection", "paragraph", "subparagraph"
    };

    /** H1..H6 mapped onto LaTeX sectioning commands for {@code article}. */
    private static final String[] ARTICLE_HEADINGS = {
            "section", "subsection", "subsubsection", "paragraph", "subparagraph", "subparagraph"
    };

    private final String[] headings;
    private final CodeStyle codeStyle;
    private final Path sourceFile;
    private final Path sourceDir;
    private final Path outputDir;
    private final List<Problem> problems;
    private final MarkdownLoader.Result source;
    private final Map<String, FootnoteDefinition> footnotes = new HashMap<>();

    /** Replaces the text of the first level-1 heading, when the manifest asks. */
    private String titleOverride;
    private boolean titleOverrideUsed;

    private final IndexTerms indexTerms;

    public RenderContext(boolean bookClass,
                         CodeStyle codeStyle,
                         Path sourceFile,
                         Path outputDir,
                         MarkdownLoader.Result source,
                         List<Problem> problems,
                         IndexTerms indexTerms) {
        this.headings = bookClass ? BOOK_HEADINGS : ARTICLE_HEADINGS;
        this.codeStyle = codeStyle;
        this.sourceFile = sourceFile;
        this.sourceDir = sourceFile.getParent();
        this.outputDir = outputDir;
        this.source = source;
        this.problems = problems;
        this.indexTerms = indexTerms;
    }

    public IndexTerms indexTerms() {
        return indexTerms;
    }

    public String headingCommand(int level) {
        int index = Math.max(1, Math.min(level, headings.length)) - 1;
        return headings[index];
    }

    public CodeStyle codeStyle() {
        return codeStyle;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public Path sourceDir() {
        return sourceDir;
    }

    public Path outputDir() {
        return outputDir;
    }

    public void registerFootnote(FootnoteDefinition def) {
        footnotes.put(def.getLabel(), def);
    }

    public FootnoteDefinition footnote(String label) {
        return footnotes.get(label);
    }

    // ------------------------------------------------------------------
    // Problem reporting
    // ------------------------------------------------------------------

    /** Records a fault that would stop the generated LaTeX from compiling. */
    public void error(Node node, String message, String hint) {
        problems.add(problem(Problem.Severity.ERROR, node, 0, message, hint));
    }

    /**
     * As {@link #error(Node, String, String)}, but for a fault at a known
     * offset in characters from the start of the node's first line.
     */
    public void errorAt(Node node, int columnOffset, String message, String hint) {
        problems.add(problem(Problem.Severity.ERROR, node, columnOffset, message, hint));
    }

    /** Records something the author should know that still compiles. */
    public void warn(Node node, String message, String hint) {
        problems.add(problem(Problem.Severity.WARNING, node, 0, message, hint));
    }

    private Problem problem(Problem.Severity severity, Node node,
                            int columnOffset, String message, String hint) {
        int line = 0;
        int column = 0;
        String text = null;

        SourceSpan span = firstSpan(node);
        if (span != null) {
            line = source.originalLine(span.getLineIndex());
            text = source.sourceLine(line);
            column = span.getColumnIndex() + 1 + columnOffset;
        }
        return new Problem(severity, sourceFile, line, column, text, message, hint);
    }

    /**
     * The first source span of a node, or of its nearest ancestor that has one.
     * Inline nodes occasionally lack spans, in which case the enclosing block
     * still gives a usable line.
     */
    private static SourceSpan firstSpan(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            List<SourceSpan> spans = n.getSourceSpans();
            if (spans != null && !spans.isEmpty()) {
                return spans.get(0);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Title override
    // ------------------------------------------------------------------

    public void setTitleOverride(String titleOverride) {
        this.titleOverride = titleOverride;
    }

    /**
     * Returns the manifest-supplied chapter title the first time a level-1
     * heading is rendered, and null every time after that, so only the opening
     * heading of a document is replaced.
     */
    public String takeTitleOverride() {
        if (titleOverride == null || titleOverrideUsed) {
            return null;
        }
        titleOverrideUsed = true;
        return titleOverride;
    }

    /**
     * The pending override without consuming it, so index matching can see the
     * text a heading is about to be given.
     */
    public String peekTitleOverride() {
        return titleOverrideUsed ? null : titleOverride;
    }

    /** True once a title override has actually been applied to a heading. */
    public boolean titleOverrideUsed() {
        return titleOverrideUsed;
    }
}
