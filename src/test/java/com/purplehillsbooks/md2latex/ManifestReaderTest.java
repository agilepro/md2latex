package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.purplehillsbooks.exception.CommonException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

class ManifestReaderTest {
    @TempDir Path tmp = Path.of("temp/docs");

    @BeforeEach
    void seedSources() throws IOException {
        Files.createDirectories(tmp.resolve("docs"));
        writeTempFile("docs/one.md", "# One\n");
        writeTempFile("docs/two.md", "# Two\n");
    }

    public static String assertException(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            return CommonException.getFullMessage(t);
        }
        throw CommonException.newBasic("expected exception was not thrown");
    }

    public static void assertContains(String body, String searchText) {
        if (!body.contains(searchText)) {
            throw CommonException.newBasic(
                    "Failed to find error text:\nEXPECTED: '%s'\nWITHIN: %s", searchText, body);
        }
    }

    private Path writeTempFile(String relative, String content) throws IOException {
        Path p = tmp.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private Manifest parseManifestFromString(String yaml) throws Exception {
        Path m = writeTempFile("docs/book.manifest", yaml);
        return ManifestReader.readManifest(m);
    }

    private String errorFromParsingManifest(String yaml) throws IOException {
        Path m = writeTempFile("docs/book.manifest", yaml);
        try {
            ManifestReader.readManifest(m);
            throw CommonException.newBasic("did not receive an exception for " + m + ":\n" + yaml);
        } catch (Exception e) {
            return CommonException.getFullMessage(e);
        }
    }

    // ------------------------------------------------------------------
    // Happy paths
    // ------------------------------------------------------------------

    @Test
    void minimalManifestGetsSensibleDefaults() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: My Book
                chapters:
                  - one.md
                  - two.md
                """);

        assertEquals("My Book", m.title());
        assertNull(m.author());
        assertEquals(2, m.chapters().size());
        assertEquals(tmp.resolve("docs"), m.sourceFolder());

        assertEquals(tmp.resolve("docs/latex"), m.latex().directory());
        assertEquals("book.tex", m.latex().mainFile());
        assertEquals(tmp.resolve("docs/latex/chapters"), m.latex().chapterPath());

        assertEquals("book", m.document().documentClass());
        assertTrue(m.document().toc());
        assertTrue(m.document().hasChapters());
        assertEquals(CodeStyle.LISTINGS, m.codeStyle());
        // Saying nothing about targets means a LaTeX book, as it always did.
        assertTrue(m.hasLatex());
        assertFalse(m.hasDocusaurus());
    }

    @Test
    void dialectIsRejectedWithAnExplanation() throws IOException {
        String message =
                errorFromParsingManifest(
                        """
                title: X
                dialect: markua
                chapters:
                  - one.md
                """);
        assertTrue(message.contains("no longer used"), message);
        assertTrue(message.contains("Markua"), message);
    }

    @Test
    void docusaurusBlockNamesItsOwnOutput() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                docusaurus:
                  directory: ../site/docs/x
                  category: The Book
                  position: 4
                  format: mdx
                  assets: false
                chapters:
                  - one.md
                """);
        assertTrue(m.hasDocusaurus());
        // Naming only the site means only the site is built.
        assertFalse(m.hasLatex());
        assertEquals(tmp.resolve("site/docs/x"), m.docusaurus().directory());
        assertEquals("The Book", m.docusaurus().category());
        assertEquals(4, m.docusaurus().position());
        assertEquals("mdx", m.docusaurus().format());
        assertFalse(m.docusaurus().assets());
    }

    @Test
    void docusaurusDefaultsToTheBookTitleAndCommonMark() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: Essentials
                latex:
                  directory: tex
                docusaurus:
                  directory: site
                chapters:
                  - one.md
                """);
        // Naming both means both are built.
        assertTrue(m.hasLatex());
        assertTrue(m.hasDocusaurus());
        assertEquals("Essentials", m.docusaurus().category());
        assertEquals("md", m.docusaurus().format());
        assertTrue(m.docusaurus().assets());
        assertNull(m.docusaurus().position());
        assertEquals(tmp.resolve("docs/tex"), m.latex().directory());
    }

    @Test
    void docusaurusNeedsADirectory() throws IOException {
        String message =
                errorFromParsingManifest(
                        """
                title: X
                docusaurus:
                  category: Whatever
                chapters:
                  - one.md
                """);
        assertTrue(message.contains("docusaurus.directory"), message);
    }

    @Test
    void unknownDocusaurusFormatIsRejected() throws IOException {
        String message =
                errorFromParsingManifest(
                        """
                title: X
                docusaurus:
                  directory: site
                  format: asciidoc
                chapters:
                  - one.md
                """);
        assertTrue(message.contains("format"), message);
    }

    @Test
    void latexAndOutputCannotBothBeGiven() throws IOException {
        String message =
                errorFromParsingManifest(
                        """
                title: X
                latex:
                  directory: a
                output:
                  directory: b
                chapters:
                  - one.md
                """);
        assertTrue(message.contains("delete"), message.toLowerCase());
    }

    @Test
    void allSettingsAreRead() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title:    Full
                subtitle: A Subtitle
                author:   Someone
                date:     August 2026
                output:
                  directory: out/tex
                  main:      main
                document:
                  class:       article
                  toc:         false
                  tocDepth:    3
                  numberDepth: 1
                code: minted
                preamble:
                  - \\usepackage{microtype}
                chapters:
                  - one.md
                """);

        assertEquals("A Subtitle", m.subtitle());
        assertEquals("Someone", m.author());
        assertEquals("August 2026", m.date());
        assertEquals(tmp.resolve("docs/out/tex"), m.latex().directory());
        // A missing .tex extension is supplied rather than rejected.
        assertEquals("main.tex", m.latex().mainFile());
        assertEquals("article", m.document().documentClass());
        assertFalse(m.document().hasChapters());
        assertFalse(m.document().toc());
        assertEquals(3, m.document().tocDepth());
        assertEquals(CodeStyle.MINTED, m.codeStyle());
        assertEquals(1, m.extraPreamble().size());
    }

    @Test
    void keyNamesIgnoreCaseAndSeparators() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                document:
                  TOC-DEPTH: 4
                  number_depth: 1
                chapters:
                  - one.md
                """);
        assertEquals(4, m.document().tocDepth());
        assertEquals(1, m.document().numberDepth());
    }

    @Test
    void chapterEntriesAcceptStringOrMapping() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                chapters:
                  - one.md
                  - file:  two.md
                    title: Renamed
                """);
        assertEquals(2, m.chapters().size());
        assertNull(m.chapters().get(0).titleOverride());
        assertEquals("Renamed", m.chapters().get(1).titleOverride());
    }

    @Test
    void partDividersAreKeptInOrderButAreNotChapters() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                chapters:
                  - part: Part One
                  - one.md
                  - part: Part Two
                  - two.md
                """);
        assertEquals(4, m.chapters().size());
        assertEquals(2, m.sourceEntries().size());
        assertTrue(m.chapters().get(0).isPart());
        assertEquals("Part One", m.chapters().get(0).partTitle());
    }

    @Test
    void theThreeSectionsAreReadSeparately() throws Exception {
        writeTempFile("docs/foreword.md", "# Foreword\n");
        writeTempFile("docs/glossary.md", "# Glossary\n");
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                frontMatter:
                  - foreword.md
                chapters:
                  - one.md
                  - two.md
                appendices:
                  - glossary.md
                """);
        assertEquals(1, m.frontMatter().size());
        assertEquals(2, m.chapters().size());
        assertEquals(1, m.appendices().size());
        assertEquals(tmp.resolve("docs/foreword.md"), m.frontMatter().get(0).file());
        assertEquals(tmp.resolve("docs/glossary.md"), m.appendices().get(0).file());

        // Book order across all three, which is what index collection walks.
        assertEquals(4, m.sourceEntries().size());
        assertEquals(tmp.resolve("docs/foreword.md"), m.sourceEntries().get(0).file());
        assertEquals(tmp.resolve("docs/glossary.md"), m.sourceEntries().get(3).file());
    }

    @Test
    void frontMatterAndAppendicesAreOptional() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                chapters:
                  - one.md
                """);
        assertTrue(m.frontMatter().isEmpty());
        assertTrue(m.appendices().isEmpty());
    }

    @Test
    void theOtherSectionsTakeTheSameEntryFormsAsChapters() throws Exception {
        writeTempFile("docs/foreword.md", "# Foreword\n");
        writeTempFile("docs/glossary.md", "# Glossary\n");
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                frontMatter:
                  - file:  foreword.md
                    title: A Word Before
                chapters:
                  - one.md
                appendices:
                  - part: Reference
                  - glossary.md
                """);
        assertEquals("A Word Before", m.frontMatter().get(0).titleOverride());
        assertTrue(m.appendices().get(0).isPart());
        assertEquals(tmp.resolve("docs/glossary.md"), m.appendices().get(1).file());
    }

    @Test
    void aMissingFileInAnySectionIsReported() throws IOException {
        String message =
                errorFromParsingManifest(
                        """
                title: X
                frontMatter:
                  - nope.md
                chapters:
                  - one.md
                appendices:
                  - alsonope.md
                """);
        // One run names every bad path, not just the first section's.
        assertContains(message, "nope.md");
        assertContains(message, "alsonope.md");
        assertContains(message, "2 source file(s)");
    }

    @Test
    void booleansAcceptYesAndNo() throws Exception {
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                document:
                  toc: no
                chapters:
                  - one.md
                """);
        assertFalse(m.document().toc());
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void missingTitleIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                chapters: [one.md]
                """),
                "'title' is missing");
    }

    @Test
    void missingChaptersIsRejected() throws IOException {
        assertContains(errorFromParsingManifest("title: X\n"), "'chapters' is missing");
    }

    @Test
    void emptyChaptersIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                title: X
                chapters: []
                """),
                "at least one file");
    }

    // Test
    void unknownTopLevelKeyIsRejected() throws IOException {
        String msg =
                errorFromParsingManifest(
                        """
                title: X
                autor: typo
                chapters: [one.md]
                """);
        assertContains(msg, "unknown key");
        assertContains(msg, "autor");
    }

    @Test
    void unknownNestedKeyIsRejected() throws IOException {
        String msg =
                errorFromParsingManifest(
                        """
                title: X
                document:
                  colour: blue
                chapters: [one.md]
                """);
        assertContains(msg, "colour");
        assertContains(msg, "document");
    }

    @Test
    void everyMissingSourceFileIsReportedAtOnce() throws IOException {
        String msg =
                errorFromParsingManifest(
                        """
                title: X
                chapters:
                  - one.md
                  - nope.md
                  - also-missing.md
                """);
        assertContains(msg, "2 source file(s)");
        assertContains(msg, "nope.md");
        assertContains(msg, "also-missing.md");
        assertFalse(msg.contains("- one.md"));
    }

    @Test
    void badDocumentClassIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                title: X
                document:
                  class: memoir
                chapters: [one.md]
                """),
                "document.class must be one of");
    }

    @Test
    void badCodeStyleIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                title: X
                code: rainbow
                chapters: [one.md]
                """),
                "Unknown code style");
    }

    @Test
    void entryWithNeitherFileNorPartIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                title: X
                chapters:
                  - title: orphan
                """),
                "needs either a 'file' or a 'part'");
    }

    @Test
    void entryWithBothFileAndPartIsRejected() throws IOException {
        assertContains(
                errorFromParsingManifest(
                        """
                title: X
                chapters:
                  - file: one.md
                    part: Part One
                """),
                "both 'part' and 'file'");
    }

    @Test
    void theRemovedSourceDirKeyGetsAPointedExplanation() throws IOException {
        String msg =
                errorFromParsingManifest(
                        """
                title: X
                sourceDir: docs
                chapters: [one.md]
                """);
        assertContains(msg, "'sourceDir' is no longer used");
        assertContains(msg, "../shared/intro.md");
    }

    @Test
    void chapterPathsMayReachOutsideTheManifestFolder() throws Exception {
        writeTempFile("shared/preface.md", "# Preface\n");
        Manifest m =
                parseManifestFromString(
                        """
                title: X
                chapters:
                  - ../shared/preface.md
                  - one.md
                """);
        assertEquals(2, m.chapters().size());
        assertEquals(tmp.resolve("shared/preface.md"), m.chapters().get(0).file());
    }

    @Test
    void malformedYamlIsReportedAsSuch() throws IOException {
        assertContains(errorFromParsingManifest("title: [unclosed\n"), "Unable to parse YAML");
    }

    @Test
    void emptyManifestIsRejected() throws IOException {
        assertContains(errorFromParsingManifest("# only a comment\n"), "empty");
    }

    @Test
    void duplicateKeysAreRejected() throws IOException {
        String msg =
                errorFromParsingManifest(
                        """
                title: One
                title: Two
                chapters: [one.md]
                """);
        assertTrue(msg.contains("invalid YAML") || msg.contains("duplicate"));
    }

    @Test
    void scalarAtTopLevelIsRejected() throws IOException {
        assertContains(errorFromParsingManifest("just a string\n"), "Expected a mapping");
    }

    // ------------------------------------------------------------------
    // locate()
    // ------------------------------------------------------------------

    @Test
    void locateFindsTheSingleManifestInADirectory() throws Exception {
        Path m = writeTempFile("only.manifest", "title: X\nchapters: [docs/one.md]\n");
        assertEquals(m, ManifestReader.locateMaster(tmp));
    }

    @Test
    void locateRefusesToGuessBetweenTwoManifests() throws Exception {
        writeTempFile("a.manifest", "title: A\n");
        writeTempFile("b.manifest", "title: B\n");
        String msg = assertException(() -> ManifestReader.locateMaster(tmp));
        assertContains(msg, "2 manifest files");
    }

    @Test
    void locateReportsWhenThereIsNoManifest() {
        String msg = assertException(() -> ManifestReader.locateMaster(tmp.resolve("docs")));
        assertContains(msg, "no *.manifest");
    }

    @Test
    void locateReportsAMissingPath() {
        String msg = assertException(() -> ManifestReader.locateMaster(tmp.resolve("absent")));
        assertContains(msg, "not found");
    }
}
