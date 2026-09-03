package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Md2LatexTest {

    // These tests assert on the shape of the generated LaTeX. Problems raised
    // along the way are collected and ignored here; ConversionErrorTest covers
    // them.
    private final List<Problem> problems = new ArrayList<>();
    private final Path here = Paths.get(".").toAbsolutePath().normalize();
    private final Path sourceFile = here.resolve("test.md");

    private String convert(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    private String convertAsBook(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, true, problems)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    @Test
    void headingsMapToArticleSectioning() {
        assertTrue(convert("# Top").contains("\\section{Top}"));
        assertTrue(convert("## Second").contains("\\subsection{Second}"));
        assertTrue(convert("###### Sixth").contains("\\subparagraph{Sixth}"));
    }

    @Test
    void headingsMapToChapterInBookMode() {
        assertTrue(convertAsBook("# Top").contains("\\chapter{Top}"));
        assertTrue(convertAsBook("## Second").contains("\\section{Second}"));
    }

    @Test
    void emphasisAndStrong() {
        assertTrue(convert("*a* and **b**").contains("\\emph{a}"));
        assertTrue(convert("*a* and **b**").contains("\\textbf{b}"));
    }

    @Test
    void specialCharactersInProseAreEscaped() {
        String tex = convert("Costs 50% of R&D, see file_name.txt");
        assertTrue(tex.contains("50\\%"));
        assertTrue(tex.contains("R\\&D"));
        assertTrue(tex.contains("file\\_name.txt"));
    }

    @Test
    void codeBlockContentIsNotEscaped() {
        String tex = convert("```java\nMap<String, Integer> m = new HashMap<>();\n```");
        assertTrue(tex.contains("\\begin{lstlisting}[language=Java]"));
        assertTrue(tex.contains("Map<String, Integer> m = new HashMap<>();"));
        assertFalse(tex.contains("\\_"));
    }

    @Test
    void unknownCodeLanguageIsOmittedRatherThanBreakingTheBuild() {
        // listings has no JSON, and passing language=json is a hard LaTeX error.
        String tex = convert("```json\n{\"a\": 1}\n```");
        assertTrue(tex.contains("\\begin{lstlisting}\n"));
        assertFalse(tex.contains("language=json"));
    }

    @Test
    void inlineCodeIsEscapedInsideTexttt() {
        assertTrue(convert("use `a_b` here").contains("\\texttt{a\\_b}"));
    }

    @Test
    void bulletAndOrderedLists() {
        String tex = convert("- one\n- two\n");
        assertTrue(tex.contains("\\begin{itemize}"));
        assertTrue(tex.contains("\\item one"));

        String ordered = convert("1. first\n2. second\n");
        assertTrue(ordered.contains("\\begin{enumerate}"));
    }

    @Test
    void orderedListWithNonStandardStart() {
        String tex = convert("5. five\n6. six\n");
        assertTrue(tex.contains("\\setcounter{enumi}{4}"));
    }

    @Test
    void blockQuote() {
        assertTrue(convert("> quoted").contains("\\begin{quote}"));
    }

    @Test
    void externalLinkUsesHref() {
        String tex = convert("[text](https://example.test/p)");
        assertTrue(tex.contains("\\href{https://example.test/p}{text}"));
    }

    @Test
    void bareUrlUsesUrlCommand() {
        String tex = convert("<https://example.test/p>");
        assertTrue(tex.contains("\\url{https://example.test/p}"));
    }

    @Test
    void internalMarkdownLinkKeepsTextAndDropsTarget() {
        String tex = convert("see [chapter two](./two.md) now");
        assertTrue(tex.contains("chapter two"));
        assertFalse(tex.contains("\\href"));
    }

    @Test
    void blockImageBecomesFigureWithCaption() {
        String tex = convert("![A caption](pic.png)");
        assertTrue(tex.contains("\\begin{figure}"));
        assertTrue(tex.contains("\\caption{A caption}"));
        assertTrue(tex.contains("{pic}.png"));
    }

    @Test
    void inlineImageDoesNotOpenAFigure() {
        String tex = convert("before ![x](pic.png) after");
        assertFalse(tex.contains("\\begin{figure}"));
        assertTrue(tex.contains("\\includegraphics"));
    }

    @Test
    void tableProducesTabularWithAlignment() {
        String tex =
                convert(
                        """
                | Left | Center | Right |
                |:-----|:------:|------:|
                | a    | b      | c     |
                """);
        assertTrue(tex.contains("\\begin{tabular}{|l|c|r|}"));
        assertTrue(tex.contains("\\textbf{Left}"));
        assertTrue(tex.contains("a & b & c \\\\ \\hline"));
    }

    @Test
    void strikethroughUsesTheSelfContainedMacroNotUlem() {
        String tex = convert("~~gone~~");
        assertTrue(tex.contains("\\mdstrikeout{gone}"));
        assertFalse(tex.contains("\\sout"));
    }

    @Test
    void taskListMarkers() {
        String tex = convert("- [x] done\n- [ ] todo\n");
        assertTrue(tex.contains("$\\boxtimes$"));
        assertTrue(tex.contains("$\\square$"));
    }

    @Test
    void footnoteIsInlinedAtThePointOfReference() {
        String tex = convert("Claim[^1]\n\n[^1]: The evidence.\n");
        assertTrue(tex.contains("\\footnote{The evidence.}"));
    }

    @Test
    void admonitionBecomesEnvironment() {
        String tex = convert(":::tip[Key Takeaway]\n\nBody.\n\n:::\n");
        assertTrue(tex.contains("\\begin{admonition}{Key Takeaway}"));
        assertTrue(tex.contains("Body."));
        assertTrue(tex.contains("\\end{admonition}"));
    }

    @Test
    void admonitionWithoutTitleUsesCapitalizedKind() {
        String tex = convert(":::note\nBody.\n:::\n");
        assertTrue(tex.contains("\\begin{admonition}{Note}"));
    }

    @Test
    void rawLatexEscapeHatch() {
        assertTrue(convert("<!-- latex: \\clearpage -->").contains("\\clearpage"));
    }

    @Test
    void thematicBreak() {
        assertTrue(convert("---\n").contains("\\hrulefill"));
    }

    @Test
    void percentDecodeHandlesMultiByteSequences() {
        assertEquals("café", LatexVisitor.percentDecode("caf%C3%A9"));
        assertEquals("a b", LatexVisitor.percentDecode("a%20b"));
        assertEquals("a+b", LatexVisitor.percentDecode("a+b"));
    }
}
