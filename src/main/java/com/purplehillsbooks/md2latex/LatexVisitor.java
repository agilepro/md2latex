package com.purplehillsbooks.md2latex;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.footnotes.InlineFootnote;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.ins.Ins;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomBlock;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

/**
 * Walks a CommonMark AST and emits LaTeX.
 *
 * <p>This is a visitor rather than a {@code NodeRenderer} on purpose: the renderer machinery in
 * commonmark-java is built around producing HTML, whereas a foreign output format is served better
 * by walking the tree directly.
 *
 * <p>Nested instances sharing one {@link RenderContext} are used to render subtrees out of order,
 * which footnotes and table cells both require.
 */
public final class LatexVisitor extends AbstractVisitor {

    private static final Pattern ADM_BEGIN = Pattern.compile("data-adm=\"begin\"");
    private static final Pattern ADM_END = Pattern.compile("data-adm=\"end\"");
    private static final Pattern ADM_KIND = Pattern.compile("data-kind=\"([^\"]*)\"");
    private static final Pattern ADM_TITLE = Pattern.compile("data-title=\"([^\"]*)\"");

    /** Marks a Markua blurb that carries no heading of its own. */
    private static final Pattern ADM_UNTITLED = Pattern.compile("data-untitled=\"1\"");

    /** Markua inline index marker, planted by {@link MarkdownLoader}. */
    private static final Pattern INDEX_MARKER = Pattern.compile("data-index=\"([^\"]*)\"");

    /**
     * Characters makeindex reserves that cannot be escaped on the way through. The same set {@link
     * IndexTerms} rejects, less {@code !}, which an author writing a marker by hand uses
     * deliberately to make a sub-entry.
     */
    private static final String INDEX_RESERVED = "\"|@";

    /** Escape hatch: {@code <!-- latex: \clearpage -->} is emitted verbatim. */
    private static final Pattern RAW_LATEX =
            Pattern.compile("<!--\\s*latex:(.*?)-->", Pattern.DOTALL);

    private final RenderContext ctx;
    private final StringBuilder sb = new StringBuilder();

    /** Suppresses the blank line after a paragraph inside a tight list. */
    private boolean tightList;

    /** Current list nesting depth, checked against LaTeX's limit of four. */
    private int listDepth;

    /** Column count of the table being rendered, for validating body rows. */
    private int tableColumns;

    /** Set on sub-visitors so footnote bodies and link labels emit no index entries. */
    private boolean suppressIndex;

    /**
     * Index terms already emitted in the current section. Repeating an entry for every paragraph
     * turns a common word into an unreadable run of page numbers, so each term is recorded once per
     * section.
     */
    private final Set<String> indexedInSection = new HashSet<>();

    /**
     * Environments opened by an admonition or blurb begin-marker, so the matching end-marker closes
     * the right one. A deque rather than a field because it costs nothing and stays correct if
     * markers ever do nest.
     */
    private final Deque<String> openBlocks = new ArrayDeque<>();

    /**
     * Index entries from markers found inside the current heading. A sectioning command is a moving
     * argument, so they are held here and emitted after the closing brace with the ones matched
     * automatically.
     */
    private final List<String> headingIndexEntries = new ArrayList<>();

    /** True while rendering the text of a heading. */
    private boolean inHeading;

    /**
     * Decides whether each straight quote opens or closes. Stateful and carried across nodes,
     * because a quotation routinely spans them, and reset at the start of each block, where a mark
     * can only be an opening one.
     */
    private final Quotes quotes = new Quotes();

    public LatexVisitor(RenderContext ctx) {
        this.ctx = ctx;
    }

