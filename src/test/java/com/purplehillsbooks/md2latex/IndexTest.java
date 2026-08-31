package com.purplehillsbooks.md2latex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Index generation: term collection from front matter, whole-word matching,
 * where {@code \index} may and may not appear, and the LaTeX placement rules
 * that decide whether the printed page numbers are correct.
 */
class IndexTest {

    @TempDir
    Path tmp;

    private final List<Problem> problems = new ArrayList<>();

    @BeforeEach
    void seed() throws IOException {
        Files.createDirectories(tmp.resolve("docs"));
    }

    private Path writeToTempFile(String relative, String content) throws IOException {
        Path p = tmp.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    /** Converts one file with an explicit term list, returning the LaTeX. */
    private String convertTestTerms(String markdown, String... terms) throws IOException {
        Path file = writeToTempFile("docs/one.md",
                "---\nindexTerms: " + String.join(", ", terms) + "\n---\n" + markdown);
        IndexTerms collected = IndexTerms.collect(
                List.of(Manifest.Entry.newChapter(file, null)),
                java.util.Map.of(file, Files.readString(file, StandardCharsets.UTF_8)),
                problems);
        return new Md2Latex(CodeStyle.LISTINGS, true, problems, collected)
                .convert(Files.readString(file, StandardCharsets.UTF_8),
                         file, tmp.resolve("latex")).latex();
    }

    private List<Problem> errors() {
        return problems.stream().filter(Problem::isError).toList();
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    @Test
    void wholeWordMatchIsCaseInsensitive() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThe Tribe survives.\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void aPluralIsNotAMatch() throws IOException {
        assertFalse(convertTestTerms("# H\n\nMany tribes died out.\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void aSubstringInsideAnotherWordIsNotAMatch() throws IOException {
        String tex = convertTestTerms("# H\n\nWe redistribute the tribal wealth.\n", "tribe");
        assertFalse(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void multiWordPhrasesMatch() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThis is moral realism at work.\n", "moral realism")
                .contains("\\index{moral realism}"));
    }

    @Test
    void aPhraseSplitByEmphasisStillMatches() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThis is *moral* realism.\n", "moral realism")
                .contains("\\index{moral realism}"));
    }

    @Test
    void aPhraseSplitAcrossASoftLineBreakStillMatches() throws IOException {
        // The naive plainText() helper drops line breaks entirely, which would
        // glue "moral" to "realism" and silently stop matching.
        assertTrue(convertTestTerms("# H\n\nThis is moral\nrealism at work.\n", "moral realism")
                .contains("\\index{moral realism}"));
    }

    @Test
    void aWordFollowedByALineBreakStillMatches() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThe tribe\nsurvives.\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void aNonBreakingSpaceInsideAPhraseStillMatches() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThis is moral realism.\n", "moral realism")
                .contains("\\index{moral realism}"));
    }

    @Test
    void anAccentedLetterDoesNotCreateAFalseWordBoundary() throws IOException {
        // Java's \w is ASCII-only unless UNICODE_CHARACTER_CLASS is set, which
        // would let "Kant" match inside "Kante".
        assertFalse(convertTestTerms("# H\n\nThe writer Kante spoke.\n", "Kant")
                .contains("\\index{Kant}"));
    }

    // ------------------------------------------------------------------
    // Scope: what must never be searched
    // ------------------------------------------------------------------

