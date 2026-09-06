package com.purplehillsbooks.md2latex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Writes a starter manifest for a directory that already contains Markdown.
 *
 * <p>Chapter order is taken from the {@code sidebar_position} front matter when present, falling
 * back to filename, which reproduces the reading order of a Docusaurus site. The result is meant to
 * be edited, not treated as final.
 */
public final class ManifestScaffold {

    private ManifestScaffold() {}

    /**
     * @param sourceDir directory to scan for Markdown
     * @param manifestFile where the manifest is written
     * @param title book title to seed the manifest with
     * @return the number of chapters listed
     */
    public static int write(Path sourceDir, Path manifestFile, String title) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir)) {
            files = walk.filter(Files::isRegularFile).filter(ManifestScaffold::isMarkdown).toList();
        }
        if (files.isEmpty()) {
            throw new IOException("no .md files found under " + sourceDir);
        }

        record Candidate(Path path, String relative, int position, String heading) {}

        List<Candidate> candidates = new ArrayList<>();
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            MarkdownLoader.Result parsed = MarkdownLoader.process(text);
            candidates.add(
                    new Candidate(
                            file,
                            sourceDir.relativize(file).toString().replace('\\', '/'),
                            parsed.sidebarPosition(),
                            firstHeading(parsed.body())));
        }
        candidates.sort(
                Comparator.comparingInt(Candidate::position)
                        .thenComparing(c -> c.relative().toLowerCase()));

        Path manifestDir = manifestFile.toAbsolutePath().normalize().getParent();

        StringBuilder y = new StringBuilder();
        y.append("# Book manifest for md2latex.\n");
        y.append("# This file lives in the folder with the Markdown, and every path\n");
        y.append("# below is relative to this file. Reorder the chapters list to\n");
        y.append("# reorder the book; delete a line to leave that file out.\n\n");
        y.append("title:    ").append(quote(title)).append('\n');
        y.append("subtitle: \"\"\n");
        y.append("author:   \"\"\n");
        y.append("date:     \"\"\n\n");
        y.append("# Where the generated LaTeX goes, relative to this file. Delete this\n");
        y.append("# block, and name a docusaurus one, to build only the site.\n");
        y.append("latex:\n");
        y.append("  directory: latex\n");
        y.append("  main:      book.tex\n\n");
        y.append("# Where the generated Docusaurus pages go, relative to this file.\n");
        y.append("# Uncomment to build the site as well as, or instead of, the book.\n");
        y.append("# Everything in that folder is generated; edit the Markua instead.\n");
        y.append("# docusaurus:\n");
        y.append("#   directory: ../../site/docs/").append(Slug.of(title)).append('\n');
        y.append("#   category:  ").append(quote(title)).append("   # sidebar label\n");
        y.append("#   position:  1                     # where the section sits\n");
        y.append("#   format:    md                    # md | mdx | none\n");
        y.append("#   assets:    true                  # copy images alongside\n\n");
        y.append("document:\n");
        y.append("  class:       book        # book | report | article\n");
        y.append("  toc:         true\n");
        y.append("  tocDepth:    2\n");
        y.append("  numberDepth: 2\n");
        y.append("# How fenced code blocks are rendered: listings | minted | verbatim\n");
        y.append("code: listings\n\n");
        y.append("# Raw lines appended to the preamble, e.g.\n");
        y.append("# preamble:\n");
        y.append("#   - \\usepackage{microtype}\n\n");
        y.append("# Chapter-like files that belong in the front matter, before\n");
        y.append("# \\mainmatter: a foreword, a preface, acknowledgements. Unnumbered\n");
        y.append("# and on roman page numbers. Same entry format as 'chapters'.\n");
        y.append("# frontMatter:\n");
        y.append("#   - foreword.md\n\n");
        y.append("# Chapters, in book order, relative to this file. Either a bare\n");
        y.append("# filename, or a mapping with 'file' plus an optional 'title' that\n");
        y.append("# overrides the H1. A mapping with 'part' inserts a part divider\n");
        y.append("# and reads no file. A file outside this folder is fine too, e.g.\n");
        y.append("#   - ../shared/preface.md\n");
        y.append("chapters:\n");
        for (Candidate c : candidates) {
            y.append("  - ").append(quote(relativize(manifestDir, c.path())));
            if (c.heading() != null) {
                y.append("   # ").append(c.heading());
            }
            y.append('\n');
        }

        y.append("\n# Appendices, after \\appendix, lettered rather than numbered.\n");
        y.append("# Same entry format as 'chapters'.\n");
        y.append("# appendices:\n");
        y.append("#   - appendix-a.md\n");

        Files.createDirectories(manifestDir);
        Files.writeString(manifestFile, y.toString(), StandardCharsets.UTF_8);
        return candidates.size();
    }

    private static boolean isMarkdown(Path p) {
        String name = p.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    /** First ATX heading in the body, used only as a comment in the manifest. */
    private static String firstHeading(String body) {
        for (String line : body.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("#")) {
                String heading = t.replaceFirst("^#+\\s*", "").strip();
                if (!heading.isEmpty()) {
                    return heading;
                }
            }
        }
        return null;
    }

    private static String relativize(Path from, Path to) {
        try {
            String r =
                    from.relativize(to.toAbsolutePath().normalize()).toString().replace('\\', '/');
            return r.isEmpty() ? "." : r;
        } catch (IllegalArgumentException e) {
            return to.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }

    /** Quotes a YAML scalar defensively; these values are machine written. */
    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