    public String result() {
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Blocks
    // ------------------------------------------------------------------

    @Override
    public void visit(Heading heading) {
        // What the reader will actually see: a manifest title override replaces
        // the Markdown heading, so index that instead. peek, never take, or the
        // one-shot override would be consumed before it is rendered.
        String shown = heading.getLevel() == 1 ? ctx.peekTitleOverride() : null;

        sb.append('\\').append(ctx.headingCommand(heading.getLevel())).append('{');
        String override = heading.getLevel() == 1 ? ctx.takeTitleOverride() : null;
        headingIndexEntries.clear();
        quotes.startBlock();
        if (override != null) {
            sb.append(LatexEscaper.text(override));
        } else {
            inHeading = true;
            visitChildren(heading);
            inHeading = false;
        }
        sb.append('}');

        // A chapter or section starts a new scope for index de-duplication.
        if (heading.getLevel() <= 2) {
            indexedInSection.clear();
        }

        List<IndexTerms.Term> hits = claimIndexTerms(shown != null ? shown : indexText(heading));
        if (!hits.isEmpty() || !headingIndexEntries.isEmpty()) {
            // After the closing brace, never inside it: a sectioning command is
            // a moving argument, and \index is fragile there. \nobreak undoes
            // the page break that the index whatsit would otherwise legalise
            // between a heading and its first line.
            sb.append("%\n");
            appendIndexEntries(hits);
            for (String entry : headingIndexEntries) {
                sb.append(entry);
            }
            headingIndexEntries.clear();
            sb.append("\\nobreak%");
        }
        sb.append("\n\n");
    }

    @Override
    public void visit(Paragraph paragraph) {
        quotes.startBlock();
        List<IndexTerms.Term> hits =
                indexingAllowed() ? claimIndexTerms(indexText(paragraph)) : List.of();
        if (!hits.isEmpty()) {
            // \leavevmode starts the paragraph first, so the \index whatsit
            // lands inside the first line box and records that line's page.
            // Without it the whatsit sits on the vertical list ahead of the
            // \parskip glue, where a page break can strand it on the previous
            // page and report a page number the word never appears on.
            sb.append("\\leavevmode");
            appendIndexEntries(hits);
            sb.append("%\n");
        }
        visitChildren(paragraph);
        sb.append(tightList ? "\n" : "\n\n");
    }

    @Override
    public void visit(BlockQuote blockQuote) {
        sb.append("\\begin{quote}\n");
        visitChildren(blockQuote);
        trimTrailingBlankLine();
        sb.append("\\end{quote}\n\n");
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {
        sb.append("\\par\\noindent\\hrulefill\\par\\medskip\n\n");
    }

    @Override
    public void visit(FencedCodeBlock code) {
        checkCharacters(code, code.getLiteral(), "code block", false);
        emitCodeBlock(code, code.getLiteral(), code.getInfo());
    }

    @Override
    public void visit(IndentedCodeBlock code) {
        checkCharacters(code, code.getLiteral(), "code block", false);
        emitCodeBlock(code, code.getLiteral(), null);
    }

    private void emitCodeBlock(Node node, String literal, String info) {
        String body = literal == null ? "" : literal;
        if (!body.endsWith("\n")) {
            body = body + "\n";
        }
        switch (ctx.codeStyle()) {
            case MINTED -> {
                String lang =
                        (info == null || info.isBlank())
                                ? "text"
                                : info.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                sb.append("\\begin{minted}{")
                        .append(lang)
                        .append("}\n")
                        .append(body)
                        .append("\\end{minted}\n\n");
            }
            case VERBATIM -> {
                if (body.contains("\\end{verbatim}")) {
                    ctx.error(
                            node,
                            "this code block contains the literal text \\end{verbatim}, "
                                    + "which would close the verbatim environment early and "
                                    + "break the build",
                            "set 'code: listings' in the manifest, which has no such "
                                    + "restriction, or remove that text from the code block");
                }
                sb.append("\\begin{verbatim}\n").append(body).append("\\end{verbatim}\n\n");
            }
            case LISTINGS -> {
                String lang = ListingsLanguages.resolve(info);
                sb.append("\\begin{lstlisting}");
                if (lang != null) {
                    sb.append("[language=").append(lang).append(']');
                }
                sb.append('\n').append(body).append("\\end{lstlisting}\n\n");
            }
        }
    }

    @Override
    public void visit(BulletList list) {
        emitList("itemize", list, list.isTight());
    }

    @Override
    public void visit(OrderedList list) {
        boolean prev = tightList;
        tightList = list.isTight();
        enterList(list);
        sb.append("\\begin{enumerate}\n");
        Integer start = list.getMarkerStartNumber();
        if (start != null && start != 1) {
            // Works without the enumitem package.
            sb.append("\\setcounter{enumi}{").append(start - 1).append("}\n");
        }
        visitChildren(list);
        sb.append("\\end{enumerate}\n\n");
        leaveList();
        tightList = prev;
    }

    private void emitList(String environment, ListBlock list, boolean tight) {
        boolean prev = tightList;
        tightList = tight;
        enterList(list);
        sb.append("\\begin{").append(environment).append("}\n");
        visitChildren(list);
        sb.append("\\end{").append(environment).append("}\n\n");
        leaveList();
        tightList = prev;
    }

    /** LaTeX's built-in list environments refuse to nest more than four deep. */
    private void enterList(Node list) {
        listDepth++;
        if (listDepth == LatexSafety.MAX_LIST_DEPTH + 1) {
            ctx.error(
                    list,
                    "list is nested "
                            + listDepth
                            + " levels deep, but LaTeX allows at "
                            + "most "
                            + LatexSafety.MAX_LIST_DEPTH
                            + " ('Too deeply nested' would stop the build)",
                    "flatten the deepest level, or promote it into its own subsection");
        }
    }

    private void leaveList() {
        listDepth--;
    }

    @Override
    public void visit(ListItem item) {
        sb.append("\\item ");
        visitChildren(item);
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }
    }

    @Override
    public void visit(HtmlBlock html) {
        String literal = html.getLiteral() == null ? "" : html.getLiteral();

        if (ADM_BEGIN.matcher(literal).find()) {
            beginAdmonition(literal);
            return;
        }
        if (ADM_END.matcher(literal).find()) {
            trimTrailingBlankLine();
            sb.append("\\end{")
                    .append(openBlocks.isEmpty() ? "admonition" : openBlocks.pop())
                    .append("}\n\n");
            return;
        }
        if (emitIndexMarkerIfPresent(html, literal)) {
            return;
        }
        emitRawLatexIfPresent(literal);
    }

    /**
     * Opens the environment a begin-marker asks for. A Markua {@code C>} block is centred rather
     * than framed, and a plain {@code B>} blurb has no heading of its own, so each gets its own
     * environment; everything else is the titled {@code admonition} that Docusaurus blocks already
     * use.
     */
    private void beginAdmonition(String literal) {
        String kind = LatexMarkerSink.unattr(group(ADM_KIND, literal, "note"));
        String title = group(ADM_TITLE, literal, null);
        boolean untitled = title == null && ADM_UNTITLED.matcher(literal).find();

        if (MarkdownLoader.KIND_CENTER.equalsIgnoreCase(kind) && untitled) {
            openBlocks.push("center");
            sb.append("\\begin{center}\n");
            return;
        }
        if (untitled) {
            openBlocks.push("admonitionplain");
            sb.append("\\begin{admonitionplain}\n");
            return;
        }
        String display =
                title != null && !title.isBlank()
                        ? LatexMarkerSink.unattr(title)
                        : capitalize(kind);
        openBlocks.push("admonition");
        sb.append("\\begin{admonition}{").append(LatexEscaper.text(display)).append("}\n");
    }

    @Override
    public void visit(HtmlInline html) {
        String literal = html.getLiteral();
        if (emitIndexMarkerIfPresent(html, literal)) {
            return;
        }
        emitRawLatexIfPresent(literal);
    }

    /**
     * Emits the {@code \index} entry behind a Markua {@code {i: "term"}} marker.
     *
     * <p>Unlike the terms declared in front matter, a marker is placed by hand at a spot the author
     * chose, so it is honoured everywhere - including inside footnotes and table cells, where
     * automatic matching is suppressed.
     *
     * @return true when the literal was a marker and has been dealt with
     */
    private boolean emitIndexMarkerIfPresent(Node node, String literal) {
        if (literal == null) {
            return false;
        }
        Matcher m = INDEX_MARKER.matcher(literal);
        if (!m.find()) {
            return false;
        }
        String term = IndexTerms.normalize(LatexMarkerSink.unattr(m.group(1)));
        if (term.isEmpty()) {
            ctx.error(
                    node,
                    "index marker has no term",
                    "write the term inside the braces, as in {i: \"tribe\"}");
            return true;
        }
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (INDEX_RESERVED.indexOf(c) >= 0) {
                ctx.error(
                        node,
                        "index marker '"
                                + term
                                + "' contains '"
                                + c
                                + "', which makeindex reserves",
                        "an index marker may not contain any of: "
                                + INDEX_RESERVED
                                + " . Use '!' to separate an entry from its sub-entry, "
                                + "as in {i: \"tribe!in-group\"}");
                return true;
            }
        }
        checkCharacters(node, term, "index marker");
        String entry = "\\index{" + LatexEscaper.indexEntry(term) + "}";
        if (inHeading) {
            // A sectioning command is a moving argument, where \index is
            // fragile. Hold the entry back and let visit(Heading) place it
            // after the closing brace.
            headingIndexEntries.add(entry);
        } else {
            sb.append(entry);
        }
        return true;
    }

