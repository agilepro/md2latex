package com.purplehillsbooks.md2latex;

import com.purplehillsbooks.streams.MemFile;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Manifest} into a complete set of LaTeX files.
 *
 * <p>The layout is always the same shape:
 *
 * <pre>
 *   &lt;output.directory&gt;/
 *       book.tex              master file: preamble, then one \input per chapter
 *       chapters/
 *           01-introduction.tex
 *           02-definitions.tex
 * </pre>
 *
 * <p>Every chapter is converted into memory first. Only if the whole book converted cleanly is
 * anything written to disk, so a build that fails never leaves a half-written or stale book behind.
 *
 * <p>Compile by running the LaTeX engine on the master file from inside the output directory.
 */
public final class BookBuilder {

    private final Manifest manifest;
    private final List<Problem> problems;

    public BookBuilder(Manifest manifest, List<Problem> problems) {
        this.manifest = manifest;
        this.problems = problems;
    }

    /**
     * What the build intends to write. {@code hasIndex} tells the caller whether the book needs a
     * makeindex run between pdflatex passes.
     */
    public record Plan(
            BuildPlan writes, Path mainFile, List<Path> chapterFiles, boolean hasIndex) {}

    /** One converted chapter, held until the whole book is known to be sound. */
    private record Pending(Path target, String contents, String inputPath) {}

    /**
     * One of the book's three sections, with the LaTeX command that opens it, or null when nothing
     * needs to be emitted before its entries.
     */
    private record Section(List<Manifest.Entry> entries, String opener) {}

    /**
     * @param sources file contents, already read once by the caller so both targets share the read
     */
    public Plan plan(Map<Path, String> sources) throws Exception {
        Manifest.Latex out = manifest.latex();

        // Index terms are book-wide, so the whole set must be known before the
        // first chapter is converted.
        List<Manifest.Entry> allSources = manifest.sourceEntries();
        IndexTerms indexTerms = IndexTerms.collect(allSources, sources, problems);

        Md2Latex converter =
                new Md2Latex(
                        manifest.codeStyle(),
                        manifest.document().hasChapters(),
                        problems,
                        indexTerms);

        int width = Math.max(2, String.valueOf(allSources.size()).length());

        List<Pending> pending = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        body.append("% Chapters, in the order given by the manifest.\n");

        // The three sections of a book, and the structural command that opens
        // each. \mainmatter always fires, because chapters are required; the
        // other two are only meaningful when their section has content.
        List<Section> sections =
                List.of(
                        new Section(manifest.frontMatter(), null),
                        new Section(manifest.chapters(), "\\mainmatter"),
                        new Section(
                                manifest.appendices(),
                                manifest.appendices().isEmpty() ? null : "\\appendix"));

        int index = 0;
        for (Section section : sections) {
            if (section.opener() != null) {
                body.append('\n').append(section.opener()).append("\n\n");
            }
            for (Manifest.Entry entry : section.entries()) {
                if (entry.isPart()) {
                    body.append('\n')
                            .append("\\part{")
                            .append(LatexEscaper.text(entry.partTitle()))
                            .append("}\n\n");
                    continue;
                }

                index++;
                Path source = entry.file();
                String markdown = sources.get(source);

                // Image paths must be relative to the master document's directory,
                // because LaTeX resolves \includegraphics against the job's working
                // directory rather than the directory of the \input-ed file.
                Md2Latex.Converted converted =
                        converter.convert(markdown, source, out.directory(), entry.titleOverride());

                String latex = converted.latex();
                if (entry.titleOverride() != null && !converted.titleApplied()) {
                    // The source had no H1 to rename, so supply the heading.
                    problems.add(
                            new Problem(
                                    Problem.Severity.WARNING,
                                    source,
                                    0,
                                    0,
                                    null,
                                    "no top-level heading found; inserting the manifest title '"
                                            + entry.titleOverride()
                                            + "'",
                                    "add a '# "
                                            + entry.titleOverride()
                                            + "' heading to the file, "
                                            + "or drop the 'title' key from its manifest entry"));
                    latex =
                            "\\"
                                    + (manifest.document().hasChapters() ? "chapter" : "section")
                                    + "{"
                                    + LatexEscaper.text(entry.titleOverride())
                                    + "}\n\n"
                                    + latex;
                }

                String stem = String.format("%0" + width + "d-%s", index, Slug.ofFile(source));
                Path target = out.chapterPath().resolve(stem + ".tex");
                pending.add(
                        new Pending(target, chapterHeader(source) + latex, inputPath(out, target)));
                body.append("\\input{").append(inputPath(out, target)).append("}\n");
            }
        }

        // A term that matched nowhere in the entire book is almost always a
        // typo. Reported before the error gate so it lands in the same report.
        for (IndexTerms.Term term : indexTerms.unused()) {
            problems.add(
                    new Problem(
                            Problem.Severity.WARNING,
                            term.declaredIn(),
                            term.declaredLine(),
                            0,
                            null,
                            "index term '"
                                    + term.canonical()
                                    + "' does not appear anywhere in the book",
                            "check the spelling; matching is whole-word, so 'tribe' "
                                    + "does not match 'tribes'"));
        }

        BuildPlan writes = new BuildPlan();
        List<Path> chapterFiles = new ArrayList<>();
        for (Pending p : pending) {
            writes.add(p.target(), p.contents());
            chapterFiles.add(p.target());
        }

        MemFile mainDocument = new MemFile();
        Writer mainWriter = mainDocument.getWriter();

        Preamble.beginTheBook(manifest, mainWriter);
        mainWriter.write(body.toString().stripTrailing() + "\n");
        Preamble.endTheBook(manifest, mainWriter);
        mainWriter.close();

        writes.add(out.mainPath(), mainDocument.toString());

        return new Plan(writes, out.mainPath(), chapterFiles, true);
    }

    private String chapterHeader(Path source) {
        return "% Generated from " + source.getFileName() + " - do not edit.\n\n";
    }

    /** Path for {@code \input}, relative to the master file, without extension. */
    private static String inputPath(Manifest.Latex out, Path chapterFile) {
        String relative = out.directory().relativize(chapterFile).toString().replace('\\', '/');
        return relative.endsWith(".tex") ? relative.substring(0, relative.length() - 4) : relative;
    }
}
