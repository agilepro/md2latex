package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every fault that would produce LaTeX which does not compile must be reported against the Markdown
 * line that caused it, with enough detail to fix it.
 *
 * <p>Note that arrows, Greek letters and comparison operators are no longer examples of rejected
 * characters: they have exact LaTeX equivalents and are translated by {@link CharacterMap}. Emoji
 * and CJK have none, so they are used here whenever an unrepresentable character is needed.
 */
class ConversionErrorTest {

    @TempDir Path tmp;

    private Path sourceFile;

    @BeforeEach
    void setUp() {
        sourceFile = tmp.resolve("chapter.md");
    }

    private List<Problem> problemsFor(String markdown) throws IOException {
        return problemsFor(markdown, CodeStyle.LISTINGS);
    }

    private List<Problem> problemsFor(String markdown, CodeStyle style) throws IOException {
        Files.writeString(sourceFile, markdown, StandardCharsets.UTF_8);
        List<Problem> problems = new ArrayList<>();
        new Md2Latex(style, true, problems).convert(markdown, sourceFile, tmp);
        return problems;
    }

    private Problem onlyError(String markdown) throws IOException {
        return onlyError(markdown, CodeStyle.LISTINGS);
    }

    private Problem onlyError(String markdown, CodeStyle style) throws IOException {
        List<Problem> errors =
                problemsFor(markdown, style).stream().filter(Problem::isError).toList();
        assertEquals(
                1,
                errors.size(),
                "expected exactly one error but got: "
                        + errors.stream().map(Problem::format).toList());
        return errors.get(0);
    }

    // ------------------------------------------------------------------
    // Clean input produces nothing
    // ------------------------------------------------------------------

    @Test
    void ordinaryProseProducesNoProblems() throws IOException {
        assertTrue(
                problemsFor(
                                """
                # A Heading

                Ordinary prose with *emphasis*, a list:

                - one
                - two

                and a [link](https://example.test).
                """)
                        .isEmpty());
    }

    @Test
    void typographicPunctuationIsAccepted() throws IOException {
        // Curly quotes, em dash and non-breaking space all exist in the real
        // sources and are all setable by pdflatex with T1.
        assertTrue(problemsFor("It’s a “quote” — and more.\n").isEmpty());
    }

    @Test
    void accentedLatinIsAccepted() throws IOException {
        assertTrue(problemsFor("Café, naïve, Łódź, Dvořák.\n").isEmpty());
    }

    @Test
    void charactersWithABuiltInLatexEquivalentAreTranslatedNotRejected() throws IOException {
        // Arrows, Greek and comparison operators have exact LaTeX equivalents,
        // so they are translated rather than reported.
        assertTrue(problemsFor("Input → output where α ≤ β.\n").isEmpty());
    }

    // ------------------------------------------------------------------
    // Characters pdflatex cannot set and cannot translate
    // ------------------------------------------------------------------

    @Test
    void emojiIsRejectedWithItsLocation() throws IOException {
        Problem p = onlyError("# Title\n\nGreat work 😀 well done.\n");
        assertEquals(3, p.line(), "should point at the line holding the emoji");
        assertTrue(p.message().contains("U+1F600"), p.message());
        assertTrue(p.message().contains("cannot typeset"), p.message());
        assertTrue(p.hint().contains("pictograph"), p.hint());
        assertTrue(p.format().contains("chapter.md:3"), p.format());
    }

    @Test
    void cjkGetsAUnicodeEngineHint() throws IOException {
        Problem p = onlyError("The word 中文 here.\n");
        assertTrue(p.hint().contains("lualatex") || p.hint().contains("xelatex"), p.hint());
    }

    @Test
    void badCharacterInsideCodeBlockIsAlsoCaught() throws IOException {
        Problem p = onlyError("```\nprint(\"😀\")\n```\n");
        assertTrue(p.message().contains("code block"), p.message());
    }

    @Test
    void badCharacterInsideInlineCodeIsCaught() throws IOException {
        Problem p = onlyError("Use `x 😀 y` here.\n");
        assertTrue(p.message().contains("inline code"), p.message());
    }

    @Test
    void theCaretPointsAtTheOffendingCharacter() throws IOException {
        Problem p = onlyError("abc 😀 def\n");
        String report = p.format();
        assertTrue(report.contains("^"), report);
        int caretLine = report.indexOf("\n    ", report.indexOf("\n    ") + 1);
        assertTrue(caretLine > 0, "expected an excerpt and a caret line:\n" + report);
    }

    // ------------------------------------------------------------------
    // Images
    // ------------------------------------------------------------------

    @Test
    void missingImageFileIsAnError() throws IOException {
        Problem p = onlyError("# T\n\n![a picture](nope.png)\n");
        assertEquals(3, p.line());
        assertTrue(p.message().contains("image file not found"), p.message());
        assertTrue(p.message().contains("nope.png"), p.message());
        assertTrue(p.hint().contains("chapter.md"), p.hint());
    }

    @Test
    void unsupportedImageFormatIsAnError() throws IOException {
        Files.writeString(tmp.resolve("diagram.svg"), "<svg/>");
        Problem p = onlyError("![d](diagram.svg)\n");
        assertTrue(p.message().contains(".svg"), p.message());
        assertTrue(p.message().contains("cannot include"), p.message());
        assertTrue(p.hint().contains("png"), p.hint());
    }

    @Test
    void imageWithNoExtensionIsAnError() throws IOException {
        Problem p = onlyError("![d](picture)\n");
        assertTrue(p.message().contains("no extension"), p.message());
    }

