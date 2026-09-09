package com.purplehillsbooks.md2latex;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.purplehillsbooks.exception.CommonException;

/** The shape of the folder handed to Docusaurus, and what has to travel with it. */
class DocusaurusBuilderTest {

    @TempDir Path tmp;

    private Path docs;

    @BeforeEach
    void setUp() throws IOException {
        docs = tmp.resolve("book");
        Files.createDirectories(docs);
    }

    private Path writeTempFile(String relative, String contents) throws IOException {
        Path p = docs.resolve(relative);
        Files.createDirectories(p.getParent());
        return Files.writeString(p, contents, StandardCharsets.UTF_8);
    }

    private String read(String relative) throws IOException {
        return Files.readString(site().resolve(relative), StandardCharsets.UTF_8);
    }

    private Path site() {
        return tmp.resolve("site/docs/x");
    }

    /** Runs a build over a manifest written into the book folder. */
    private Path build(String manifest) throws Exception {
        Path manifestPath = writeTempFile("book.manifest", manifest);
        Build.generateOutputFiles(ManifestReader.readManifest(manifestPath), null);
        return manifestPath;
    }

    private static final String SITE_ONLY =
            """
            title: The Book
            docusaurus:
              directory: ../site/docs/x
            """;

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Test
    void chaptersKeepTheirOwnNamesAndCarryTheirPosition() throws Exception {
        writeTempFile("intro.md", "# Introduction\n\nHello.\n");
        writeTempFile("second.md", "# Second\n\nMore.\n");
        build(SITE_ONLY + "chapters:\n  - intro.md\n  - second.md\n");

        assertTrue(Files.isRegularFile(site().resolve("intro.md")));
        assertTrue(Files.isRegularFile(site().resolve("second.md")));
        ManifestReaderTest.assertContains(read("_category_.json"), "\"label\": \"The Book\"");
        ManifestReaderTest.assertContains(read("intro.md"), "sidebar_position: 1");
        ManifestReaderTest.assertContains(read("second.md"), "sidebar_position: 2");
    }

    @Test
    void aPartDividerBecomesASidebarFolder() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: Part Two\n  - two.md\n");

