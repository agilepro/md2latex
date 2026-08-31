package com.purplehillsbooks.md2latex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the command line end to end.
 *
 * <p>These tests are only possible because {@code main} delegates to a
 * {@code run(String[])} that returns the exit code instead of calling
 * {@link System#exit}, which would tear down the test JVM.
 */
class MainTest {

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void captureStreams() throws IOException {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));

        writeTempFile("docs/intro.md", "---\nsidebar_position: 1\n---\n# Introduction\n\nHello.\n");
        writeTempFile("docs/second.md", "---\nsidebar_position: 2\n---\n# Second\n\nMore.\n");
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String stderr() {
        return err.toString(StandardCharsets.UTF_8);
    }

    private Path writeTempFile(String relative, String content) throws IOException {
        Path p = tmp.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    private String readTempFile(String relative) throws IOException {
        return Files.readString(tmp.resolve(relative), StandardCharsets.UTF_8);
    }

    private Path standardManifest() throws IOException {
        return writeTempFile("docs/book.manifest", """
                title:     Test Book
                subtitle:  Being a Test
                author:    A. Tester
                chapters:
                  - intro.md
                  - second.md
                """);
    }

    // ------------------------------------------------------------------
    // Exit codes
    // ------------------------------------------------------------------

    @Test
    void noArgumentsPrintsUsageAndSucceeds() {
        assertEquals(0, Main.run(new String[]{}));
        assertTrue(stdout().contains("USAGE"));
    }

    @Test
    void helpFlagSucceeds() {
        assertEquals(0, Main.run(new String[]{"--help"}));
        assertTrue(stdout().contains("THE MANIFEST"));
    }

    @Test
    void unknownOptionIsAUsageError() {
        assertEquals(2, Main.run(new String[]{"--nope"}));
        assertTrue(stderr().contains("Unknown option"));
    }

    @Test
    void missingManifestArgumentIsAUsageError() {
        assertEquals(2, Main.run(new String[]{"-v"}));
        assertTrue(stderr().contains("No manifest given"));
    }

    @Test
    void outputFlagWithoutInitIsAUsageError() {
        assertEquals(2, Main.run(new String[]{"book.manifest", "-o", "x"}));
        assertTrue(stderr().contains("only applies to --init"));
    }

    @Test
    void nonExistentManifestIsAnError() {
        assertEquals(1, Main.run(new String[]{tmp.resolve("absent.manifest").toString()}));
        assertTrue(stdout().contains("not found"));
    }

    @Test
    void invalidManifestIsAnError() throws IOException {
        Path testManifest = writeTempFile("docs/bad.manifest", "chapters: [intro.md]\n");
        assertEquals(1, Main.run(new String[]{testManifest.toString()}));
        assertTrue(stdout().contains("'title' is missing"));
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    @Test
    void buildsAMasterFileAndOneFilePerChapter() throws IOException {
        Path m = standardManifest();
        assertEquals(0, Main.run(new String[]{m.toString()}));

        assertTrue(Files.exists(tmp.resolve("docs/latex/book.tex")));
        assertTrue(Files.exists(tmp.resolve("docs/latex/chapters/01-intro.tex")));
        assertTrue(Files.exists(tmp.resolve("docs/latex/chapters/02-second.tex")));
        assertTrue(stdout().contains("Wrote 2 chapter file(s)"));
    }

    @Test
    void masterFileInputsEachChapterInOrder() throws IOException {
        Main.run(new String[]{standardManifest().toString()});
        String master = readTempFile("docs/latex/book.tex");

        assertTrue(master.contains("\\title{Test Book \\\\[0.4em] \\large Being a Test}"));
        assertTrue(master.contains("\\author{A. Tester}"));
        assertTrue(master.contains("\\begin{document}"));
        assertTrue(master.contains("\\end{document}"));

        int first = master.indexOf("\\input{chapters/01-intro}");
        int second = master.indexOf("\\input{chapters/02-second}");
        assertTrue(first > 0 && second > first, "chapters must be input in order:\n" + master);
    }

    @Test
    void chapterFilesAreFragmentsWithNoPreamble() throws IOException {
        Main.run(new String[]{standardManifest().toString()});
        String chapter = readTempFile("docs/latex/chapters/01-intro.tex");

        assertTrue(chapter.contains("\\chapter{Introduction}"));
        assertFalse(chapter.contains("\\documentclass"));
        assertFalse(chapter.contains("\\begin{document}"));
    }

    @Test
    void manifestOrderOverridesFilenameAndFrontMatter() throws IOException {
        Path m = writeTempFile("docs/book.manifest", """
                title: Reversed
                chapters:
                  - second.md
                  - intro.md
                """);
        Main.run(new String[]{m.toString()});
        String master = readTempFile("docs/latex/book.tex");

        assertTrue(master.indexOf("\\input{chapters/01-second}")
                < master.indexOf("\\input{chapters/02-intro}"));
    }

    @Test
    void titleOverrideReplacesTheHeading() throws IOException {
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                chapters:
                  - file:  intro.md
                    title: A Better Name
                """);
        Main.run(new String[]{m.toString()});
        String chapter = readTempFile("docs/latex/chapters/01-intro.tex");

        assertTrue(chapter.contains("\\chapter{A Better Name}"));
        assertFalse(chapter.contains("\\chapter{Introduction}"));
    }

    @Test
    void titleOverrideIsInsertedWhenTheSourceHasNoHeading() throws IOException {
        writeTempFile("docs/headless.md", "Just a paragraph, no heading.\n");
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                chapters:
                  - file:  headless.md
                    title: Supplied Title
                """);
        assertEquals(0, Main.run(new String[]{m.toString()}));

        assertTrue(readTempFile("docs/latex/chapters/01-headless.tex").contains("\\chapter{Supplied Title}"));
        assertTrue(stderr().contains("no top-level heading"));
    }

    @Test
    void partDividersAppearInTheMasterFile() throws IOException {
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                chapters:
                  - part: The First Part
                  - intro.md
                  - part: The Second Part
                  - second.md
                """);
        Main.run(new String[]{m.toString()});
        String master = readTempFile("docs/latex/book.tex");

        assertTrue(master.contains("\\part{The First Part}"));
        assertTrue(master.contains("\\part{The Second Part}"));
        // Parts consume no source, so chapter numbering stays 1..2.
        assertTrue(master.indexOf("\\part{The First Part}")
                < master.indexOf("\\input{chapters/01-intro}"));
        assertTrue(Files.exists(tmp.resolve("docs/latex/chapters/02-second.tex")));
    }

    @Test
    void outputLocationsComeFromTheManifest() throws IOException {
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                output:
                  directory: dist/tex
                  main:      thesis.tex
                chapters:
                  - intro.md
                """);
        Main.run(new String[]{m.toString()});

        assertTrue(Files.exists(tmp.resolve("docs/dist/tex/thesis.tex")));
        assertTrue(Files.exists(tmp.resolve("docs/dist/tex/chapters/01-intro.tex")));
        assertTrue(readTempFile("docs/dist/tex/thesis.tex").contains("\\input{chapters/01-intro}"));
    }

    @Test
    void imagePathsAreRelativeToTheMasterFileNotTheChapterFile() throws IOException {
        Files.createDirectories(tmp.resolve("docs"));
        Files.writeString(tmp.resolve("docs/pic.png"), "not really a png");
        writeTempFile("docs/withimage.md", "# Pictures\n\n![cap](pic.png)\n");
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                chapters:
                  - withimage.md
                """);
        Main.run(new String[]{m.toString()});

        String chapter = readTempFile("docs/latex/chapters/01-withimage.tex");
        // From docs/latex/ (where pdflatex runs) the image is ../pic.png,
        // NOT ../../pic.png as it would be relative to docs/latex/chapters/.
        assertTrue(chapter.contains("{../pic}.png"),
                "image path should be relative to the master document:\n" + chapter);
    }

    @Test
    void directoryArgumentFindsTheManifest() throws IOException {
        standardManifest();
        assertEquals(0, Main.run(new String[]{tmp.resolve("docs").toString()}));
        assertTrue(Files.exists(tmp.resolve("docs/latex/book.tex")));
    }

    @Test
    void thePreambleDoesNotDependOnUlem() throws IOException {
        Main.run(new String[]{standardManifest().toString()});
        String bookContents = readTempFile("docs/latex/book.tex");
        assertFalse(bookContents.contains("ulem"), "ulem should no longer be required");
    }

    @Test
    void extraPreambleLinesAreEmitted() throws IOException {
        Path m = writeTempFile("docs/book.manifest", """
                title: X
                preamble:
                  - \\usepackage{microtype}
                chapters:
                  - intro.md
                """);
        Main.run(new String[]{m.toString()});
        assertTrue(readTempFile("docs/latex/book.tex").contains("\\usepackage{microtype}"));
    }

    @Test
    void verboseListsChapterFiles() throws IOException {
        Main.run(new String[]{standardManifest().toString(), "-v"});
        assertTrue(stdout().contains("chapters/01-intro.tex"));
    }

    // ------------------------------------------------------------------
    // Scaffolding
    // ------------------------------------------------------------------

    @Test
    void initWritesTheManifestIntoTheSourceFolderByDefault() throws IOException {
        writeTempFile("docs/zzz.md", "---\nsidebar_position: 0\n---\n# First By Position\n");

        assertEquals(0, Main.run(new String[]{
                "--init", tmp.resolve("docs").toString(),
                "--title", "Scaffolded"}));

        Path target = tmp.resolve("docs/book.manifest");
        assertTrue(Files.exists(target), "manifest should land beside the Markdown");

        String yaml = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(yaml.contains("title:    \"Scaffolded\""));
        assertFalse(yaml.contains("sourceDir"), "sourceDir is no longer part of the schema");
        // Files are in the manifest's own folder, so the paths are bare names.
        assertTrue(yaml.contains("- \"intro.md\""), yaml);
        assertTrue(yaml.indexOf("zzz.md") < yaml.indexOf("intro.md"));
        assertTrue(yaml.contains("# First By Position"));
        assertTrue(stdout().contains("3 chapter(s)"));
    }

    @Test
    void initHonoursAnExplicitOutputPathAndStillResolvesChapters() throws IOException {
        Path target = tmp.resolve("elsewhere/generated.manifest");
        assertEquals(0, Main.run(new String[]{
                "--init", tmp.resolve("docs").toString(), "-o", target.toString()}));

        String yaml = Files.readString(target, StandardCharsets.UTF_8);
        // The manifest sits one level away, so chapter paths reach back out.
        assertTrue(yaml.contains("../docs/intro.md"), yaml);

        assertEquals(0, Main.run(new String[]{target.toString()}));
        assertTrue(Files.exists(tmp.resolve("elsewhere/latex/book.tex")));
    }

    @Test
    void aScaffoldedManifestBuildsWithoutEditing() throws IOException {
        assertEquals(0, Main.run(new String[]{
                "--init", tmp.resolve("docs").toString()}));
        assertEquals(0, Main.run(new String[]{
                tmp.resolve("docs/book.manifest").toString()}));
        assertTrue(Files.exists(tmp.resolve("docs/latex/book.tex")));
    }

    @Test
    void initOnADirectoryWithNoMarkdownIsAnError() throws IOException {
        Files.createDirectories(tmp.resolve("empty"));
        assertEquals(1, Main.run(new String[]{
                "--init", tmp.resolve("empty").toString(),
                "-o", tmp.resolve("x.manifest").toString()}));
        assertTrue(stdout().contains("no .md files"));
    }
}
