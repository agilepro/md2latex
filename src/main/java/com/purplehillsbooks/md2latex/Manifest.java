package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.util.List;

/**
 * A parsed {@code *.manifest} file: everything needed to build one book.
 *
 * <p>A manifest sits in the folder holding its Markdown, and every relative
 * path in it - chapter files and the output directory alike - is resolved
 * against that folder. All paths held here are already absolute, so nothing
 * downstream needs to know where the manifest lived.
 */
public record Manifest(
        Path manifestFile,
        String title,
        String subtitle,
        String author,
        String date,
        Output output,
        Document document,
        CodeStyle codeStyle,
        List<String> extraPreamble,
        List<Entry> entries) {

    /**
     * The folder containing the manifest, which is also the folder holding the
     * Markdown sources and the base for every relative path in the file.
     */
    public Path sourceFolder() {
        return manifestFile.getParent();
    }

    /** Where the generated LaTeX is written. */
    public record Output(Path directory, String mainFile) {

        /** Absolute path of the master .tex file. */
        public Path mainPath() {
            return directory.resolve(mainFile);
        }

        /** Absolute path of the directory holding per-chapter .tex files. */
        public Path chapterPath() {
            return directory.resolve("chapters");
        }
    }

    /** LaTeX document-level settings. */
    public record Document(
            String documentClass,
            String fontSize,
            String paperSize,
            String geometry,
            boolean toc,
            int tocDepth,
            int numberDepth,
            boolean twoSide) {

        /**
         * True for classes that provide {@code \chapter}, which decides whether
         * a Markdown H1 becomes a chapter or a section.
         */
        public boolean hasChapters() {
            return documentClass.equals("book") || documentClass.equals("report");
        }
    }

    /**
     * One line of the {@code chapters:} list. Exactly one of {@link #file} or
     * {@link #partTitle} is non-null: a chapter pulls in a Markdown file, a
     * part emits a {@code \part} divider and consumes no source.
     */
    public record Entry(Path file, String titleOverride, String partTitle) {

        public static Entry newChapter(Path file, String titleOverride) {
            return new Entry(file, titleOverride, null);
        }

        public static Entry part(String partTitle) {
            return new Entry(null, null, partTitle);
        }

        public boolean isPart() {
            return partTitle != null;
        }
    }

    /** Chapter entries only, in book order, skipping any part dividers. */
    public List<Entry> chapters() {
        return entries.stream().filter(e -> !e.isPart()).toList();
    }
}
