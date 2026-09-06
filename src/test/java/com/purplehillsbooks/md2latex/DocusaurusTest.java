package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Markua translated into the Markdown a Docusaurus site reads.
 *
 * <p>These assert on the generated text rather than on a tree, because the generated text is the
 * deliverable: a person reads it next to the source when a page comes out wrong.
 */
class DocusaurusTest {

    private static final Manifest.Docusaurus SETTINGS =
            new Manifest.Docusaurus(Paths.get("out"), "Book", null, "md", true);

    private final Path sourceFile = Paths.get(".").toAbsolutePath().normalize().resolve("test.md");

    /** The body of the generated file, with the front matter taken off. */
    private String body(String markua) {
        String all = whole(markua);
        int end = all.indexOf("\n---\n", 3);
        return all.substring(end + "\n---\n".length()).strip();
    }

    private String whole(String markua) {
        return new Md2Docusaurus(SETTINGS)
                .convert(markua, sourceFile, null, 1, (destination, image) -> destination)
                .markdown();
    }

    // ------------------------------------------------------------------
    // Blurbs
    // ------------------------------------------------------------------

    @Test
    void warningPrefixBecomesAWarningAdmonition() {
        assertEquals(":::warning\n\nMind the gap.\n\n:::", body("W> Mind the gap.\n"));
    }

    @Test
    void aKindDocusaurusLacksKeepsItsNameAsTheTitle() {
        // Markua names ten kinds and Docusaurus renders five. Folding 'exercise'
        // onto info silently would lose the distinction the author drew.
        assertTrue(body("X> Try this.\n").startsWith(":::info[Exercise]"));
        assertTrue(body("D> Discuss.\n").startsWith(":::info[Discussion]"));
        assertTrue(body("A> Aside.\n").startsWith(":::note[Aside]"));
        assertTrue(body("E> Wrong.\n").startsWith(":::danger[Error]"));
    }

    @Test
    void aKindDocusaurusHasNativelyGetsNoTitle() {
        assertTrue(body("T> Handy.\n").startsWith(":::tip\n"));
        assertTrue(body("I> Note this.\n").startsWith(":::info\n"));
    }

    @Test
    void anUntitledBlurbBecomesABlockquote() {
        // :::note always prints the word "note", so there is no admonition
        // without a heading to map an untitled blurb onto.
        assertEquals("> No heading here.", body("B> No heading here.\n"));
    }

    @Test
    void aBlankLineInsideAnUntitledBlurbKeepsTheQuoteGoing() {
        String out = body("B> One.\nB>\nB> Two.\n");
        assertEquals("> One.\n>\n> Two.", out);
    }

    @Test
    void aCentredBlurbBecomesADivThatWorksAsBothMarkdownAndJsx() {
        String out = body("C> Middle.\n");
        assertTrue(out.startsWith("<div align=\"center\">"), out);
        assertTrue(out.endsWith("</div>"), out);
        assertTrue(out.contains("\nMiddle.\n"), out);
    }

    @Test
    void aFencedBlurbCarriesItsClassAndTitle() {
        assertTrue(
                body("{blurb, class: warning, title: Careful}\nText.\n{/blurb}\n")
                        .startsWith(":::warning[Careful]"));
    }

    @Test
    void anUnknownClassKeepsItsOwnName() {
        assertTrue(
                body("{blurb, class: sidebar}\nText.\n{/blurb}\n").startsWith(":::note[Sidebar]"));
    }

    @Test
    void aDocusaurusAdmonitionSurvivesUnchanged() {
        assertEquals(
                ":::tip[Key Takeaway]\n\nBody.\n\n:::",
                body(":::tip[Key Takeaway]\n\nBody.\n\n:::\n"));
    }

    @Test
    void aClosingBracketInATitleIsEscaped() {
        assertTrue(
                body("{blurb, class: note, title: \"a [b] c\"}\nx\n{/blurb}\n")
                        .contains(":::note[a [b\\] c]"));
    }

    @Test
    void markdownInsideABlurbIsStillMarkdown() {
        String out = body("W> Some `code` and a **bold** word.\nW>\nW> ```\nW> raw\nW> ```\n");
        assertTrue(out.contains("`code`"), out);
        assertTrue(out.contains("**bold**"), out);
        assertTrue(out.contains("```\nraw\n```"), out);
    }

    // ------------------------------------------------------------------
    // Index markers
    // ------------------------------------------------------------------

    @Test
    void indexMarkersAreStripped() {
        // A site has no index, and leaving the braces would hand MDX something
        // that looks like an expression.
        assertEquals("Organised by tribe.", body("Organised by tribe{i: \"tribe\"}.\n"));
    }

    @Test
    void anIndexMarkerInAHeadingGoesToo() {
        assertEquals("## Tribes", body("## Tribes{i: \"tribe\"}\n"));
    }

    @Test
    void anIndexMarkerInsideCodeIsLiteral() {
        assertTrue(body("`{i: \"x\"}`\n").contains("`{i: \"x\"}`"));
        assertTrue(body("```\n{i: \"x\"}\n```\n").contains("{i: \"x\"}"));
    }

    @Test
    void indexTermsFrontMatterIsDropped() {
        String out = whole("---\nindexTerms: tribe, kinship\ndescription: Hello\n---\n\nText.\n");
        assertFalse(out.contains("indexTerms"), out);
        assertTrue(out.contains("description: Hello"), out);
    }