    @Test
    void remoteImageIsAnError() throws IOException {
        Problem p = onlyError("![d](https://example.test/x.png)\n");
        assertTrue(p.message().contains("remote URL"), p.message());
        assertTrue(p.hint().contains("cannot download"), p.hint());
    }

    @Test
    void presentImageIsAccepted() throws IOException {
        Files.writeString(tmp.resolve("real.png"), "pretend png");
        assertTrue(problemsFor("![d](real.png)\n").isEmpty());
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Test
    void listNestedTooDeeplyIsAnError() throws IOException {
        Problem p =
                onlyError(
                        """
                - one
                    - two
                        - three
                            - four
                                - five
                """);
        assertTrue(p.message().contains("nested"), p.message());
        assertTrue(p.message().contains("at most 4"), p.message());
    }

    @Test
    void fourLevelsOfNestingIsFine() throws IOException {
        assertTrue(
                problemsFor(
                                """
                - one
                    - two
                        - three
                            - four
                """)
                        .isEmpty());
    }

    @Test
    void definedFootnoteIsFine() throws IOException {
        assertTrue(problemsFor("A claim[^1] here.\n\n[^1]: The evidence.\n").isEmpty());
    }

    @Test
    void undefinedFootnoteStaysLiteralJustAsGitHubRendersIt() throws IOException {
        // commonmark only creates a footnote reference when a definition
        // exists; otherwise the text is left alone. That compiles perfectly
        // well, so it is deliberately not an error.
        assertTrue(problemsFor("A claim[^missing] here.\n").isEmpty());
    }

    @Test
    void verbatimConflictIsAnErrorNotAWarning() throws IOException {
        Problem p = onlyError("```\nsome \\end{verbatim} text\n```\n", CodeStyle.VERBATIM);
        assertTrue(p.message().contains("verbatim"), p.message());
        assertTrue(p.hint().contains("listings"), p.hint());
    }

    @Test
    void theSameCodeBlockIsFineUnderListings() throws IOException {
        assertTrue(
                problemsFor("```\nsome \\end{verbatim} text\n```\n", CodeStyle.LISTINGS).isEmpty());
    }

    // ------------------------------------------------------------------
    // Line numbers survive preprocessing
    // ------------------------------------------------------------------

    @Test
    void lineNumbersAccountForStrippedFrontMatter() throws IOException {
        Problem p =
                onlyError(
                        """
                ---
                sidebar_position: 3
                title: Something
                ---
                # Heading

                Bad 😀 here.
                """);
        assertEquals(7, p.line(), "front matter must not shift the reported line");
    }

    @Test
    void lineNumbersAccountForRewrittenAdmonitions() throws IOException {
        Problem p =
                onlyError(
                        """
                # Heading

                :::tip[Note]

                Bad 😀 here.

                :::
                """);
        assertEquals(5, p.line(), "admonition rewriting must not shift the reported line");
    }

    @Test
    void theExcerptShowsTheOriginalSourceLine() throws IOException {
        Problem p = onlyError("---\ntitle: x\n---\n\nThe value 😀 shown.\n");
        assertTrue(p.sourceLine().contains("The value"), p.sourceLine());
        assertTrue(p.format().contains("The value"), p.format());
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    @Test
    void everyProblemInAFileIsReportedNotJustTheFirst() throws IOException {
        Files.writeString(tmp.resolve("diagram.svg"), "<svg/>");
        List<Problem> errors =
                problemsFor(
                                """
                # Heading

                First bad 😀 char.

                ![missing](gone.png)

                ![vector](diagram.svg)
                """)
                        .stream()
                        .filter(Problem::isError)
                        .toList();

        assertEquals(3, errors.size(), errors.stream().map(Problem::format).toList().toString());
        // Each is pinned to its own line, so all three can be fixed in one pass.
        assertEquals(List.of(3, 5, 7), errors.stream().map(Problem::line).toList());
    }

    @Test
    void theExceptionMessageListsEveryProblemAndSaysNothingWasWritten() {
        List<Problem> problems =
                List.of(
                        new Problem(
                                Problem.Severity.ERROR,
                                Path.of("a.md"),
                                3,
                                5,
                                "bad line",
                                "first thing wrong",
                                "fix it this way"),
                        new Problem(
                                Problem.Severity.ERROR,
                                Path.of("b.md"),
                                9,
                                0,
                                null,
                                "second thing wrong",
                                null));

        String msg = new ConversionException(problems).getMessage();
        assertTrue(msg.contains("2 problems"), msg);
        assertTrue(msg.contains("a.md:3:5"), msg);
        assertTrue(msg.contains("b.md:9"), msg);
        assertTrue(msg.contains("first thing wrong"), msg);
        assertTrue(msg.contains("second thing wrong"), msg);
        assertTrue(msg.contains("hint: fix it this way"), msg);
        assertTrue(msg.contains("No files were written"), msg);
    }

    @Test
    void warningsAreListedSeparatelyFromErrors() {
        List<Problem> problems =
                List.of(
                        new Problem(
                                Problem.Severity.ERROR,
                                Path.of("a.md"),
                                1,
                                0,
                                null,
                                "broken",
                                null),
                        new Problem(
                                Problem.Severity.WARNING,
                                Path.of("b.md"),
                                2,
                                0,
                                null,
                                "minor",
                                null));

        String msg = new ConversionException(problems).getMessage();
        assertTrue(msg.contains("1 problem"), msg);
        assertTrue(msg.contains("Also 1 warning"), msg);
        assertFalse(msg.contains("2 problems"), msg);
    }
}