    private void emitRawLatexIfPresent(String literal) {
        if (literal == null) {
            return;
        }
        Matcher m = RAW_LATEX.matcher(literal);
        if (m.find()) {
            sb.append(m.group(1).trim()).append('\n');
        }
        // Any other raw HTML has no LaTeX equivalent and is dropped.
    }

    @Override
    public void visit(LinkReferenceDefinition definition) {
        // Definitions produce no output of their own.
    }

    // ------------------------------------------------------------------
    // Inlines
    // ------------------------------------------------------------------

    @Override
    public void visit(Text text) {
        checkCharacters(text, text.getLiteral(), "text");
        // Punctuation an author cannot type directly is worked out first, so
        // that the result goes through the same CharacterMap translation as the
        // form an author who could type it would have written. Dashes before
        // quotes, because a dash is opening context for a quotation mark.
        String literal = Dashes.convert(text.getLiteral());
        sb.append(LatexEscaper.text(quotes.directional(literal)));
    }

    @Override
    public void visit(Emphasis emphasis) {
        wrap("\\emph{", emphasis);
    }

    @Override
    public void visit(StrongEmphasis emphasis) {
        wrap("\\textbf{", emphasis);
    }

    @Override
    public void visit(Code code) {
        checkCharacters(code, code.getLiteral(), "inline code");
        // A code span is content, not punctuation: a quote right after it is a
        // closing one. Its own straight quotes are left exactly as written.
        quotes.passThrough(code.getLiteral());
        sb.append("\\texttt{").append(LatexEscaper.text(code.getLiteral())).append('}');
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        quotes.lineBreak();
        sb.append('\n');
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        quotes.lineBreak();
        sb.append("\\\\\n");
    }

