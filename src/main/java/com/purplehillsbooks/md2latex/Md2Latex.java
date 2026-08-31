package com.purplehillsbooks.md2latex;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.nio.file.Path;
import java.util.List;

/**
 * Converts one Markdown document to a LaTeX body fragment.
 *
 * <p>The parser is configured once and is thread safe, so a single instance can
 * be reused across an entire directory tree. Source spans are switched on so
 * that any problem found while rendering can be reported against the line of
 * Markdown that caused it.
 */
public final class Md2Latex {

    private final Parser parser;
    private final CodeStyle codeStyle;
    private final boolean bookClass;
    private final List<Problem> problems;
    private final IndexTerms indexTerms;

    public Md2Latex(CodeStyle codeStyle, boolean bookClass, List<Problem> problems) {
        this(codeStyle, bookClass, problems, IndexTerms.none());
    }

    public Md2Latex(CodeStyle codeStyle, boolean bookClass, List<Problem> problems,
                    IndexTerms indexTerms) {
        this.codeStyle = codeStyle;
        this.bookClass = bookClass;
        this.problems = problems;
        this.indexTerms = indexTerms;
        this.parser = Parser.builder()
                .extensions(List.of(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        AutolinkExtension.create(),
                        TaskListItemsExtension.create(),
                        InsExtension.create(),
                        FootnotesExtension.create()))
                .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build();
    }

    /**
     * The LaTeX body, the metadata found in the source front matter, and
     * whether a requested title override actually landed on a heading.
     */
    public record Converted(String latex, MarkdownLoader.Result source, boolean titleApplied) {
    }

    /**
     * @param markdown   raw file contents
     * @param sourceFile the file being converted, used to resolve images and to
     *                   name the file in any problem report
     * @param outputDir  directory image paths should be relative to. For a
     *                   multi-file book this is the directory holding the master
     *                   .tex, not the chapter subdirectory, because LaTeX
     *                   resolves graphics against the main document.
     */
    public Converted convert(String markdown, Path sourceFile, Path outputDir) {
        return convert(markdown, sourceFile, outputDir, null);
    }

    /**
     * @param titleOverride replaces the text of the first level-1 heading, or
     *                      null to keep whatever the Markdown says
     */
    public Converted convert(String markdown, Path sourceFile, Path outputDir,
                             String titleOverride) {
        MarkdownLoader.Result pre = MarkdownLoader.process(markdown);
        Node document = parser.parse(pre.body());

        RenderContext ctx = new RenderContext(
                bookClass, codeStyle, sourceFile, outputDir, pre, problems, indexTerms);
        ctx.setTitleOverride(titleOverride);
        registerFootnotes(document, ctx);

        LatexVisitor visitor = new LatexVisitor(ctx);
        document.accept(visitor);
        return new Converted(visitor.result().strip() + "\n", pre, ctx.titleOverrideUsed());
    }

    /**
     * Footnote definitions may appear after the references that use them, so
     * they are collected before rendering starts.
     */
    private static void registerFootnotes(Node node, RenderContext ctx) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof FootnoteDefinition def) {
                ctx.registerFootnote(def);
            }
            registerFootnotes(child, ctx);
        }
    }
}
