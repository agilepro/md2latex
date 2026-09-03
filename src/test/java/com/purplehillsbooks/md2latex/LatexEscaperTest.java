package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LatexEscaperTest {

    @Test
    void escapesTheSimpleSpecials() {
        assertEquals("100\\% \\& \\#1 \\$5 a\\_b", LatexEscaper.text("100% & #1 $5 a_b"));
    }

    @Test
    void escapesBracesAndBackslashFirst() {
        // The backslash must not be re-escaped by the brace replacements.
        assertEquals("\\textbackslash{}\\{x\\}", LatexEscaper.text("\\{x}"));
    }

    @Test
    void escapesTildeAndCaret() {
        assertEquals("\\textasciitilde{}\\textasciicircum{}", LatexEscaper.text("~^"));
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertEquals("Hello, world.", LatexEscaper.text("Hello, world."));
    }

    @Test
    void handlesNullAndEmpty() {
        assertEquals("", LatexEscaper.text(null));
        assertEquals("", LatexEscaper.text(""));
    }

    @Test
    void urlEscapingIsMinimal() {
        assertEquals(
                "https://x.test/a\\%20b\\#frag", LatexEscaper.url("https://x.test/a%20b#frag"));
    }

    @Test
    void imagePathBracesTheStemSoSpacesSurvive() {
        assertEquals("{smashing-glass -1}.png", LatexEscaper.imagePath("smashing-glass -1.png"));
        assertEquals("{a/b/c}.jpg", LatexEscaper.imagePath("a/b/c.jpg"));
        assertEquals("{noext}", LatexEscaper.imagePath("noext"));
    }
}