    @Override
    public void visit(Link link) {
        String dest = link.getDestination();
        String label = renderChildren(link);

        if (dest == null || dest.isBlank()) {
            sb.append(label);
            return;
        }
        if (!isExternal(dest)) {
            // A relative .md link or a bare anchor has no meaning in a PDF;
            // keep the wording and drop the unresolvable target.
            sb.append(label);
            return;
        }
        if (plainText(link).equals(dest)) {
            sb.append("\\url{").append(LatexEscaper.url(dest)).append('}');
        } else {
            sb.append("\\href{")
                    .append(LatexEscaper.url(dest))
                    .append("}{")
                    .append(label)
                    .append('}');
        }
    }

    @Override
    public void visit(Image image) {
        String dest = image.getDestination();

        if (dest == null || dest.isBlank()) {
            ctx.error(
                    image,
                    "image has no file path",
                    "give the image a path, as in ![caption](picture.png)");
            return;
        }
        if (isExternal(dest)) {
            ctx.error(
                    image,
                    "image points at a remote URL: " + dest,
                    "LaTeX cannot download images; save the file next to the "
                            + "Markdown and reference it by a relative path");
            return;
        }

        String extension = LatexSafety.extensionOf(dest);
        if (extension.isEmpty()) {
            ctx.error(
                    image,
                    "image file '" + dest + "' has no extension",
                    "add the file extension, for example " + dest + ".png");
            return;
        }
        if (!LatexSafety.isSupportedImageType(extension)) {
            ctx.error(
                    image,
                    "image '"
                            + dest
                            + "' is a ."
                            + extension
                            + " file, which pdflatex cannot include",
                    "convert it to one of: " + LatexSafety.supportedImageTypes());
            return;
        }

        String path = resolveImagePath(image, dest);
        String alt = plainText(image);

        if (isBlockImage(image)) {
            sb.append("\\begin{figure}[htbp]\n\\centering\n")
                    .append("\\includegraphics[width=0.9\\linewidth,keepaspectratio]{")
                    .append(LatexEscaper.imagePath(path))
                    .append("}\n");
            if (!alt.isBlank()) {
                sb.append("\\caption{").append(LatexEscaper.text(alt)).append("}\n");
            }
            sb.append("\\end{figure}\n");
        } else {
            sb.append("\\includegraphics[height=1em,keepaspectratio]{")
                    .append(LatexEscaper.imagePath(path))
                    .append('}');
        }
    }

