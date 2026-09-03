package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command line options, parsed by hand to keep the shaded jar free of a third-party CLI dependency.
 *
 * <p>Everything that shapes the output now lives in the manifest, so the command line only says
 * which manifest to build.
 */
public final class Options {

    /** Manifest file, or a directory containing exactly one *.manifest. */
    public Path manifest;

    /** Set by --init: directory of Markdown to scaffold a manifest from. */
    public Path initFrom;

    /** Destination for --init. Defaults to book.manifest inside the source folder. */
    public Path initTo;

    /** Title to seed a scaffolded manifest with. */
    public String initTitle = "Untitled Book";

    public boolean verbose;
    public boolean help;

    public static final String USAGE =
            """
            md2latex - build a LaTeX book from Markdown, driven by a manifest

            USAGE
              md2latex <manifest>            build the book described by a manifest
              md2latex --init <source-dir>   write a starter manifest for a folder

              <manifest>  a *.manifest file, or a directory containing exactly one

            OPTIONS
              -o, --output <path>   where --init writes the manifest
                                    (default: <source-dir>/book.manifest)
                  --title <text>    title to seed a scaffolded manifest with
              -v, --verbose         list each file as it is written
              -h, --help            show this message

            THE MANIFEST
              A manifest is YAML and describes one book. It lives in the folder
              holding the Markdown, and every relative path in it is resolved
              against that folder. Minimal example:

                title: My Book
                chapters:
                  - introduction.md
                  - chapter-one.md

              A chapter outside the folder is reached with a relative path:

                chapters:
                  - ../shared/preface.md

              Full set of keys:

                title       required. Book title.
                subtitle    optional. Set smaller under the title.
                author      optional.
                date        optional. Empty means no date is printed.
                output:
                  directory   where .tex files go.       Default: latex
                  main        master file name.          Default: book.tex
                  chapters    subdirectory for chapters. Default: chapters
                document:
                  class       book | report | article.   Default: book
                  toc         Default: true
                  tocDepth    Default: 2
                  numberDepth Default: 2
                code        listings | minted | verbatim. Default: listings
                preamble    list of raw lines appended to the preamble.
                chapters    required. Ordered list. Each item is either a
                            filename, or a mapping with:
                              file    the Markdown file
                              title   overrides the file's H1
                            or a mapping with:
                              part    inserts a \\part divider, reads no file

            MARKDOWN FRONT MATTER
              Chapter files may carry a YAML header. Recognised keys:

                index   comma-separated words to build a LaTeX index from, e.g.

                          ---
                          indexTerms: tribe, eudaimonia, moral realism
                          ---

                        Terms are pooled across the WHOLE book, so a word
                        declared in one chapter is indexed everywhere it
                        appears. Matching is whole-word and case-insensitive:
                        'tribe' matches 'Tribe' but not 'tribes'. Only
                        paragraphs and headings are searched, never code,
                        URLs, image paths, lists or tables. Each term is
                        recorded once per section, so a common word does not
                        become an unreadable run of page numbers.

                        Must be one line. A YAML list under 'indexTerms:' is
                        rejected. Terms may not contain " | @ ! \\ { } % ;
                        for a sub-entry, place the marker by hand:
                          <!-- latex: \\index{tribe!in-group} -->

            OUTPUT
              A complete, compilable set of files, relative to the manifest:

                latex/book.tex               master document
                latex/chapters/01-....tex    one file per chapter, \\input by the master

              Compile from inside the output directory:
                cd latex && pdflatex book.tex

              With an index, makeindex must run between two pdflatex passes:
                cd latex && pdflatex book.tex && makeindex book && pdflatex book.tex

              The exact command is printed at the end of every build.

            EXAMPLES
              md2latex --init docs/Morality --title "Essentials of Moral Realism"
              md2latex docs/Morality/book.manifest -v
              md2latex docs/Morality
            """;

    public static Options parse(String[] args) {
        Options o = new Options();
        List<String> positional = new ArrayList<>();
        Path output = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> o.help = true;
                case "-v", "--verbose" -> o.verbose = true;
                case "--init" -> o.initFrom = Paths.get(requireValue(args, ++i, a));
                case "-o", "--output" -> output = Paths.get(requireValue(args, ++i, a));
                case "--title" -> o.initTitle = requireValue(args, ++i, a);
                default -> {
                    if (a.startsWith("-") && a.length() > 1) {
                        throw new IllegalArgumentException("Unknown option: " + a);
                    }
                    positional.add(a);
                }
            }
        }

        if (o.help) {
            return o;
        }

        if (o.initFrom != null) {
            if (!positional.isEmpty()) {
                throw new IllegalArgumentException(
                        "--init takes the source directory as its value; unexpected extra "
                                + "argument: "
                                + positional.get(0));
            }
            // A manifest belongs beside the Markdown it describes.
            o.initTo = output != null ? output : o.initFrom.resolve("book.manifest");
            return o;
        }

        if (output != null) {
            throw new IllegalArgumentException(
                    "--output only applies to --init; all build output paths come from "
                            + "the manifest.");
        }
        if (positional.isEmpty()) {
            throw new IllegalArgumentException(
                    "No manifest given. Pass a *.manifest file, or a directory "
                            + "containing one.");
        }
        if (positional.size() > 1) {
            throw new IllegalArgumentException(
                    "Expected one manifest but got " + positional.size() + ": " + positional);
        }
        o.manifest = Paths.get(positional.get(0));
        return o;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value.");
        }
        return args[index];
    }
}