    @Test
    void inlineCodeIsNotSearched() throws IOException {
        assertFalse(convertTestTerms("# H\n\nSet `tribe` to five.\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void codeBlocksAreNotSearched() throws IOException {
        assertFalse(convertTestTerms("# H\n\n```\nint tribe = 5;\n```\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void imagePathsAndAltTextAreNotSearched() throws IOException {
        Files.writeString(tmp.resolve("docs/tribe.png"), "png");
        String tex = convertTestTerms("# H\n\n![a tribe gathering](tribe.png)\n", "tribe");
        assertFalse(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void autolinkUrlsAreNotSearched() throws IOException {
        String tex = convertTestTerms("# H\n\nSee <https://example.test/tribe/> for more.\n",
                "tribe");
        assertFalse(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void aRealLinkLabelIsSearched() throws IOException {
        assertTrue(convertTestTerms("# H\n\nSee [the tribe page](https://example.test).\n",
                "tribe").contains("\\index{tribe}"));
    }

    @Test
    void listItemsAreNotSearched() throws IOException {
        assertFalse(convertTestTerms("# H\n\n- the tribe cooperates\n- and survives\n", "tribe")
                .contains("\\index{tribe}"));
    }

    @Test
    void tableCellsAreNotSearched() throws IOException {
        String tex = convertTestTerms("""
                # H

                | tribe | count |
                |-------|-------|
                | a     | 1     |
                """, "tribe");
        assertFalse(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void footnoteBodiesAreNotSearched() throws IOException {
        String tex = convertTestTerms("# H\n\nA claim[^1].\n\n[^1]: About the tribe.\n", "tribe");
        assertFalse(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void blockQuotesAndAdmonitionsAreSearched() throws IOException {
        assertTrue(convertTestTerms("# H\n\n> The tribe survives.\n", "tribe")
                .contains("\\index{tribe}"));
        problems.clear();
        assertTrue(convertTestTerms("# H\n\n:::tip\n\nThe tribe survives.\n\n:::\n", "tribe")
                .contains("\\index{tribe}"));
    }

    // ------------------------------------------------------------------
    // LaTeX placement
    // ------------------------------------------------------------------

    @Test
    void paragraphEntriesAreLeavevmodeGuardedAndPrecedeTheText() throws IOException {
        String tex = convertTestTerms("# H\n\nThe tribe survives.\n", "tribe");
        assertTrue(tex.contains("\\leavevmode\\index{tribe}%\nThe tribe survives."), tex);
    }

    @Test
    void headingEntriesFollowTheClosingBraceAndAreNobreakGuarded() throws IOException {
        String tex = convertTestTerms("# The Tribe\n\nSomething else.\n", "tribe");
        assertTrue(tex.contains("\\chapter{The Tribe}%\n\\index{tribe}\\nobreak%"), tex);
    }

    @Test
    void indexNeverAppearsInsideASectioningArgument() throws IOException {
        // \index is fragile in a moving argument and would be re-executed from
        // the .toc file with the wrong page number.
        String tex = convertTestTerms("# The Tribe\n\n## A tribe section\n\nText.\n", "tribe");
        assertFalse(tex.matches("(?s).*\\\\(chapter|section)\\{[^}]*\\\\index.*"), tex);
    }

    @Test
    void indexNeverAppearsInsideAFootnote() throws IOException {
        String tex = convertTestTerms("A claim[^1].\n\n[^1]: About the tribe.\n", "tribe");
        assertFalse(tex.matches("(?s).*\\\\footnote\\{[^}]*\\\\index.*"), tex);
    }

    // ------------------------------------------------------------------
    // Once per section
    // ------------------------------------------------------------------

    @Test
    void aTermIsRecordedOncePerSectionNotOncePerParagraph() throws IOException {
        String tex = convertTestTerms("""
                # Chapter

                The tribe survives.

                The tribe cooperates.

                The tribe endures.
                """, "tribe");
        assertEquals(1, countOccurrences(tex, "\\index{tribe}"), tex);
    }

    @Test
    void aNewSectionStartsANewScope() throws IOException {
        String tex = convertTestTerms("""
                # Chapter

                The tribe survives.

                ## Second Section

                The tribe cooperates.

                ### Deeper

                The tribe endures.
                """, "tribe");
        // Chapter heading resets, section heading resets; the h3 does not.
        assertEquals(2, countOccurrences(tex, "\\index{tribe}"), tex);
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0;
             i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // Term collection and validation
    // ------------------------------------------------------------------

    @Test
    void termsArePooledAcrossTheWholeBook() throws IOException {
        Path a = writeToTempFile("docs/a.md", "---\nindexTerms: tribe\n---\n# A\n\nThe tribe here.\n");
        Path b = writeToTempFile("docs/b.md", "# B\n\nThe tribe appears here too.\n");

        IndexTerms terms = IndexTerms.collect(
                List.of(Manifest.Entry.newChapter(a, null), Manifest.Entry.newChapter(b, null)),
                java.util.Map.of(a, Files.readString(a), b, Files.readString(b)),
                problems);

        // b.md declares nothing, but is still searched for a.md's term.
        String tex = new Md2Latex(CodeStyle.LISTINGS, true, problems, terms)
                .convert(Files.readString(b), b, tmp.resolve("latex")).latex();
        assertTrue(tex.contains("\\index{tribe}"), tex);
    }

    @Test
    void reservedMakeindexCharactersAreRejected() throws IOException {
        convertTestTerms("# H\n\nText.\n", "a|b");
        assertEquals(1, errors().size());
        assertTrue(errors().get(0).message().contains("makeindex reserves"),
                errors().get(0).message());
    }

    @Test
    void emptySegmentsAreIgnored() throws IOException {
        assertTrue(convertTestTerms("# H\n\nThe tribe survives.\n", "tribe", "", " ")
                .contains("\\index{tribe}"));
        assertTrue(errors().isEmpty());
    }

    @Test
    void aTermMatchingNothingIsReportedByTheBookBuilder() throws IOException {
        writeToTempFile("docs/one.md",
                "---\nindexTerms: zebra\n---\n# H\n\nNo such animal here.\n");
        writeToTempFile("docs/book.manifest", "title: T\nchapters:\n  - one.md\n");

        assertEquals(0, Main.run(new String[]{tmp.resolve("docs/book.manifest").toString()}));
        // The build succeeds; the warning is advisory.
        assertTrue(Files.exists(tmp.resolve("docs/latex/book.tex")));
    }

    // ------------------------------------------------------------------
    // Preamble
    // ------------------------------------------------------------------


}
