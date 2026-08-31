package com.purplehillsbooks.md2latex;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Built-in translation of characters pdflatex cannot set. A character with an
 * unambiguous LaTeX equivalent is translated; one without stays an error.
 */
class CharacterMapTest {

    private final List<Problem> problems = new ArrayList<>();
    private final Path here = Paths.get(".").toAbsolutePath().normalize();
    private final Path sourceFile = here.resolve("test.md");

    private String convert(String markdown) {
        return new Md2Latex(CodeStyle.LISTINGS, false, problems)
                .convert(markdown, sourceFile, here).latex();
    }

    private List<Problem> errors() {
        return problems.stream().filter(Problem::isError).toList();
    }

    // ------------------------------------------------------------------
    // Translation
    // ------------------------------------------------------------------

    @Test
    void arrowsAreTranslated() {
        assertTrue(convert("Input → output.\n").contains("\\ensuremath{\\rightarrow}"));
        assertTrue(convert("A ⇒ B.\n").contains("\\ensuremath{\\Rightarrow}"));
    }

    @Test
    void comparisonOperatorsAreTranslated() {
        assertTrue(convert("x ≤ y and a ≠ b.\n").contains("\\ensuremath{\\leq}"));
        assertTrue(convert("x ≤ y and a ≠ b.\n").contains("\\ensuremath{\\neq}"));
    }

    @Test
    void greekIsTranslated() {
        assertTrue(convert("The angle α and Ω.\n").contains("\\ensuremath{\\alpha}"));
        assertTrue(convert("The angle α and Ω.\n").contains("\\ensuremath{\\Omega}"));
    }

    @Test
    void checkMarksAreTranslated() {
        assertTrue(convert("Done ✓\n").contains("\\ensuremath{\\checkmark}"));
    }

    @Test
    void setAndLogicSymbolsAreTranslated() {
        assertTrue(convert("x ∈ S, ∀ y.\n").contains("\\ensuremath{\\in}"));
        assertTrue(convert("x ∈ S, ∀ y.\n").contains("\\ensuremath{\\forall}"));
    }

    @Test
    void translatedTextProducesNoError() {
        convert("Input → output where x ≤ y, with α and ✓.\n");
        assertTrue(errors().isEmpty(),
                errors().stream().map(Problem::format).toList().toString());
    }

    @Test
    void aReplacementIsNotItselfEscaped() {
        // The backslashes in \ensuremath must survive verbatim.
        String tex = convert("Input → output.\n");
        assertFalse(tex.contains("\\textbackslash"), tex);
    }

    @Test
    void surroundingTextIsStillEscaped() {
        String tex = convert("100% → more_value.\n");
        assertTrue(tex.contains("100\\%"), tex);
        assertTrue(tex.contains("more\\_value"), tex);
        assertTrue(tex.contains("\\ensuremath{\\rightarrow}"), tex);
    }

    @Test
    void zeroWidthCharactersAreDropped() {
        String tex = convert("a​b\n");
        assertTrue(tex.contains("ab"), tex);
        assertTrue(errors().isEmpty());
    }

    // ------------------------------------------------------------------
    // Still rejected
    // ------------------------------------------------------------------

    @Test
    void emojiHasNoEquivalentAndRemainsAnError() {
        convert("Great work 😀 well done.\n");
        assertEquals(1, errors().size());
        assertTrue(errors().get(0).message().contains("U+1F600"),
                errors().get(0).message());
    }

    @Test
    void astralCharactersAreReportedByFullCodePointNotSurrogate() {
        convert("Emoji 😀 here.\n");
        // A surrogate half would print as U+D83D, which would be useless.
        assertFalse(errors().get(0).message().contains("U+D83D"),
                errors().get(0).message());
    }

    @Test
    void cjkRemainsAnError() {
        convert("The word 中文 here.\n");
        assertEquals(1, errors().size());
    }

    @Test
    void translationDoesNotApplyInsideCodeBlocks() {
        // lstlisting is verbatim, so a LaTeX command would print literally.
        convert("```\nx → y\n```\n");
        assertEquals(1, errors().size());
        assertTrue(errors().get(0).hint().contains("verbatim"),
                errors().get(0).hint());
    }

    @Test
    void translationDoesApplyInsideInlineCode() {
        // \texttt is real LaTeX, so the replacement is obeyed.
        String tex = convert("Use `a → b` here.\n");
        assertTrue(errors().isEmpty(),
                errors().stream().map(Problem::format).toList().toString());
        assertTrue(tex.contains("\\ensuremath{\\rightarrow}"), tex);
    }

    // ------------------------------------------------------------------
    // The table itself
    // ------------------------------------------------------------------

    @Test
    void alreadySupportedCharactersAreNotRemapped() {
        // Anything T1 can already set must pass through untouched, so existing
        // documents keep rendering exactly as they did.
        for (int cp : new int[]{'a', 'Z', '1', 0x00E9, 0x00F1, 0x0141,
                                0x2019, 0x2014, 0x201C}) {
            assertFalse(CharacterMap.contains(cp),
                    "U+" + Integer.toHexString(cp) + " is already setable and "
                    + "should not be in the translation table");
        }
    }

    @Test
    void everyReplacementIsPlainAsciiLatex() {
        for (Map.Entry<Integer, String> e : CharacterMap.entries().entrySet()) {
            String latex = e.getValue();
            for (int i = 0; i < latex.length(); i++) {
                assertTrue(latex.charAt(i) < 0x80,
                        "replacement for U+" + Integer.toHexString(e.getKey())
                        + " contains a non-ASCII character: " + latex);
            }
        }
    }

    @Test
    void everyMappedCharacterCountsAsSupported() {
        for (int cp : CharacterMap.entries().keySet()) {
            String s = new String(Character.toChars(cp));
            assertEquals(-1, LatexSafety.firstUnsupportedChar(s),
                    "U+" + Integer.toHexString(cp) + " is mapped and should pass");
            assertTrue(LatexSafety.firstUnsupportedChar(s, false) >= 0
                            || cp <= 0x17F,
                    "U+" + Integer.toHexString(cp)
                    + " should still be rejected where no translation applies");
        }
    }

    @Test
    void bracesInReplacementsAreBalanced() {
        for (Map.Entry<Integer, String> e : CharacterMap.entries().entrySet()) {
            int depth = 0;
            for (char c : e.getValue().toCharArray()) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
                assertTrue(depth >= 0, "unbalanced braces in " + e.getValue());
            }
            assertEquals(0, depth, "unbalanced braces in " + e.getValue());
        }
    }
}
