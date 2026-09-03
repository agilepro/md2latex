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
 * Quotation marks: the directional characters translate one for one, and the straight one is made
 * directional first according to where it sits.
 */
class QuotesTest {

    private final List<Problem> problems = new ArrayList<>();
    private final Path here = Paths.get(".").toAbsolutePath().normalize();
    private final Path sourceFile = here.resolve("test.md");

    private String convert(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    // ------------------------------------------------------------------
    // Directional characters, translated one for one
    // ------------------------------------------------------------------

    @Test
    void directionalDoubleQuotesBecomeBacktickAndApostrophePairs() {
        String tex = convert("He said “hello” loudly.\n");
        assertTrue(tex.contains("``hello''"), tex);
        assertFalse(tex.contains("“"), tex);
        assertFalse(tex.contains("”"), tex);
    }

    @Test
    void directionalSingleQuotesBecomeSingleBacktickAndApostrophe() {
        String tex = convert("He said ‘hello’ quietly.\n");
        assertTrue(tex.contains("`hello'"), tex);
        assertFalse(tex.contains("‘"), tex);
        assertFalse(tex.contains("’"), tex);
    }

    @Test
    void aDirectionalApostropheBecomesAPlainOne() {
        assertTrue(convert("Don’t stop.\n").contains("Don't stop."));
    }

    // ------------------------------------------------------------------
    // The straight quote, made directional by position
    // ------------------------------------------------------------------

    @Test
    void aStraightQuoteOpensAtTheStartOfAParagraph() {
        assertTrue(convert("\"Hello,\" she said.\n").contains("``Hello,''"));
    }

    @Test
    void aStraightQuoteOpensAfterASpaceAndClosesAfterAWord() {
        String tex = convert("He said \"hello\" loudly.\n");
        assertTrue(tex.contains("``hello''"), tex);
    }

    @Test
    void severalQuotationsInOneSentenceAlternateCorrectly() {
        String tex = convert("\"Hello,\" she said, \"and goodbye.\"\n");
        assertTrue(tex.contains("``Hello,''"), tex);
        assertTrue(tex.contains("``and goodbye.''"), tex);
    }

    @Test
    void aStraightQuoteOpensAfterAnOpeningBracket() {
        assertTrue(convert("A remark (\"an aside\") here.\n").contains("(``an aside'')"));
    }

    @Test
    void aQuotationSpanningEmphasisStillOpensAndCloses() {
        // The two marks are in different Text nodes with a StrongEmphasis
        // between them, so the context has to survive the crossing.
        String tex = convert("A \"quote with **bold** inside\" here.\n");
        assertTrue(tex.contains("``quote with \\textbf{bold} inside''"), tex);
    }

    @Test
    void aQuoteOpeningInsideEmphasisIsStillAnOpeningOne() {
        assertTrue(convert("*\"emphasised\"*\n").contains("\\emph{``emphasised''}"));
    }

    @Test
    void aQuoteAfterALineBreakOpens() {
        String tex = convert("A sentence and\n\"a quotation\" after it.\n");
        assertTrue(tex.contains("``a quotation''"), tex);
    }

    @Test
    void aQuoteAfterADigitCloses() {
        // A measurement: a closing mark is the conventional stand-in for inches.
        assertTrue(convert("A 6\" pipe.\n").contains("6'' pipe"));
    }

    @Test
    void everyParagraphStartsFresh() {
        // An unbalanced quote in one paragraph must not leave the next inverted.
        String tex = convert("A \"dangling quotation.\n\n\"A new one\" here.\n");
        assertTrue(tex.contains("``A new one''"), tex);
    }

    @Test
    void aQuoteAtTheStartOfATableCellOpens() {
        String tex =
                convert(
                        """
                | A | B |
                |---|---|
                | "quoted" | plain |
                """);
        assertTrue(tex.contains("``quoted''"), tex);
    }

    // ------------------------------------------------------------------
    // Nesting
    // ------------------------------------------------------------------

    @Test
    void aDirectionalQuotationNestsInsideAnother() {
        String tex = convert("“He said ‘hello’ to me”\n");
        assertTrue(tex.contains("``He said `hello' to me''"), tex);
    }

    @Test
    void aStraightQuotationNestsInsideAnother() {
        // Inside a double-quoted phrase a single quote in opening position is
        // read as a nested quotation rather than an apostrophe.
        String tex = convert("\"He said 'hello' to me\"\n");
        assertTrue(tex.contains("``He said `hello' to me''"), tex);
    }

    @Test
    void marksThatMeetAtTheEndAreSeparatedByAThinSpace() {
        // Without the thin space, ''' ligates greedily into '' + ' and prints
        // the closing double quote before the closing single one.
        assertTrue(convert("“he said ‘hello’”\n").contains("`hello'\\,''"));
        assertTrue(convert("\"he said 'hello'\"\n").contains("`hello'\\,''"));
    }

    @Test
    void marksThatMeetAtTheStartAreSeparatedByAThinSpace() {
        assertTrue(convert("“‘hello’ he said”\n").contains("``\\,`hello'"));
        assertTrue(convert("\"'hello' he said\"\n").contains("``\\,`hello'"));
    }

    @Test
    void aNestedQuotationSurvivesEmphasisBetweenTheMarks() {
        String tex = convert("\"He said *'hello'* to me\"\n");
        assertTrue(tex.contains("\\emph{`hello'}"), tex);
    }

    @Test
    void aSingleQuoteOutsideAnyQuotationStaysAnApostrophe() {
        // The nesting rule must not reach prose that is not quoted at all.
        String tex = convert("The '90s were good, 'twas said.\n");
        assertTrue(tex.contains("'90s"), tex);
        assertTrue(tex.contains("'twas"), tex);
    }

    @Test
    void aDecadeKeepsItsApostropheEvenInsideAQuotation() {
        assertTrue(convert("\"Back in the '90s,\" he said.\n").contains("the '90s,"));
    }

    @Test
    void anApostropheInsideAQuotationIsNotMistakenForANestedMark() {
        String tex = convert("\"Don't touch Jones' book,\" she said.\n");
        assertTrue(tex.contains("Don't"), tex);
        assertTrue(tex.contains("Jones'"), tex);
    }

    // ------------------------------------------------------------------
    // What is left alone
    // ------------------------------------------------------------------

    @Test
    void straightQuotesInsideInlineCodeAreUntouched() {
        String tex = convert("Write `say \"hi\"` in the shell.\n");
        assertTrue(tex.contains("\\texttt{say \"hi\"}"), tex);
        assertFalse(tex.contains("``"), tex);
    }

    @Test
    void straightQuotesInsideAFencedBlockAreUntouched() {
        String tex = convert("```\nprintf(\"hi\");\n```\n");
        assertTrue(tex.contains("printf(\"hi\");"), tex);
    }

    @Test
    void aCodeSpanCountsAsAWordSoTheNextQuoteCloses() {
        String tex = convert("A \"quotation with `code` inside\" here.\n");
        assertTrue(tex.contains("inside''"), tex);
    }

    @Test
    void theStraightSingleQuoteIsLeftAsAnApostrophe() {
        // TeX already sets ' as a right single quote, which is what an
        // apostrophe needs; guessing at opening quotes would break these.
        String tex = convert("Don't touch the '90s, nor Jones' book.\n");
        assertTrue(tex.contains("Don't"), tex);
        assertTrue(tex.contains("'90s"), tex);
        assertTrue(tex.contains("Jones'"), tex);
    }

    @Test
    void quotesInAUrlAreNotDisturbed() {
        String tex = convert("See [the page](https://example.com/a\"b).\n");
        assertTrue(tex.contains("https://example.com/a\"b"), tex);
    }

    // ------------------------------------------------------------------
    // The positional rule on its own
    // ------------------------------------------------------------------

    @Test
    void directionalChoosesByPrecedingCharacter() {
        assertEquals("“a", curl("\"a"));
        assertEquals("“a", curl(" ", "\"a"));
        assertEquals("“a", curl("(", "\"a"));
        assertEquals("”a", curl("x", "\"a"));
        assertEquals("”a", curl(".", "\"a"));
        assertEquals("”a", curl("6", "\"a"));
    }

    @Test
    void textWithNoStraightQuoteIsReturnedUnchanged() {
        String s = "nothing to do here";
        assertEquals(s, curl(s));
    }

    /** Curls one run starting fresh at a block. */
    private static String curl(String text) {
        Quotes q = new Quotes();
        q.startBlock();
        return q.directional(text);
    }

    /** Curls {@code text} as though {@code before} had just been emitted. */
    private static String curl(String before, String text) {
        Quotes q = new Quotes();
        q.startBlock();
        q.directional(before);
        return q.directional(text);
    }
}
