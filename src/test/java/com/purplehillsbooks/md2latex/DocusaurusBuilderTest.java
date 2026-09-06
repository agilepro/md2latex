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

/** The shape of the folder handed to Docusaurus, and what has to travel with it. */
class DocusaurusBuilderTest {

    @TempDir Path tmp;

    private Path docs;

    @BeforeEach
    void setUp() throws IOException {
        docs = tmp.resolve("book");
        Files.createDirectories(docs);
    }

    private void write(String relative, String contents) throws IOException {
        Path p = docs.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, contents, StandardCharsets.UTF_8);
    }

    private String read(String relative) throws IOException {
        return Files.readString(site().resolve(relative), StandardCharsets.UTF_8);
    }

    private Path site() {
        return tmp.resolve("site/docs/x");
    }

    /** Runs a build over a manifest written into the book folder. */
    private void build(String manifest) throws Exception {
        write("book.manifest", manifest);
        Build.run(ManifestReader.readManifest(docs.resolve("book.manifest")), null);
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
        write("intro.md", "# Introduction\n\nHello.\n");
        write("second.md", "# Second\n\nMore.\n");
        build(SITE_ONLY + "chapters:\n  - intro.md\n  - second.md\n");

        assertTrue(Files.isRegularFile(site().resolve("intro.md")));
        assertTrue(Files.isRegularFile(site().resolve("second.md")));
        assertTrue(read("_category_.json").contains("\"label\": \"The Book\""));
        assertTrue(read("intro.md").contains("sidebar_position: 1"));
        assertTrue(read("second.md").contains("sidebar_position: 2"));
    }

    @Test
    void aPartDividerBecomesASidebarFolder() throws Exception {
        write("one.md", "# One\n");
        write("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: Part Two\n  - two.md\n");

        assertTrue(Files.isRegularFile(site().resolve("one.md")));
        assertTrue(Files.isRegularFile(site().resolve("part-two/two.md")));
        assertTrue(read("part-two/_category_.json").contains("\"label\": \"Part Two\""));
        // Numbering restarts inside the part, because Docusaurus orders each
        // folder among its own siblings.
        assertTrue(read("part-two/two.md").contains("sidebar_position: 1"));
        // The folder's own place in the sidebar comes from its category file,
        // which is why neither it nor the pages need a number in their name.
        assertTrue(read("part-two/_category_.json").contains("\"position\": 2"));
    }

    @Test
    void aNameIsCarriedAcrossExactlyAsItWasWritten() throws Exception {
        // Not slugged, not lower-cased: something already links to this name.
        write("Moral_Realism-Part1.md", "# One\n");
        build(SITE_ONLY + "chapters:\n  - Moral_Realism-Part1.md\n");
        assertTrue(Files.isRegularFile(site().resolve("Moral_Realism-Part1.md")));
    }

    @Test
    void aMarkdownExtensionIsNormalisedSoDocusaurusCollectsThePage() throws Exception {
        write("intro.markdown", "# One\n");
        build(SITE_ONLY + "chapters:\n  - intro.markdown\n");
        assertTrue(Files.isRegularFile(site().resolve("intro.md")));
    }

    @Test
    void twoChaptersThatWouldShareAPageAreAnErrorRatherThanALostChapter() throws Exception {
        Files.createDirectories(tmp.resolve("shared"));
        Files.writeString(tmp.resolve("shared/intro.md"), "# Shared\n");
        write("intro.md", "# Mine\n");
        write("book.manifest", SITE_ONLY + "chapters:\n  - intro.md\n  - ../shared/intro.md\n");
        try {
            Build.run(ManifestReader.readManifest(docs.resolve("book.manifest")), null);
        } catch (ConversionException expected) {
            assertTrue(expected.getMessage().contains("same page"), expected.getMessage());
            assertFalse(Files.exists(site()), "nothing should be written");
            return;
        }
        throw new AssertionError("expected the clash to stop the build");
    }

    @Test
    void appendicesComeBackToTheTopLevel() throws Exception {
        write("one.md", "# One\n");
        write("two.md", "# Two\n");
        write("a.md", "# Appendix\n");
        build(
                SITE_ONLY
                        + "chapters:\n  - one.md\n  - part: P\n  - two.md\nappendices:\n  - a.md\n");
        assertTrue(Files.isRegularFile(site().resolve("a.md")));
    }

    @Test
    void categoryNoneWritesNoCategoryFile() throws Exception {
        write("one.md", "# One\n");
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
        write("images/tribe.png", "png");
        write("one.md", "# One\n\n![A tribe](images/tribe.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");

        assertTrue(Files.isRegularFile(site().resolve("images/tribe.png")));
        assertTrue(read("one.md").contains("(./images/tribe.png)"));
    }

    @Test
    void anImageFromOutsideTheBookIsGatheredIntoAssets() throws Exception {
        Files.createDirectories(tmp.resolve("shared"));
        Files.writeString(tmp.resolve("shared/logo.png"), "png");
        write("one.md", "# One\n\n![Logo](../shared/logo.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");

        assertTrue(Files.isRegularFile(site().resolve("assets/logo.png")));
        assertTrue(read("one.md").contains("(./assets/logo.png)"));
    }

    @Test
    void anImageUsedTwiceIsCopiedOnce() throws Exception {
        write("images/x.png", "png");
        write("one.md", "# One\n\n![x](images/x.png)\n");
        write("two.md", "# Two\n\n![x](images/x.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");

        assertTrue(Files.isRegularFile(site().resolve("images/x.png")));
        assertTrue(read("two.md").contains("(./images/x.png)"));
    }

    @Test
    void assetsFalseLeavesTheReferenceAlone() throws Exception {
        write("images/x.png", "png");
        write("one.md", "# One\n\n![x](images/x.png)\n");
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
        assertTrue(read("one.md").contains("(images/x.png)"));
    }

    @Test
    void aLinkToAnotherChapterFollowsItToItsNewName() throws Exception {
        write("one.md", "# One\n\nSee [two](two.md#part) and [out](https://example.com/a.md).\n");
        write("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: P\n  - two.md\n");

        String out = read("one.md");
        assertTrue(out.contains("(./p/two.md#part)"), out);
        // An external URL that happens to end in .md is somebody else's.
        assertTrue(out.contains("(https://example.com/a.md)"), out);
    }

    @Test
    void aBrokenReferenceIsAWarningRatherThanAFailure() throws Exception {
        write("one.md", "# One\n\n![missing](nope.png)\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n");
        // The site is still written; unlike LaTeX, a page builds around it.
        assertTrue(Files.isRegularFile(site().resolve("one.md")));
    }

    // ------------------------------------------------------------------
    // Keeping the folder clean
    // ------------------------------------------------------------------

    @Test
    void aPageWhoseChapterLeavesTheManifestIsRemoved() throws Exception {
        write("one.md", "# One\n");
        write("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");
        assertTrue(Files.isRegularFile(site().resolve("two.md")));

        build(SITE_ONLY + "chapters:\n  - one.md\n");
        assertFalse(Files.exists(site().resolve("two.md")), "stale page should be removed");
        assertTrue(Files.isRegularFile(site().resolve("one.md")));
    }

    @Test
    void aPartFolderEmptiedOfItsPagesGoesTooRatherThanLingering() throws Exception {
        write("one.md", "# One\n");
        write("two.md", "# Two\n");
        build(SITE_ONLY + "chapters:\n  - one.md\n  - part: Part Two\n  - two.md\n");
        assertTrue(Files.isDirectory(site().resolve("part-two")));

        build(SITE_ONLY + "chapters:\n  - one.md\n  - two.md\n");
        assertFalse(Files.exists(site().resolve("part-two")), "empty part folder should go");
        assertTrue(Files.isRegularFile(site().resolve("two.md")));
    }

    @Test
    void aFileNobodyGeneratedIsLeftAlone() throws Exception {
        write("one.md", "# One\n");
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
        write("one.md", "# One\n\nShipped it 😀 and it went fine.\n");
        write(
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
            Build.run(manifest, null);
        } catch (ConversionException expected) {
            assertFalse(Files.exists(site()), "no site should be written");
            assertFalse(Files.exists(tmp.resolve("out")), "no book should be written");
            return;
        }
        throw new AssertionError("expected the emoji to stop the build");
    }

    @Test
    void oneTargetCanBeAskedForOnItsOwn() throws Exception {
        write("one.md", "# One\n");
        write(
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
        Build.run(manifest, java.util.EnumSet.of(Target.DOCUSAURUS));

        assertTrue(Files.isRegularFile(site().resolve("one.md")));
        assertFalse(Files.exists(tmp.resolve("out")), "latex was not asked for");
    }

    @Test
    void askingForATargetTheManifestDoesNotDescribeIsAnError() throws Exception {
        write("one.md", "# One\n");
        write("book.manifest", SITE_ONLY + "chapters:\n  - one.md\n");
        Manifest manifest = ManifestReader.readManifest(docs.resolve("book.manifest"));
        try {
            Build.run(manifest, java.util.EnumSet.of(Target.LATEX));
        } catch (ManifestException expected) {
            assertTrue(expected.getMessage().contains("latex"), expected.getMessage());
            return;
        }
        throw new AssertionError("expected a complaint about the missing latex block");
    }

    @Test
    void theSameSourceReachesBothTargets() throws Exception {
        write("one.md", "# One\n\nW> Mind the gap.\n");
        write(
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
        Build.run(ManifestReader.readManifest(docs.resolve("book.manifest")), null);

        assertTrue(read("one.md").contains(":::warning"));
        assertTrue(
                Files.readString(tmp.resolve("out/chapters/01-one.tex"), StandardCharsets.UTF_8)
                        .contains("\\begin{admonition}{Warning}"));
    }
}
