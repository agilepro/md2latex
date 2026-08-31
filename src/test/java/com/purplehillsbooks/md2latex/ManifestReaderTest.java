package com.purplehillsbooks.md2latex;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestReaderTest {

    @TempDir
    Path tmp;

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
        return ManifestReader.read(m);
    }

    private String errorFrom(String yaml) throws IOException {
        Path m = write("docs/book.manifest", yaml);
        return assertThrows(ManifestException.class, () -> ManifestReader.read(m)).getMessage();
    }

    // ------------------------------------------------------------------
    // Happy paths
    // ------------------------------------------------------------------

    @Test
    void minimalManifestGetsSensibleDefaults() throws Exception {
        Manifest m = read("""
                title: My Book
                chapters:
                  - one.md
                  - two.md
                """);

        assertEquals("My Book", m.title());
        assertNull(m.author());
        assertEquals(2, m.chapters().size());
        assertEquals(tmp.resolve("docs"), m.sourceFolder());

        assertEquals(tmp.resolve("docs/latex"), m.output().directory());
        assertEquals("book.tex", m.output().mainFile());
        assertEquals(tmp.resolve("docs/latex/chapters"), m.output().chapterPath());

        assertEquals("book", m.document().documentClass());
        assertEquals("11pt", m.document().fontSize());
        assertTrue(m.document().toc());
        assertTrue(m.document().hasChapters());
        assertEquals(CodeStyle.LISTINGS, m.codeStyle());
    }

    @Test
    void allSettingsAreRead() throws Exception {
        Manifest m = read("""
                title:    Full
                subtitle: A Subtitle
                author:   Someone
                date:     August 2026
                output:
                  directory: out/tex
                  main:      main
                document:
                  class:       article
                  fontSize:    12pt
                  paperSize:   letterpaper
                  geometry:    margin=2cm
                  toc:         false
                  tocDepth:    3
                  numberDepth: 1
                  twoSide:     true
                code: minted
                preamble:
                  - \\usepackage{microtype}
                chapters:
                  - one.md
                """);

        assertEquals("A Subtitle", m.subtitle());
        assertEquals("Someone", m.author());
        assertEquals("August 2026", m.date());
        assertEquals(tmp.resolve("docs/out/tex"), m.output().directory());
        // A missing .tex extension is supplied rather than rejected.
        assertEquals("main.tex", m.output().mainFile());
        assertEquals("article", m.document().documentClass());
        assertFalse(m.document().hasChapters());
        assertFalse(m.document().toc());
        assertEquals(3, m.document().tocDepth());
        assertTrue(m.document().twoSide());
        assertEquals(CodeStyle.MINTED, m.codeStyle());
        assertEquals(1, m.extraPreamble().size());
    }

    @Test
    void keyNamesIgnoreCaseAndSeparators() throws Exception {
        Manifest m = read("""
                title: X
                document:
                  FONT-SIZE: 12pt
                  toc_depth: 4
                chapters:
                  - one.md
                """);
        assertEquals("12pt", m.document().fontSize());
        assertEquals(4, m.document().tocDepth());
    }

    @Test
    void chapterEntriesAcceptStringOrMapping() throws Exception {
        Manifest m = read("""
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
        Manifest m = read("""
                title: X
                chapters:
                  - part: Part One
                  - one.md
                  - part: Part Two
                  - two.md
                """);
        assertEquals(4, m.entries().size());
        assertEquals(2, m.chapters().size());
        assertTrue(m.entries().get(0).isPart());
        assertEquals("Part One", m.entries().get(0).partTitle());
    }

    @Test
    void booleansAcceptYesAndNo() throws Exception {
        Manifest m = read("""
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
        assertTrue(errorFrom("""
                chapters: [one.md]
                """).contains("'title' is missing"));
    }

    @Test
    void missingChaptersIsRejected() throws IOException {
        assertTrue(errorFrom("title: X\n").contains("'chapters' is missing"));
    }

    @Test
    void emptyChaptersIsRejected() throws IOException {
        assertTrue(errorFrom("""
                title: X
                chapters: []
                """).contains("at least one file"));
    }

    @Test
    void unknownTopLevelKeyIsRejected() throws IOException {
        String msg = errorFrom("""
                title: X
                autor: typo
                chapters: [one.md]
                """);
        assertTrue(msg.contains("unknown key"));
        assertTrue(msg.contains("autor"));
    }

    @Test
    void unknownNestedKeyIsRejected() throws IOException {
        String msg = errorFrom("""
                title: X
                document:
                  fontsize: 12pt
                  colour: blue
                chapters: [one.md]
                """);
        assertTrue(msg.contains("colour"));
        assertTrue(msg.contains("document"));
    }

    @Test
    void everyMissingSourceFileIsReportedAtOnce() throws IOException {
        String msg = errorFrom("""
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
        assertTrue(errorFrom("""
                title: X
                document:
                  class: memoir
                chapters: [one.md]
                """).contains("document.class must be one of"));
    }

    @Test
    void badCodeStyleIsRejected() throws IOException {
        assertTrue(errorFrom("""
                title: X
                code: rainbow
                chapters: [one.md]
                """).contains("Unknown code style"));
    }

    @Test
    void entryWithNeitherFileNorPartIsRejected() throws IOException {
        assertTrue(errorFrom("""
                title: X
                chapters:
                  - title: orphan
                """).contains("needs either a 'file' or a 'part'"));
    }

    @Test
    void entryWithBothFileAndPartIsRejected() throws IOException {
        assertTrue(errorFrom("""
                title: X
                chapters:
                  - file: one.md
                    part: Part One
                """).contains("both 'part' and 'file'"));
    }

    @Test
    void theRemovedSourceDirKeyGetsAPointedExplanation() throws IOException {
        String msg = errorFrom("""
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
        Manifest m = read("""
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
        String msg = errorFrom("""
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
        String msg = assertThrows(ManifestException.class,
                () -> ManifestReader.locate(tmp)).getMessage();
        assertTrue(msg.contains("2 manifest files"));
    }

    @Test
    void locateReportsWhenThereIsNoManifest() {
        String msg = assertThrows(ManifestException.class,
                () -> ManifestReader.locate(tmp.resolve("docs"))).getMessage();
        assertTrue(msg.contains("no *.manifest"));
    }

    @Test
    void locateReportsAMissingPath() {
        String msg = assertThrows(ManifestException.class,
                () -> ManifestReader.locate(tmp.resolve("absent"))).getMessage();
        assertTrue(msg.contains("not found"));
    }
}
