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
        Latex latex,
        Docusaurus docusaurus,
        Document document,
        CodeStyle codeStyle,
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

    /** True when the manifest asks for a LaTeX book. */
    public boolean hasLatex() {
        return latex != null;
    }

    /** True when the manifest asks for a Docusaurus docs section. */
    public boolean hasDocusaurus() {
        return docusaurus != null;
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

    /** Where the generated LaTeX is written. Absent when the manifest asks for no LaTeX. */
    public record Latex(Path directory, String mainFile) {

        /** Absolute path of the master .tex file. */
        public Path mainPath() {
            return directory.resolve(mainFile);
        }

        /** Absolute path of the directory holding per-chapter .tex files. */
        public Path chapterPath() {
            return directory.resolve("chapters");
        }
    }

    /**
     * Where the generated Docusaurus Markdown is written, and how.
     *
     * <p>{@code directory} is a folder inside a Docusaurus site's {@code docs} tree, and everything
     * in it is generated: the chapter files, a {@code _category_.json} naming the section, and
     * copies of whatever images the chapters refer to. Nothing of the Markua source is left in it,
     * which is the point - the two trees stay separate and only one of them is hand edited.
     *
     * @param category label for the generated {@code _category_.json}; defaults to the book title
     * @param position sidebar position of the section itself, or null to leave it unset
     * @param format value written into each file's {@code format:} front matter key. {@code md}
     *     parses the file as CommonMark rather than MDX, which matters because prose that is
     *     perfectly ordinary in a book - a stray brace, a less-than sign - is JSX to MDX and fails
     *     the site build. {@code none} omits the key for a Docusaurus too old to know it.
     * @param assets whether images and other referenced files are copied into the output tree
     */
    public record Docusaurus(
            Path directory, String category, Integer position, String format, boolean assets) {

        /** True when a {@code format:} key should be written into each file's front matter. */
        public boolean writesFormat() {
            return !"none".equals(format);
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