        assertTrue(Files.isRegularFile(site().resolve("one.md")));
        assertTrue(Files.isRegularFile(site().resolve("part-two/two.md")));
        ManifestReaderTest.assertContains(read("part-two/_category_.json"), "\"label\": \"Part Two\"");
        // Numbering restarts inside the part, because Docusaurus orders each
        // folder among its own siblings.
        ManifestReaderTest.assertContains(read("part-two/two.md"), "sidebar_position: 1");
        // The folder's own place in the sidebar comes from its category file,
        // which is why neither it nor the pages need a number in their name.
        ManifestReaderTest.assertContains(read("part-two/_category_.json"), "\"position\": 2");
    }

    @Test
    void aNameIsCarriedAcrossExactlyAsItWasWritten() throws Exception {
        // Not slugged, not lower-cased: something already links to this name.
        writeTempFile("Moral_Realism-Part1.md", "# One\n");
        build(SITE_ONLY + "chapters:\n  - Moral_Realism-Part1.md\n");
        assertTrue(Files.isRegularFile(site().resolve("Moral_Realism-Part1.md")));
    }

    @Test
    void aMarkdownExtensionIsNormalisedSoDocusaurusCollectsThePage() throws Exception {
        writeTempFile("intro.markdown", "# One\n");
        build(SITE_ONLY + "chapters:\n  - intro.markdown\n");
        assertTrue(Files.isRegularFile(site().resolve("intro.md")));
    }

    @Test
    void twoChaptersThatWouldShareAPageAreAnErrorRatherThanALostChapter() throws Exception {
        Files.createDirectories(tmp.resolve("shared"));
        Files.writeString(tmp.resolve("shared/intro.md"), "# Shared\n");
        writeTempFile("intro.md", "# Mine\n");
        writeTempFile("book.manifest", SITE_ONLY + "chapters:\n  - intro.md\n  - ../shared/intro.md\n");
        try {
            Build.generateOutputFiles(ManifestReader.readManifest(docs.resolve("book.manifest")), null);
        } catch (ConversionException expected) {
            assertTrue(expected.getMessage().contains("same page"), expected.getMessage());
            assertFalse(Files.exists(site()), "nothing should be written");
            return;
        }
        throw new AssertionError("expected the clash to stop the build");
    }

    @Test
    void appendicesComeBackToTheTopLevel() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile("two.md", "# Two\n");
        writeTempFile("a.md", "# Appendix\n");
        build(
                SITE_ONLY
                        + "chapters:\n  - one.md\n  - part: P\n  - two.md\nappendices:\n  - a.md\n");
        assertTrue(Files.isRegularFile(site().resolve("a.md")));
    }

    @Test
    void categoryNoneWritesNoCategoryFile() throws Exception {
        writeTempFile("one.md", "# One\n");
        build(
                """
                title: The Book
                docusaurus:
                  directory: ../site/docs/x
                  category: none
                chapters:
                  - one.md
                """);
        assertFalse(Files.exists(site().resolve("_category_.json")));
    }

    // ------------------------------------------------------------------
    // Links and assets
    // ------------------------------------------------------------------

    @Test
    void anImageIsCopiedAndRepointed() throws Exception {
        writeTempFile("images/tribe.png", "png");
        writeTempFile("one.md", "# One\n\n![A tribe](images/tribe.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");

        assertTrue(Files.isRegularFile(site().resolve("images/tribe.png")));
        ManifestReaderTest.assertContains(read("one.md"), "(./images/tribe.png)");
    }

    @Test
    void anImageFromOutsideTheBookIsGatheredIntoAssets() throws Exception {
        Files.createDirectories(tmp.resolve("shared"));
        Files.writeString(tmp.resolve("shared/logo.png"), "png");
        writeTempFile("one.md", "# One\n\n![Logo](../shared/logo.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");

        assertTrue(Files.isRegularFile(site().resolve("assets/logo.png")));
        ManifestReaderTest.assertContains(read("one.md"), "(./assets/logo.png)");
    }

    @Test
    void anImageUsedTwiceIsCopiedOnce() throws Exception {
        writeTempFile("images/x.png", "png");
        writeTempFile("one.md", "# One\n\n![x](images/x.png)\n");
        writeTempFile("two.md", "# Two\n\n![x](images/x.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");

        assertTrue(Files.isRegularFile(site().resolve("images/x.png")));
        ManifestReaderTest.assertContains(read("two.md"), "(./images/x.png)");
    }

    @Test
    void assetsFalseLeavesTheReferenceAlone() throws Exception {
        writeTempFile("images/x.png", "png");
        writeTempFile("one.md", "# One\n\n![x](images/x.png)\n");
        build(
                """
                title: The Book
                docusaurus:
                  directory: ../site/docs/x
                  assets: false
                chapters:
                  - one.md
                """);
        assertFalse(Files.exists(site().resolve("images/x.png")));
        ManifestReaderTest.assertContains(read("one.md"), "(images/x.png)");
    }

    @Test
    void aLinkToAnotherChapterFollowsItToItsNewName() throws Exception {
        writeTempFile("one.md", "# One\n\nSee [two](two.md#part) and [out](https://example.com/a.md).\n");
        writeTempFile("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: P\n  - two.md\n");

        String out = read("one.md");
        ManifestReaderTest.assertContains(out, "(./p/two.md#part)");
        // An external URL that happens to end in .md is somebody else's.
        ManifestReaderTest.assertContains(out, "(https://example.com/a.md)");
    }

    @Test
    void aBrokenReferenceIsAWarningRatherThanAFailure() throws Exception {
        writeTempFile("one.md", "# One\n\n![missing](nope.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");
        // The site is still written; unlike LaTeX, a page builds around it.
        assertTrue(Files.isRegularFile(site().resolve("one.md")));
    }

    // ------------------------------------------------------------------
    // Keeping the folder clean
    // ------------------------------------------------------------------

    @Test
    void aPageWhoseChapterLeavesTheManifestIsRemoved() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");
        assertTrue(Files.isRegularFile(site().resolve("two.md")));

        build(SITE_ONLY + "chapters:\n  - one.md\n");
        assertFalse(Files.exists(site().resolve("two.md")), "stale page should be removed");
        assertTrue(Files.isRegularFile(site().resolve("one.md")));
    }

    @Test
    void aPartFolderEmptiedOfItsPagesGoesTooRatherThanLingering() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: Part Two\n  - two.md\n");
        assertTrue(Files.isDirectory(site().resolve("part-two")));

        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");
        assertFalse(Files.exists(site().resolve("part-two")), "empty part folder should go");
        assertTrue(Files.isRegularFile(site().resolve("two.md")));
    }

    @Test
    void aFileNobodyGeneratedIsLeftAlone() throws Exception {
        writeTempFile("one.md", "# One\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");
        Files.writeString(site().resolve("hand-written.md"), "# Mine\n");

        build(SITE_ONLY + "chapters:\n  - one.md\n");
        assertTrue(Files.isRegularFile(site().resolve("hand-written.md")));
    }

    // ------------------------------------------------------------------
    // Two targets, one gate
    // ------------------------------------------------------------------

    @Test
    void aBookThatWillNotCompileWritesNoSiteEither() throws Exception {
        // An emoji is fatal to pdflatex and perfectly fine on the web, so this
        // is exactly the case where the two targets could disagree.
        writeTempFile("one.md", "# One\n\nShipped it 😀 and it went fine.\n");
        writeTempFile(
                "book.manifest",
                """
                title: The Book
                latex:
                  directory: ../out
                docusaurus:
                  directory: ../site/docs/x
                chapters:
                  - one.md
                """);
        Manifest manifest = ManifestReader.readManifest(docs.resolve("book.manifest"));
        try {
            Build.generateOutputFiles(manifest, null);
        } catch (ConversionException expected) {
            assertFalse(Files.exists(site()), "no site should be written");
            assertFalse(Files.exists(tmp.resolve("out")), "no book should be written");
            return;
        }
        throw new AssertionError("expected the emoji to stop the build");
    }

    @Test
    void oneTargetCanBeAskedForOnItsOwn() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile(
                "book.manifest",
                """
                title: The Book
                latex:
                  directory: ../out
                docusaurus:
                  directory: ../site/docs/x
                chapters:
                  - one.md
                """);
        Manifest manifest = ManifestReader.readManifest(docs.resolve("book.manifest"));
        Build.generateOutputFiles(manifest, java.util.EnumSet.of(Target.DOCUSAURUS));

        assertTrue(Files.isRegularFile(site().resolve("one.md")));
        assertFalse(Files.exists(tmp.resolve("out")), "latex was not asked for");
    }

    @Test
    void askingForATargetTheManifestDoesNotDescribeIsAnError() throws Exception {
        writeTempFile("one.md", "# One\n");
        writeTempFile("book.manifest", SITE_ONLY + "chapters:\n  - one.md\n");
        Manifest manifest = ManifestReader.readManifest(docs.resolve("book.manifest"));
        try {
            Build.generateOutputFiles(manifest, java.util.EnumSet.of(Target.LATEX));
        } catch (Exception expected) {
            ManifestReaderTest.assertContains(CommonException.getFullMessage(expected), "the manifest has no 'latex:' section");
            return;
        }
        throw new AssertionError("expected a complaint about the missing latex block");
    }

    @Test
    void theSameSourceReachesBothTargets() throws Exception {
        writeTempFile("one.md", "# One\n\nW> Mind the gap.\n");
        Path manifestPath = writeTempFile(
                "book.manifest",
                """
                title: The Book
                latex:
                  directory: ../out
                docusaurus:
                  directory: ../site/docs/x
                chapters:
                  - one.md
                """);
        Build.generateOutputFiles(ManifestReader.readManifest(manifestPath), null);

        ManifestReaderTest.assertContains(read("one.md"), ":::warning");
        ManifestReaderTest.assertContains(
                Files.readString(tmp.resolve("out/chapters/01-one.tex"), StandardCharsets.UTF_8),
                "\\begin{admonition}{Warning}"
        );
    }
}
