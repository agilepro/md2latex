package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * A parsed {@code *.manifest} file: everything needed to build one book.
 *
 * <p>A manifest sits in the folder holding its Markdown, and every relative path in it - chapter
 * files and the output directory alike - is resolved against that folder. All paths held here are
 * already absolute, so nothing downstream needs to know where the manifest lived.
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
        Dialect dialect,
        List<String> extraPreamble,
        List<Entry> frontMatter,
        List<Entry> chapters,
        List<Entry> appendices) {

    /**
     * The folder containing the manifest, which is also the folder holding the Markdown sources and
     * the base for every relative path in the file.
     */
    public Path sourceFolder() {
        return manifestFile.getParent();
    }

    /**
     * Every entry that reads a file, across all three sections in book order.
     *
     * <p>Part dividers are skipped, because they consume no source. Callers that care about where a
     * chapter sits in the book - which is only the writer of the master document - walk the three
     * lists themselves; everything else, index terms above all, is book-wide and wants this.
     */
    public List<Entry> sourceEntries() {
        return Stream.of(frontMatter, chapters, appendices)
                .flatMap(List::stream)
                .filter(e -> !e.isPart())
                .toList();
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
    public record Document(String documentClass, boolean toc, int tocDepth, int numberDepth) {

        /**
         * True for classes that provide {@code \chapter}, which decides whether a Markdown H1
         * becomes a chapter or a section.
         */
        public boolean hasChapters() {
            return documentClass.equals("book") || documentClass.equals("report");
        }
    }

    /**
     * One line of a {@code frontMatter:}, {@code chapters:} or {@code appendices:} list. Exactly
     * one of {@link #file} or {@link #partTitle} is non-null: a chapter pulls in a Markdown file, a
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
}
