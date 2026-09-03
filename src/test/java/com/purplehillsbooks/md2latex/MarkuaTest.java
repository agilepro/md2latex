package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Markua blurbs and inline index markers, which are recognised only when the manifest asks for
 * {@code dialect: markua}.
 */
class MarkuaTest {

    private final List<Problem> problems = new ArrayList<>();
    private final Path here = Paths.get(".").toAbsolutePath().normalize();
    private final Path sourceFile = here.resolve("test.md");

    private String convert(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems, IndexTerms.none(), Dialect.MARKUA)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    private String convertPlain(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    private String convertAsBook(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, true, problems, IndexTerms.none(), Dialect.MARKUA)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    private String errors() {
        StringBuilder b = new StringBuilder();
        for (Problem p : problems) {
            if (p.isError()) {
                b.append(p.message()).append('\n');
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------
    // Line-prefix blurbs
    // ------------------------------------------------------------------

    @Test
    void asidePrefixBecomesATitledAdmonition() {
        String tex =
                convert(
                        """
                A> This is an aside.
                A> It runs to here.

                Ordinary text.
                """);
        assertTrue(tex.contains("\\begin{admonition}{Aside}"), tex);
        assertTrue(tex.contains("This is an aside."), tex);
        assertTrue(tex.contains("\\end{admonition}"), tex);
        // The unprefixed paragraph is outside the blurb.
        assertTrue(tex.indexOf("\\end{admonition}") < tex.indexOf("Ordinary text."), tex);
    }

    @Test
    void everyBlurbLetterGetsItsOwnHeading() {
        assertTrue(convert("W> careful\n").contains("\\begin{admonition}{Warning}"));
        assertTrue(convert("T> handy\n").contains("\\begin{admonition}{Tip}"));
        assertTrue(convert("E> wrong\n").contains("\\begin{admonition}{Error}"));
        assertTrue(convert("I> note\n").contains("\\begin{admonition}{Information}"));
        assertTrue(convert("Q> why?\n").contains("\\begin{admonition}{Question}"));
        assertTrue(convert("D> debate\n").contains("\\begin{admonition}{Discussion}"));
        assertTrue(convert("X> try it\n").contains("\\begin{admonition}{Exercise}"));
    }

    @Test
    void genericBlurbHasNoHeading() {
        String tex = convert("B> Just a blurb.\n");
        assertTrue(tex.contains("\\begin{admonitionplain}"), tex);
        assertTrue(tex.contains("\\end{admonitionplain}"), tex);
        assertFalse(tex.contains("\\begin{admonition}"), tex);
    }

    @Test
    void centerPrefixCentresRatherThanFrames() {
        String tex = convert("C> Centred line.\n");
        assertTrue(tex.contains("\\begin{center}"), tex);
        assertTrue(tex.contains("\\end{center}"), tex);
    }

    @Test
    void blankPrefixedLineSeparatesParagraphsInsideOneBlurb() {
        String tex =
                convert(
                        """
                A> First paragraph.
                A>
                A> Second paragraph.
                """);
        assertEquals(1, count(tex, "\\begin{admonition}{Aside}"), tex);
        assertTrue(tex.contains("First paragraph."), tex);
        assertTrue(tex.contains("Second paragraph."), tex);
    }

    @Test
    void adjacentBlurbsOfDifferentKindsDoNotMerge() {
        String tex =
                convert(
                        """
                W> Careful.
                T> Handy.
                """);
        assertTrue(tex.contains("\\begin{admonition}{Warning}"), tex);
        assertTrue(tex.contains("\\begin{admonition}{Tip}"), tex);
        assertEquals(2, count(tex, "\\end{admonition}"), tex);
    }

    @Test
    void blurbContentIsStillMarkdown() {
        String tex = convert("A> Some **bold** and a [link](https://example.com).\n");
        assertTrue(tex.contains("\\textbf{bold}"), tex);
        assertTrue(tex.contains("\\href{https://example.com}"), tex);
    }

    @Test
    void fencedCodeInsideABlurbSurvives() {
        String tex =
                convert(
                        """
                A> Before.
                A> ```
                A> W> not a blurb
                A> ```
                A> After.
                """);
        assertEquals(1, count(tex, "\\begin{admonition}{Aside}"), tex);
        assertTrue(tex.contains("\\begin{lstlisting}"), tex);
        assertTrue(tex.contains("W> not a blurb"), tex);
        assertFalse(tex.contains("\\begin{admonition}{Warning}"), tex);
    }

    @Test
    void aBlurbLeftOpenAtEndOfFileIsClosed() {
        String tex = convert("A> dangling\n");
        assertEquals(1, count(tex, "\\begin{admonition}{Aside}"), tex);
        assertEquals(1, count(tex, "\\end{admonition}"), tex);
    }

    // ------------------------------------------------------------------
    // Fenced blurbs
    // ------------------------------------------------------------------

    @Test
    void fencedBlurbUsesItsClass() {
        String tex =
                convert(
                        """
                {blurb, class: warning}
                Mind the gap.
                {/blurb}
                """);
        assertTrue(tex.contains("\\begin{admonition}{Warning}"), tex);
        assertTrue(tex.contains("Mind the gap."), tex);
        assertTrue(tex.contains("\\end{admonition}"), tex);
    }

    @Test
    void classlessFencedBlurbHasNoHeadingButAnAsideDoes() {
        assertTrue(convert("{blurb}\nText.\n{/blurb}\n").contains("\\begin{admonitionplain}"));
        assertTrue(convert("{aside}\nText.\n{/aside}\n").contains("\\begin{admonition}{Aside}"));
    }

    @Test
    void fencedBlurbAcceptsAnExplicitTitle() {
        String tex =
                convert(
                        """
                {blurb, class: tip, title: "Key Takeaway"}
                Text.
                {/blurb}
                """);
        assertTrue(tex.contains("\\begin{admonition}{Key Takeaway}"), tex);
    }

    @Test
    void asideCloserAlsoClosesABlurb() {
        // Markua treats the two as one construct; being strict here would only
        // reject documents that render fine.
        String tex = convert("{blurb, class: tip}\nText.\n{/aside}\nAfter.\n");
        assertEquals(1, count(tex, "\\end{admonition}"), tex);
        assertTrue(tex.indexOf("\\end{admonition}") < tex.indexOf("After."), tex);
    }

    // ------------------------------------------------------------------
    // Index markers
    // ------------------------------------------------------------------

    @Test
    void indexMarkerBecomesAnIndexEntry() {
        String tex = convert("The tribe{i: \"tribe\"} gathered.\n");
        assertTrue(tex.contains("\\index{tribe}"), tex);
        assertTrue(tex.contains("The tribe"), tex);
        assertFalse(tex.contains("{i:"), tex);
    }

    @Test
    void indexMarkerAcceptsUnquotedAndSingleQuotedTerms() {
        assertTrue(convert("x {i: tribe} y\n").contains("\\index{tribe}"));
        assertTrue(convert("x {i: 'moral realism'} y\n").contains("\\index{moral realism}"));
    }

    @Test
    void exclamationMakesASubEntry() {
        assertTrue(convert("x {i: \"tribe!in-group\"} y\n").contains("\\index{tribe!in-group}"));
    }

    @Test
    void indexTermIsLatexEscaped() {
        assertTrue(convert("x {i: \"R&D\"} y\n").contains("\\index{R\\&D}"));
        assertTrue(convert("x {i: \"a_b\"} y\n").contains("\\index{a\\_b}"));
    }

    @Test
    void indexMarkerInAHeadingLandsAfterTheClosingBrace() {
        String tex = convertAsBook("# The Tribe{i: \"tribe\"}\n");
        int brace = tex.indexOf("}");
        assertTrue(tex.startsWith("\\chapter{The Tribe}"), tex);
        assertTrue(tex.indexOf("\\index{tribe}") > brace, tex);
    }

    @Test
    void indexMarkerSurvivesInsideAFootnote() {
        String tex = convert("Text.[^a]\n\n[^a]: A note about the tribe{i: \"tribe\"}.\n");
        assertTrue(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void indexMarkerInsideACodeSpanIsLeftAlone() {
        String tex = convert("Write `{i: \"tribe\"}` to index it.\n");
        assertFalse(tex.contains("\\index{"), tex);
        assertTrue(tex.contains("\\texttt{"), tex);
    }

    @Test
    void indexMarkerInsideAFencedBlockIsLeftAlone() {
        String tex = convert("```\n{i: \"tribe\"}\n```\n");
        assertFalse(tex.contains("\\index{"), tex);
        assertTrue(tex.contains("{i: \"tribe\"}"), tex);
    }

    @Test
    void escapedIndexMarkerIsLeftAlone() {
        String tex = convert("A literal \\{i: \"tribe\"} marker.\n");
        assertFalse(tex.contains("\\index{"), tex);
    }

    @Test
    void reservedCharacterInAnIndexMarkerIsAnError() {
        convert("x {i: \"a|b\"} y\n");
        assertTrue(errors().contains("makeindex reserves"), errors());
    }

    @Test
    void emptyIndexMarkerIsAnError() {
        convert("x {i: \"\"} y\n");
        assertTrue(errors().contains("no term"), errors());
    }

    // ------------------------------------------------------------------
    // Dialect gating
    // ------------------------------------------------------------------

    @Test
    void markuaSyntaxIsInertUnderTheDefaultDialect() {
        String tex = convertPlain("A> Not a blurb.\n\nAnd a {i: \"tribe\"} marker.\n");
        assertFalse(tex.contains("\\begin{admonition}"), tex);
        assertFalse(tex.contains("\\index{"), tex);
        assertTrue(tex.contains("A"), tex);
        assertTrue(tex.contains("tribe"), tex);
    }

    @Test
    void docusaurusAdmonitionsStillWorkUnderMarkua() {
        String tex = convert(":::tip[Key Takeaway]\n\nBody.\n\n:::\n");
        assertTrue(tex.contains("\\begin{admonition}{Key Takeaway}"), tex);
        assertTrue(tex.contains("\\end{admonition}"), tex);
    }

    @Test
    void markuaSyntaxInsideAFencedBlockIsLeftAlone() {
        String tex = convert("```\nA> not a blurb\n{blurb}\n```\n");
        assertFalse(tex.contains("\\begin{admonition}"), tex);
        assertTrue(tex.contains("A> not a blurb"), tex);
    }

    @Test
    void aPrefixMustStartInColumnOne() {
        // An indented A> is content, not a blurb: Markua requires column one,
        // and loosening it would swallow list continuations and code.
        assertFalse(convert("  A> indented\n").contains("\\begin{admonition}"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
