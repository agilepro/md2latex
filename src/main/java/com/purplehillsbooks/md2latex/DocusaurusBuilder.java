package com.purplehillsbooks.md2latex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a {@link Manifest} into a folder a Docusaurus site can serve.
 *
 * <p>The manifest already says everything a sidebar needs - the order of the chapters, which of
 * them are front matter and which are appendices, and where the author drew a part divider - so
 * none of that has to be said a second time in the site's own configuration. The shape produced is:
 *
 * <pre>
 *   &lt;docusaurus.directory&gt;/
 *       _category_.json           the book, as one section of the sidebar
 *       introduction.md
 *       what-morality-is.md
 *       part-two/                 a manifest 'part:' divider
 *           _category_.json
 *           the-argument.md
 *       images/tribe.png          copied from beside the Markua source
 * </pre>
 *
 * <p>Every page keeps the name of the file it came from. Ordering is carried by the {@code
 * sidebar_position} front matter and by each folder's {@code _category_.json}, so nothing has to be
 * encoded in a filename as well - which leaves the generated tree readable against its source, and
 * leaves a name that something else already links to alone.
 *
 * <p>Everything in that folder is generated. A file left over from an earlier run whose chapter has
 * since been dropped from the manifest is removed, so the folder never accumulates pages that no
 * longer belong to the book; the record of what was generated lives in a small stamp file that
 * Docusaurus ignores.
 *
 * <p>Nothing is written here. The whole section is converted into a {@link BuildPlan} and handed
 * back, so a book that fails to convert never leaves a half-written site behind - and never leaves
 * a fresh site beside a book that would not compile.
 */
public final class DocusaurusBuilder {

    /**
     * Names the files this converter generated, so the next run can tell them from anything else in
     * the folder. Dot-prefixed, which Docusaurus ignores when it collects documents.
     */
    static final String STAMP = ".md2latex-generated";

    private final Manifest manifest;
    private final Manifest.Docusaurus settings;
    private final List<Problem> problems;

    public DocusaurusBuilder(Manifest manifest, List<Problem> problems) {
        this.manifest = manifest;
        this.settings = manifest.docusaurus();
        this.problems = problems;
    }

    /** One chapter's place in the generated tree, worked out before anything is converted. */
    private record Placed(Manifest.Entry entry, Path file, int sidebarPosition) {}

    /** What was planned, for the caller to report on. */
    public record Plan(BuildPlan writes, List<Path> documents, int assetCount) {}

    /**
     * @param sources file contents, already read once by the caller so both targets share the read
     */
    public Plan plan(Map<Path, String> sources) throws IOException {
        Path root = settings.directory();
        List<Placed> placed = new ArrayList<>();
        List<BuildPlan.NewFile> categories = new ArrayList<>();
        layout(root, placed, categories);

        DocusaurusAssets assets =
                new DocusaurusAssets(root, manifest.sourceFolder(), settings.assets(), problems);
        for (Placed p : placed) {
            assets.registerDocument(p.entry().file(), p.file());
        }

        Md2Docusaurus converter = new Md2Docusaurus(settings);
        BuildPlan writes = new BuildPlan();
        List<Path> documents = new ArrayList<>();
        Set<String> generated = new LinkedHashSet<>();

        for (BuildPlan.NewFile category : categories) {
            writes.add(category.target(), category.contents());
            generated.add(relative(root, category.target()));
        }

        for (Placed p : placed) {
            Path source = p.entry().file();
            String markua = sources.get(source);
            Md2Docusaurus.Converted converted =
                    converter.convert(
                            markua,
                            source,
                            p.entry().titleOverride(),
                            p.sidebarPosition(),
                            assets.forChapter(source, p.file(), lines(markua)));

            String markdown = converted.markdown();
            if (p.entry().titleOverride() != null && !converted.titleApplied()) {
                problems.add(
                        new Problem(
                                Problem.Severity.WARNING,
                                source,
                                0,
                                0,
                                null,
                                "no top-level heading found; inserting the manifest title '"
                                        + p.entry().titleOverride()
                                        + "'",
                                "add a '# "
                                        + p.entry().titleOverride()
                                        + "' heading to the file, "
                                        + "or drop the 'title' key from its manifest entry"));
                markdown = insertHeading(markdown, p.entry().titleOverride());
            }

            writes.add(p.file(), markdown);
            documents.add(p.file());
            generated.add(relative(root, p.file()));
        }

        for (Map.Entry<Path, Path> copy : assets.ordered()) {
            writes.copy(copy.getKey(), copy.getValue());
            generated.add(relative(root, copy.getValue()));
        }

        for (Path stale : stale(root, generated)) {
            writes.removeStale(stale);
        }
        writes.pruneEmptyDirectoriesUnder(root);
        writes.add(root.resolve(STAMP), stampContents(generated));

        return new Plan(writes, documents, assets.ordered().size());
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /**
     * Decides where every chapter goes and what its sidebar position is.
     *
     * <p>A page keeps the name of the Markua file it came from, so that the generated tree can be
     * read against its source and any link or bookmark already pointing at a page still lands on
     * it. Order is carried entirely by the {@code sidebar_position} front matter, which the
     * manifest decides; there is no number on the front of the filename doing it as well.
     *
     * <p>A part divider opens a subfolder, which is how Docusaurus groups pages, and takes the next
     * position at the top level; chapters inside it are numbered from one again. Appendices always
     * return to the top level, whatever part was open when the chapters ran out.
     */
    private void layout(Path root, List<Placed> placed, List<BuildPlan.NewFile> categories) {
        if (settings.category() != null && !"none".equalsIgnoreCase(settings.category())) {
            categories.add(
                    new BuildPlan.NewFile(
                            root.resolve("_category_.json"),
                            category(settings.category(), settings.position())));
        }

        int rootPosition = 1;
        int partPosition = 1;
        Path folder = root;

        // Without a number on the front of each name, two chapters called the
        // same thing in different source folders would land on top of one
        // another. Claimed names are remembered so that is an error naming both
        // rather than a book quietly missing a chapter.
        Map<String, Path> claimed = new LinkedHashMap<>();

        List<List<Manifest.Entry>> sections =
                List.of(manifest.frontMatter(), manifest.chapters(), manifest.appendices());

        for (int s = 0; s < sections.size(); s++) {
            // Appendices are their own run of top-level pages, not a tail of
            // whatever part the last chapter happened to sit in.
            if (s == 2 && !manifest.appendices().isEmpty()) {
                folder = root;
            }
            for (Manifest.Entry entry : sections.get(s)) {
                if (entry.isPart()) {
                    int position = rootPosition++;
                    folder = root.resolve(Slug.of(entry.partTitle()));
                    categories.add(
                            new BuildPlan.NewFile(
                                    folder.resolve("_category_.json"),
                                    category(entry.partTitle(), position)));
                    partPosition = 1;
                    continue;
                }
                int position = folder.equals(root) ? rootPosition++ : partPosition++;
                Path file = folder.resolve(pageName(entry.file()));
                if (!claim(claimed, file, entry.file())) {
                    continue;
                }
                placed.add(new Placed(entry, file, position));
            }
        }
    }

    /**
     * The generated page's filename: the source file's own, unchanged.
     *
     * <p>The only exception is the extension. Docusaurus collects {@code .md} and {@code .mdx} and
     * would not notice a {@code .markdown} at all, so that one spelling is normalised.
     */
    private static String pageName(Path source) {
        String name = source.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".markdown")
                ? name.substring(0, name.length() - ".markdown".length()) + ".md"
                : name;
    }