    // ------------------------------------------------------------------
    // The escape hatch, both ways round
    // ------------------------------------------------------------------

    @Test
    void aLatexOnlyCommentIsDropped() {
        // The space either side of it stays, which renders as one space.
        assertEquals("Before  After", body("Before <!-- latex: \\clearpage --> After\n"));
    }

    @Test
    void aLineHoldingNothingButALatexCommentGoesEntirely() {
        assertEquals("One.\n\nTwo.", body("One.\n\n<!-- latex: \\clearpage -->\n\nTwo.\n"));
    }

    @Test
    void aDocusaurusOnlyCommentIsUnwrapped() {
        assertEquals(
                "Before <Thing /> After", body("Before <!-- docusaurus: <Thing /> --> After\n"));
    }

    @Test
    void anOrdinaryCommentIsLeftAlone() {
        assertEquals("<!-- a note to self -->", body("<!-- a note to self -->\n"));
    }

    // ------------------------------------------------------------------
    // Typography
    // ------------------------------------------------------------------

    @Test
    void quotesAreCurledAndDashesAreRead() {
        assertEquals(
                "He said “hello” — see pages 3–5.", body("He said \"hello\" -- see pages 3-5.\n"));
    }

    @Test
    void aHyphenatedWordKeepsItsHyphen() {
        assertEquals("A well-known catch-22.", body("A well-known catch-22.\n"));
    }

    @Test
    void codeKeepsWhateverWasWritten() {
        // The flag inside the span is untouched; the quotes outside it are prose
        // and are curled like any others.
        assertEquals(
                "Run `git log --oneline` “as is”.", body("Run `git log --oneline` \"as is\".\n"));
        assertTrue(body("```\n--verbose \"x\"\n```\n").contains("--verbose \"x\""));
    }

    @Test
    void aTableDelimiterRowIsNotProse() {
        // Read as prose, |---|---| becomes a row of em dashes and the table
        // stops being a table.
        String out = body("| A | B |\n|---|---|\n| 1 | 2 |\n");
        assertTrue(out.contains("|---|---|"), out);
    }

    @Test
    void aThematicBreakIsNotAnEmDash() {
        assertTrue(body("One.\n\n---\n\nTwo.\n").contains("\n---\n"));
    }

    @Test
    void aUrlKeepsItsHyphensAndItsQuotes() {
        String out = body("See [it](https://example.com/a--b/2020-2021 \"A title\").\n");
        assertTrue(out.contains("https://example.com/a--b/2020-2021"), out);
        assertTrue(out.contains("\"A title\""), out);
    }

    @Test
    void anInvisibleCharacterIsDropped() {
        assertEquals("ab", body("a​b\n"));
    }

    @Test
    void unicodeThatABrowserCanSetIsLeftAlone() {
        // The whole point of a table distinct from the LaTeX one: none of this
        // needs translating for the web.
        assertEquals(
                "Input → output, α ≤ β, done ✓ 😀", body("Input → output, α ≤ β, done ✓ 😀\n"));
    }

    // ------------------------------------------------------------------
    // Front matter
    // ------------------------------------------------------------------

    @Test
    void positionComesFromTheManifestNotTheSource() {
        String out =
                new Md2Docusaurus(SETTINGS)
                        .convert(
                                "---\nsidebar_position: 9\n---\n\n# T\n",
                                sourceFile,
                                null,
                                4,
                                (destination, image) -> destination)
                        .markdown();
        assertTrue(out.contains("sidebar_position: 4"), out);
        assertFalse(out.contains("sidebar_position: 9"), out);
    }

    @Test
    void formatIsWrittenSoThatOrdinaryProseCannotFailTheSiteBuild() {
        assertTrue(whole("# T\n").contains("format: md"), whole("# T\n"));
    }

    @Test
    void formatNoneWritesNoKey() {
        String out =
                new Md2Docusaurus(
                                new Manifest.Docusaurus(Paths.get("out"), "B", null, "none", true))
                        .convert("# T\n", sourceFile, null, 1, (destination, image) -> destination)
                        .markdown();
        assertFalse(out.contains("format:"), out);
    }

    @Test
    void aTitleOverrideRenamesTheHeadingAndSetsTheFrontMatter() {
        String out =
                new Md2Docusaurus(SETTINGS)
                        .convert(
                                "# Old Name\n\nText.\n",
                                sourceFile,
                                "New Name",
                                1,
                                (destination, image) -> destination)
                        .markdown();
        assertTrue(out.contains("title: \"New Name\""), out);
        assertTrue(out.contains("# New Name"), out);
        assertFalse(out.contains("Old Name"), out);
    }

    @Test
    void aTitleOverrideWithNoHeadingIsReported() {
        assertFalse(
                new Md2Docusaurus(SETTINGS)
                        .convert(
                                "Just text.\n",
                                sourceFile,
                                "New Name",
                                1,
                                (destination, image) -> destination)
                        .titleApplied());
    }

    @Test
    void authorsOwnFrontMatterIsKeptAsWritten() {
        // Re-emitting from a parsed map would turn a YAML list into a string.
        String out = whole("---\ntags:\n  - one\n  - two\nslug: /x\n---\n\nText.\n");
        assertTrue(out.contains("tags:\n  - one\n  - two"), out);
        assertTrue(out.contains("slug: /x"), out);
    }
}
