package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Hyphen, en dash and em dash, which TeX tells apart by how many hyphens are written. */
class DashesTest {

    private final List<Problem> problems = new ArrayList<>();
    private final Path here = Paths.get(".").toAbsolutePath().normalize();
    private final Path sourceFile = here.resolve("test.md");

    private String convert(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems)
                .convert(markdown, sourceFile, here)
                .latex();
    }

    // ------------------------------------------------------------------
    // The hyphen, which stays a hyphen
    // ------------------------------------------------------------------

    @Test
    void aHyphenInAWordIsLeftAlone() {
        String tex = convert("A well-known twenty-one-year-old rule.\n");
        assertTrue(tex.contains("well-known twenty-one-year-old"), tex);
        assertFalse(tex.contains("--"), tex);
    }

    @Test
    void aHyphenWithALetterOnEitherSideOfADigitIsLeftAlone() {
        // Both sides must be numeric, so a part number is not a range.
        assertEquals("A-1", Dashes.convert("A-1"));
        assertEquals("1-A", Dashes.convert("1-A"));
        assertEquals("catch-22", Dashes.convert("catch-22"));
    }

    @Test
    void aLeadingOrTrailingHyphenIsLeftAlone() {
        assertEquals("-5", Dashes.convert("-5"));
        assertEquals("5-", Dashes.convert("5-"));
    }

    // ------------------------------------------------------------------
    // The en dash: a numeric range
    // ------------------------------------------------------------------

    @Test
    void aNumericRangeBecomesAnEnDash() {
        assertTrue(convert("See pages 3-5 for this.\n").contains("pages 3--5 for"));
    }

    @Test
    void aRangeOfMultiDigitNumbersBecomesAnEnDash() {
        assertEquals("2020--2021", Dashes.convert("2020-2021"));
        assertEquals("pp. 114--117.", Dashes.convert("pp. 114-117."));
    }

    @Test
    void severalRangesInOneRunAreAllConverted() {
        assertEquals("1--2, 3--4 and 5--6", Dashes.convert("1-2, 3-4 and 5-6"));
    }

    // ------------------------------------------------------------------
    // The em dash
    // ------------------------------------------------------------------

    @Test
    void twoHyphensBecomeAnEmDash() {
        assertTrue(convert("He said -- and I quote -- that.\n").contains("said --- and"));
    }

    @Test
    void twoHyphensBetweenWordsBecomeAnEmDash() {
        assertEquals("word---word", Dashes.convert("word--word"));
    }

    @Test
    void aUnicodeEmDashBecomesThreeHyphens() {
        assertTrue(convert("He said — and I quote — that.\n").contains("said --- and"));
    }

    @Test
    void aUnicodeEnDashBecomesTwoHyphens() {
        assertTrue(convert("See pages 3–5 here.\n").contains("pages 3--5 here"));
    }

    @Test
    void threeHyphensAreAlreadyAnEmDashAndAreLeftAlone() {
        assertEquals("word---word", Dashes.convert("word---word"));
    }

    // ------------------------------------------------------------------
    // One pass, so nothing is read twice
    // ------------------------------------------------------------------

    @Test
    void aRangeIsNotThenReReadAsAnEmDash() {
        // 3-5 becomes 3--5 and must stop there; a second pass would make it ---.
        assertEquals("3--5", Dashes.convert("3-5"));
        assertTrue(convert("pages 3-5.\n").contains("3--5"), convert("pages 3-5.\n"));
        assertFalse(convert("pages 3-5.\n").contains("3---5"));
    }

    @Test
    void twoHyphensBetweenNumeralsAreAnEmDashNotARange() {
        // The range rule needs a single hyphen; two is the author asking for an
        // em dash explicitly, wherever it sits.
        assertEquals("3---5", Dashes.convert("3--5"));
    }

    // ------------------------------------------------------------------
    // Code keeps its hyphens
    // ------------------------------------------------------------------

    @Test
    void hyphensInsideInlineCodeAreLeftAlone() {
        String tex = convert("Run `git log --oneline` now.\n");
        assertTrue(tex.contains("\\texttt{git log --oneline}"), tex);
        assertFalse(tex.contains("---oneline"), tex);
    }

    @Test
    void hyphensInsideAFencedBlockAreLeftAlone() {
        String tex = convert("```\nls --all\npages 3-5\n```\n");
        assertTrue(tex.contains("ls --all"), tex);
        assertTrue(tex.contains("pages 3-5"), tex);
    }

    @Test
    void hyphensInAUrlAreLeftAlone() {
        String tex = convert("See [it](https://example.com/a--b-3-5).\n");
        assertTrue(tex.contains("https://example.com/a--b-3-5"), tex);
    }

    // ------------------------------------------------------------------
    // Interaction with the other punctuation rules
    // ------------------------------------------------------------------

    @Test
    void anEmDashIsStillOpeningContextForAQuotation() {
        String tex = convert("He turned--\"who is there?\"\n");
        assertTrue(tex.contains("---``who is there?''"), tex);
    }

    @Test
    void textWithNoHyphenIsReturnedUnchanged() {
        String s = "nothing to do here";
        assertEquals(s, Dashes.convert(s));
    }
}