    /**
     * Records that a chapter has taken an output path, or reports the clash.
     *
     * <p>Compared without regard to case, because a name that differs only in case is two files on
     * Linux and one on Windows and macOS, and a book whose chapters survive on one machine and
     * silently merge on another is worse than a build that stops and says so.
     *
     * @return true when the name was free
     */
    private boolean claim(Map<String, Path> claimed, Path file, Path source) {
        String key = file.toString().toLowerCase(Locale.ROOT);
        Path first = claimed.putIfAbsent(key, source);
        if (first == null) {
            return true;
        }
        problems.add(
                new Problem(
                        Problem.Severity.ERROR,
                        source,
                        0,
                        0,
                        null,
                        "would be written to the same page as "
                                + first
                                + ", because a generated page keeps the name of its source file",
                        "rename one of the two files, or move one of them into a 'part:' of its"
                                + " own so they land in different folders"));
        return false;
    }

    /** A Docusaurus category descriptor, written by hand because it is four lines of JSON. */
    private static String category(String label, Integer position) {
        StringBuilder b = new StringBuilder("{\n  \"label\": ").append(json(label));
        if (position != null) {
            b.append(",\n  \"position\": ").append(position);
        }
        return b.append("\n}\n").toString();
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    // ------------------------------------------------------------------
    // Stale files
    // ------------------------------------------------------------------

    /** Files this converter wrote last time that it is not writing now. */
    private List<Path> stale(Path root, Set<String> generated) throws IOException {
        Path stamp = root.resolve(STAMP);
        if (!Files.isRegularFile(stamp)) {
            return List.of();
        }
        List<Path> gone = new ArrayList<>();
        for (String line : Files.readString(stamp, StandardCharsets.UTF_8).split("\n")) {
            String name = line.strip();
            if (name.isEmpty() || generated.contains(name)) {
                continue;
            }
            Path old = root.resolve(name).normalize();
            // Never follow a path out of the generated folder, however the
            // stamp file came to say so.
            if (old.startsWith(root) && Files.isRegularFile(old)) {
                gone.add(old);
            }
        }
        return gone;
    }

    private static String stampContents(Set<String> generated) {
        StringBuilder b =
                new StringBuilder(
                        "# Written by md2latex. Lists the files it generated here, so that a\n"
                                + "# later run can remove the ones that are no longer part of the"
                                + " book.\n"
                                + "# Delete this file and stale pages will simply be left behind.\n");
        for (String name : generated) {
            b.append(name).append('\n');
        }
        return b.toString();
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /** The source as the author wrote it, which is what a warning quotes back at them. */
    private static List<String> lines(String markua) {
        return List.of(markua.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));
    }

    /** Puts a heading on a document that had none, after the front matter. */
    private static String insertHeading(String markdown, String title) {
        int end = markdown.indexOf("\n---\n", 3);
        if (!markdown.startsWith("---\n") || end < 0) {
            return "# " + title + "\n\n" + markdown;
        }
        int at = end + "\n---\n".length();
        return markdown.substring(0, at) + "\n# " + title + "\n" + markdown.substring(at);
    }
}