    // ------------------------------------------------------------------
    // Extension nodes
    // ------------------------------------------------------------------

    @Override
    public void visit(CustomBlock customBlock) {
        if (customBlock instanceof TableBlock table) {
            emitTable(table);
        } else if (customBlock instanceof FootnoteDefinition) {
            // Rendered at the point of reference, not where it was defined.
            return;
        } else {
            visitChildren(customBlock);
        }
    }

    @Override
    public void visit(CustomNode customNode) {
        if (customNode instanceof Strikethrough s) {
            // \mdstrikeout is defined in the preamble; see Preamble.build.
            wrap("\\mdstrikeout{", s);
        } else if (customNode instanceof Ins ins) {
            wrap("\\underline{", ins);
        } else if (customNode instanceof TableHead || customNode instanceof TableBody) {
            visitChildren(customNode);
        } else if (customNode instanceof TableRow row) {
            checkRowWidth(row);
            visitChildren(customNode);
            sb.append(" \\\\ \\hline\n");
        } else if (customNode instanceof TableCell cell) {
            if (cell.getPrevious() != null) {
                sb.append(" & ");
            }
            // A cell holds inlines directly, with no paragraph to reset context.
            quotes.startBlock();
            if (cell.isHeader()) {
                wrap("\\textbf{", cell);
            } else {
                visitChildren(cell);
            }
        } else if (customNode instanceof TaskListItemMarker marker) {
            sb.append(marker.isChecked() ? "$\\boxtimes$ " : "$\\square$ ");
        } else if (customNode instanceof FootnoteReference ref) {
            emitFootnote(customNode, ref.getLabel());
        } else if (customNode instanceof InlineFootnote inline) {
            sb.append("\\footnote{").append(renderChildren(inline)).append('}');
        } else {
            visitChildren(customNode);
        }
    }

    private void emitFootnote(Node node, String label) {
        FootnoteDefinition def = ctx.footnote(label);
        if (def == null) {
            ctx.error(
                    node,
                    "footnote [^" + label + "] is referenced but never defined",
                    "add a definition somewhere in the same file, on its own line: "
                            + "[^"
                            + label
                            + "]: the footnote text");
            return;
        }
        String body = renderChildren(def);
        sb.append("\\footnote{").append(body).append('}');
    }

    private void emitTable(TableBlock table) {
        int columns = countColumns(table);
        if (columns == 0) {
            ctx.error(
                    table,
                    "table has no header row, so it cannot be rendered",
                    "give the table a header row and a delimiter row such as |---|---|");
            return;
        }
        int previousColumns = tableColumns;
        tableColumns = columns;
        sb.append("\\begin{table}[htbp]\n\\centering\n\\begin{tabular}{|")
                .append(columnSpec(table, columns))
                .append("}\n\\hline\n");
        visitChildren(table);
        sb.append("\\end{tabular}\n\\end{table}\n\n");
        tableColumns = previousColumns;
    }

