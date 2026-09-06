package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.purplehillsbooks.exception.CommonException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestReaderTest {

    @TempDir Path tmp;

    @BeforeEach
    void seedSources() throws IOException {
        Files.createDirectories(tmp.resolve("docs"));
        write("docs/one.md", "# One\n");
        write("docs/two.md", "# Two\n");
    }

    private Path write(String relative, String content) throws IOException {
        Path p = tmp.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private Manifest read(String yaml) throws Exception {
        Path m = write("docs/book.manifest", yaml);
        return ManifestReader.readManifest(m);
    }

    private String errorFrom(String yaml) throws IOException {
        Path m = write("docs/book.manifest", yaml);
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
                read(
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
                errorFrom(
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
                read(
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
                read(
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
                errorFrom(
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
                errorFrom(
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
                errorFrom(
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
                read(
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
                read(
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
                read(
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
                read(
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
        write("docs/foreword.md", "# Foreword\n");
        write("docs/glossary.md", "# Glossary\n");
        Manifest m =
                read(
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
                read(
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
        write("docs/foreword.md", "# Foreword\n");
        write("docs/glossary.md", "# Glossary\n");
        Manifest m =
                read(
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
                errorFrom(
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
        assertTrue(message.contains("nope.md"), message);
        assertTrue(message.contains("alsonope.md"), message);
        assertTrue(message.contains("2 source file(s)"), message);
    }

    @Test
    void booleansAcceptYesAndNo() throws Exception {
        Manifest m =
                read(
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
        assertTrue(
                errorFrom(
                                """
                chapters: [one.md]
                """)
                        .contains("'title' is missing"));
    }

    @Test
    void missingChaptersIsRejected() throws IOException {
        assertTrue(errorFrom("title: X\n").contains("'chapters' is missing"));
    }

    @Test
    void emptyChaptersIsRejected() throws IOException {
        assertTrue(
                errorFrom(
                                """
                title: X
                chapters: []
                """)
                        .contains("at least one file"));
    }

    @Test
    void unknownTopLevelKeyIsRejected() throws IOException {
        String msg =
                errorFrom(
                        """
                title: X
                autor: typo
                chapters: [one.md]
                """);
        assertTrue(msg.contains("unknown key"));
        assertTrue(msg.contains("autor"));
    }

    @Test
    void unknownNestedKeyIsRejected() throws IOException {
        String msg =
                errorFrom(
                        """
                title: X
                document:
                  colour: blue
                chapters: [one.md]
                """);
        assertTrue(msg.contains("colour"));
        assertTrue(msg.contains("document"));
    }

    @Test
    void everyMissingSourceFileIsReportedAtOnce() throws IOException {
        String msg =
                errorFrom(
                        """
                title: X
                chapters:
                  - one.md
                  - nope.md
                  - also-missing.md
                """);
        assertTrue(msg.contains("2 source file(s)"));
        assertTrue(msg.contains("nope.md"));
        assertTrue(msg.contains("also-missing.md"));
        assertFalse(msg.contains("- one.md"));
    }

    @Test
    void badDocumentClassIsRejected() throws IOException {
        assertTrue(
                errorFrom(
                                """
                title: X
                document:
                  class: memoir
                chapters: [one.md]
                """)
                        .contains("document.class must be one of"));
    }

    @Test
    void badCodeStyleIsRejected() throws IOException {
        assertTrue(
                errorFrom(
                                """
                title: X
                code: rainbow
                chapters: [one.md]
                """)
                        .contains("Unknown code style"));
    }

    @Test
    void entryWithNeitherFileNorPartIsRejected() throws IOException {
        assertTrue(
                errorFrom(
                                """
                title: X
                chapters:
                  - title: orphan
                """)
                        .contains("needs either a 'file' or a 'part'"));
    }

    @Test
    void entryWithBothFileAndPartIsRejected() throws IOException {
        assertTrue(
                errorFrom(
                                """
                title: X
                chapters:
                  - file: one.md
                    part: Part One
                """)
                        .contains("both 'part' and 'file'"));
    }

    @Test
    void theRemovedSourceDirKeyGetsAPointedExplanation() throws IOException {
        String msg =
                errorFrom(
                        """
                title: X
                sourceDir: docs
                chapters: [one.md]
                """);
        assertTrue(msg.contains("'sourceDir' is no longer used"), msg);
        assertTrue(msg.contains("../shared/intro.md"), msg);
    }

    @Test
    void chapterPathsMayReachOutsideTheManifestFolder() throws Exception {
        write("shared/preface.md", "# Preface\n");
        Manifest m =
                read(
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
        assertTrue(errorFrom("title: [unclosed\n").contains("invalid YAML"));
    }

    @Test
    void emptyManifestIsRejected() throws IOException {
        assertTrue(errorFrom("# only a comment\n").contains("empty"));
    }

    @Test
    void duplicateKeysAreRejected() throws IOException {
        String msg =
                errorFrom(
                        """
                title: One
                title: Two
                chapters: [one.md]
                """);
        assertTrue(msg.contains("invalid YAML") || msg.contains("duplicate"));
    }

    @Test
    void scalarAtTopLevelIsRejected() throws IOException {
        assertTrue(errorFrom("just a string\n").contains("expected a mapping"));
    }

    // ------------------------------------------------------------------
    // locate()
    // ------------------------------------------------------------------

    @Test
    void locateFindsTheSingleManifestInADirectory() throws Exception {
        Path m = write("only.manifest", "title: X\nchapters: [docs/one.md]\n");
        assertEquals(m, ManifestReader.locate(tmp));
    }

    @Test
    void locateRefusesToGuessBetweenTwoManifests() throws Exception {
        write("a.manifest", "title: A\n");
        write("b.manifest", "title: B\n");
        String msg =
                assertThrows(ManifestException.class, () -> ManifestReader.locate(tmp))
                        .getMessage();
        assertTrue(msg.contains("2 manifest files"));
    }

    @Test
    void locateReportsWhenThereIsNoManifest() {
        String msg =
                assertThrows(
                                ManifestException.class,
                                () -> ManifestReader.locate(tmp.resolve("docs")))
                        .getMessage();
        assertTrue(msg.contains("no *.manifest"));
    }

    @Test
    void locateReportsAMissingPath() {
        String msg =
                assertThrows(
                                ManifestException.class,
                                () -> ManifestReader.locate(tmp.resolve("absent")))
                        .getMessage();
        assertTrue(msg.contains("not found"));
    }
}