    /**
     * A body row with more cells than the header would emit an extra {@code &}, which LaTeX reports
     * as "Extra alignment tab has been changed to \cr".
     */
    private void checkRowWidth(TableRow row) {
        int cells = 0;
        for (Node c = row.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof TableCell) {
                cells++;
            }
        }
        if (tableColumns > 0 && cells > tableColumns) {
            ctx.error(
                    row,
                    "table row has " + cells + " cells but the header defines only " + tableColumns,
                    "remove the extra cell(s), or add matching columns to the header row");
        }
    }

    private static int countColumns(TableBlock table) {
        TableRow row = firstHeaderRow(table);
        if (row == null) {
            return 0;
        }
        int count = 0;
        for (Node c = row.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof TableCell) {
                count++;
            }
        }
        return count;
    }

    /** Builds a spec like {@code l|c|r|} honouring the Markdown alignment row. */
    private static String columnSpec(TableBlock table, int columns) {
        StringBuilder spec = new StringBuilder();
        TableRow row = firstHeaderRow(table);
        Node cell = row == null ? null : row.getFirstChild();
        for (int i = 0; i < columns; i++) {
            char c = 'l';
            while (cell != null && !(cell instanceof TableCell)) {
                cell = cell.getNext();
            }
            if (cell instanceof TableCell tc && tc.getAlignment() != null) {
                c =
                        switch (tc.getAlignment()) {
                            case CENTER -> 'c';
                            case RIGHT -> 'r';
                            case LEFT -> 'l';
                        };
            }
            spec.append(c).append('|');
            if (cell != null) {
                cell = cell.getNext();
            }
        }
        return spec.toString();
    }

    private static TableRow firstHeaderRow(TableBlock table) {
        for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            if (section instanceof TableHead) {
                for (Node r = section.getFirstChild(); r != null; r = r.getNext()) {
                    if (r instanceof TableRow tr) {
                        return tr;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Rejects characters pdflatex cannot typeset, pointing at the exact column. Only the first
     * offender per string is reported, so one line of emoji does not bury the rest of the report.
     */
    private void checkCharacters(Node node, String literal, String where) {
        checkCharacters(node, literal, where, true);
    }

    /**
     * @param translate false inside verbatim code, where a LaTeX replacement would be printed
     *     literally instead of being obeyed
     */
    private void checkCharacters(Node node, String literal, String where, boolean translate) {
        int bad = LatexSafety.firstUnsupportedChar(literal, translate);
        if (bad < 0) {
            return;
        }
        int c = literal.codePointAt(bad);
        // Only offsets within the first line are meaningful for the caret.
        int newline = literal.indexOf('\n');
        int offset = (newline >= 0 && bad > newline) ? 0 : bad;
        String hint =
                !translate && CharacterMap.contains(c)
                        ? "this character is translated automatically in ordinary text, "
                                + "but a code block is set verbatim so no LaTeX command can "
                                + "be substituted; remove it or take it out of the code block"
                        : LatexSafety.hintFor(c);
        ctx.errorAt(
                node,
                offset,
                "the "
                        + where
                        + " contains "
                        + LatexSafety.describe(c)
                        + ", which pdflatex cannot typeset",
                hint);
    }

    private void wrap(String open, Node node) {
        sb.append(open);
        visitChildren(node);
        sb.append('}');
    }

    // ------------------------------------------------------------------
    // Index entries
    // ------------------------------------------------------------------

    /** Index entries belong in body prose, not in lists, tables or footnotes. */
    private boolean indexingAllowed() {
        return !suppressIndex && listDepth == 0 && tableColumns == 0;
    }

    /**
     * Terms matching {@code text} that have not already been recorded in this section. Claiming a
     * term marks it used, both for the once-per-section rule and for the book-wide "never matched
     * anything" warning.
     */
    private List<IndexTerms.Term> claimIndexTerms(String text) {
        IndexTerms terms = ctx.indexTerms();
        if (terms.isEmpty()) {
            return List.of();
        }
        List<IndexTerms.Term> claimed = new ArrayList<>();
        for (IndexTerms.Term term : terms.matching(text)) {
            terms.markUsed(term);
            if (indexedInSection.add(term.canonical().toLowerCase(Locale.ROOT))) {
                claimed.add(term);
            }
        }
        return claimed;
    }

    private void appendIndexEntries(List<IndexTerms.Term> terms) {
        for (IndexTerms.Term term : terms) {
            sb.append("\\index{").append(term.latex()).append('}');
        }
    }

    /**
     * The prose of a block, as the reader sees it, for index matching.
     *
     * <p>Deliberately not {@link #plainText}: that one is for alt text and autolink detection and
     * is wrong here in three ways. It contributes nothing for a line break, so {@code
     * moral\nrealism} would collapse to {@code moralrealism} and a phrase would stop matching
     * across a wrapped line. It includes code spans. And it includes image alt text and autolink
     * URLs, both of which the index must never see.
     */
    private static String indexText(Node parent) {
        StringBuilder out = new StringBuilder();
        collectIndexText(parent, out);
        return out.toString();
    }

    private static void collectIndexText(Node parent, StringBuilder out) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text t) {
                out.append(t.getLiteral());
            } else if (c instanceof SoftLineBreak || c instanceof HardLineBreak) {
                out.append(' ');
            } else if (c instanceof Code
                    || c instanceof Image
                    || c instanceof HtmlInline
                    || c instanceof FootnoteReference
                    || c instanceof InlineFootnote) {
                // Code, image paths and alt text, and footnote bodies are out of scope.
                out.append(' ');
            } else if (c instanceof Link link) {
                // A real link label is prose; a bare autolink is a URL.
                if (!plainText(link).equals(link.getDestination())) {
                    collectIndexText(link, out);
                } else {
                    out.append(' ');
                }
            } else {
                collectIndexText(c, out);
            }
        }
    }

    /** Renders a subtree with a fresh visitor sharing this context. */
    private String renderChildren(Node parent) {
        LatexVisitor sub = new LatexVisitor(ctx);
        sub.tightList = this.tightList;
        sub.listDepth = this.listDepth;
        sub.suppressIndex = true;
        sub.inHeading = this.inHeading;
        Node child = parent.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            child.accept(sub);
            child = next;
        }
        // An index marker inside, say, a link label in a heading is held back by
        // the sub-visitor; bring it out so the heading can place it.
        headingIndexEntries.addAll(sub.headingIndexEntries);
        return sub.result().strip();
    }

    /** Concatenates the literal text of a subtree, ignoring formatting. */
    private static String plainText(Node parent) {
        StringBuilder out = new StringBuilder();
        collectText(parent, out);
        return out.toString();
    }

    private static void collectText(Node parent, StringBuilder out) {
        for (Node c = parent.getFirstChild(); c != null; c = c.getNext()) {
            if (c instanceof Text t) {
                out.append(t.getLiteral());
            } else if (c instanceof Code code) {
                out.append(code.getLiteral());
            } else {
                collectText(c, out);
            }
        }
    }

    /**
     * True when the image is alone in its paragraph, in which case a floating figure is
     * appropriate. An image sitting in a sentence must stay inline.
     */
    private static boolean isBlockImage(Image image) {
        if (!(image.getParent() instanceof Paragraph paragraph)) {
            return false;
        }
        for (Node c = paragraph.getFirstChild(); c != null; c = c.getNext()) {
            if (c == image) {
                continue;
            }
            if (c instanceof Text t && t.getLiteral().isBlank()) {
                continue;
            }
            if (c instanceof SoftLineBreak) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static boolean isExternal(String dest) {
        if (dest == null) {
            return false;
        }
        String d = dest.toLowerCase(Locale.ROOT);
        return d.startsWith("http://")
                || d.startsWith("https://")
                || d.startsWith("mailto:")
                || d.startsWith("ftp://")
                || d.startsWith("//");
    }

    /**
     * Rewrites a relative image reference so it still resolves once the .tex file is written
     * somewhere other than beside its Markdown source.
     */
    private String resolveImagePath(Node node, String dest) {
        String decoded = percentDecode(dest);
        try {
            Path absolute = ctx.sourceDir().resolve(decoded).normalize();
            if (!Files.isRegularFile(absolute)) {
                ctx.error(
                        node,
                        "image file not found: " + absolute,
                        "check the spelling and the path; it is resolved relative to "
                                + ctx.sourceFile().getFileName());
            }
            return ctx.outputDir()
                    .toAbsolutePath()
                    .normalize()
                    .relativize(absolute.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (IllegalArgumentException e) {
            // Different roots on Windows; an absolute path is the best we can do.
            return ctx.sourceDir().resolve(decoded).normalize().toString().replace('\\', '/');
        }
    }

    /**
     * Decodes %XX sequences without the {@code +}-means-space rule that {@code URLDecoder} applies,
     * which would corrupt real filenames. Consecutive escapes are gathered so multi-byte UTF-8
     * decodes correctly.
     */
    static String percentDecode(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf('%') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        ByteArrayOutputStream pending = new ByteArrayOutputStream();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '%'
                    && i + 3 <= s.length()
                    && isHex(s.charAt(i + 1))
                    && isHex(s.charAt(i + 2))) {
                pending.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 3;
            } else {
                if (pending.size() > 0) {
                    out.append(pending.toString(StandardCharsets.UTF_8));
                    pending.reset();
                }
                out.append(s.charAt(i));
                i++;
            }
        }
        if (pending.size() > 0) {
            out.append(pending.toString(StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private void trimTrailingBlankLine() {
        while (sb.length() >= 2
                && sb.charAt(sb.length() - 1) == '\n'
                && sb.charAt(sb.length() - 2) == '\n') {
            sb.setLength(sb.length() - 1);
        }
    }

    private static String group(Pattern p, String s, String fallback) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : fallback;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
