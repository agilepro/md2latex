package com.purplehillsbooks.md2latex;

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
 * Works out what every link and image in the book should point at once the Markdown has moved into
 * the site, and which files have to move with it.
 *
 * <p>Three kinds of destination need attention, and everything else is left exactly as written:
 *
 * <ul>
 *   <li><b>A link to another chapter.</b> The generated files are renamed and may sit in different
 *       folders from their sources, so {@code definitions.md} becomes whatever that chapter was
 *       called in the output, relative to the file doing the linking. Docusaurus resolves relative
 *       {@code .md} links itself, so nothing more is needed. This is the opposite of what the LaTeX
 *       target does, which keeps the link text and drops the destination.
 *   <li><b>An image, or any other file a chapter refers to.</b> The site cannot reach back into the
 *       Markua folder, so the file is copied into the output tree and the reference is repointed. A
 *       file that lives under the manifest's own folder keeps its relative shape - {@code
 *       images/tribe.png} stays {@code images/tribe.png} - and one from outside is gathered into
 *       {@code assets/}, because its path relative to the book means nothing here.
 *   <li><b>A destination that does not resolve.</b> Reported as a warning rather than an error: a
 *       broken image is worth knowing about, but unlike LaTeX a site still builds around one.
 * </ul>
 *
 * <p>One instance serves the whole book, so an image used by three chapters is copied once.
 */
final class DocusaurusAssets {

    /** Schemes and shapes that are somebody else's problem. */
    private static final Set<String> EXTERNAL_SCHEMES =
            Set.of("http://", "https://", "mailto:", "ftp://", "ftps://", "tel:", "data:", "//");

    /** Where files from outside the book's own folder are gathered. */
    private static final String STRAY_ASSETS = "assets";

    private final Path outputRoot;
    private final Path sourceFolder;
    private final boolean copyAssets;
    private final List<Problem> problems;

    /** Source Markdown file to the file generated from it. */
    private final Map<Path, Path> documents = new LinkedHashMap<>();

    /** Asset source to its chosen home under the output root, so each is copied once. */
    private final Map<Path, Path> assets = new LinkedHashMap<>();

    /** Names already taken in {@code assets/}, so two files called logo.png do not collide. */
    private final Set<String> strayNames = new LinkedHashSet<>();

    DocusaurusAssets(
            Path outputRoot, Path sourceFolder, boolean copyAssets, List<Problem> problems) {
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.sourceFolder = sourceFolder.toAbsolutePath().normalize();
        this.copyAssets = copyAssets;
        this.problems = problems;
    }

    /** Every chapter must be registered before any of them is converted, so links can resolve. */
    void registerDocument(Path sourceFile, Path outputFile) {
        documents.put(
                sourceFile.toAbsolutePath().normalize(), outputFile.toAbsolutePath().normalize());
    }

    /**
     * A resolver bound to one chapter. Destinations are relative to the file that wrote them and
     * are rewritten relative to the file being generated, which are two different places.
     *
     * @param sourceLines the chapter as the author wrote it, used only to locate a warning
     */
    DocusaurusSink.LinkResolver forChapter(
            Path sourceFile, Path outputFile, List<String> sourceLines) {
        Path sourceDir = sourceFile.toAbsolutePath().normalize().getParent();
        Path outputDir = outputFile.toAbsolutePath().normalize().getParent();
        return (destination, image) ->
                resolve(destination, image, sourceFile, sourceDir, outputDir, sourceLines);
    }

    // ------------------------------------------------------------------

    private String resolve(
            String destination,
            boolean image,
            Path sourceFile,
            Path sourceDir,
            Path outputDir,
            List<String> sourceLines) {

        if (destination == null || destination.isBlank()) {
            return destination;
        }
        // A destination in angle brackets is quoted, not different; unwrap it,
        // rewrite what is inside, and put the brackets back.
        if (destination.startsWith("<") && destination.endsWith(">")) {
            String inner =
                    resolve(
                            destination.substring(1, destination.length() - 1),
                            image,
                            sourceFile,
                            sourceDir,
                            outputDir,
                            sourceLines);
            return "<" + inner + ">";
        }
        if (isExternal(destination) || destination.startsWith("#") || destination.startsWith("/")) {
            return destination;
        }

        // A fragment identifies a heading within the target and travels with it.
        int hash = destination.indexOf('#');
        String path = hash < 0 ? destination : destination.substring(0, hash);
        String fragment = hash < 0 ? "" : destination.substring(hash);
        if (path.isEmpty()) {
            return destination;
        }

        Path resolved;
        try {
            resolved = sourceDir.resolve(LatexVisitor.percentDecode(path)).normalize();
        } catch (RuntimeException e) {
            return destination;
        }

        return isMarkdown(path)
                ? chapterLink(destination, resolved, outputDir, fragment, sourceFile, sourceLines)
                : assetLink(
                        destination, resolved, outputDir, fragment, image, sourceFile, sourceLines);
    }

    /** A link to another chapter of the same book, repointed at the file generated from it. */
    private String chapterLink(
            String destination,
            Path resolved,
            Path outputDir,
            String fragment,
            Path sourceFile,
            List<String> sourceLines) {

        Path target = documents.get(resolved);
        if (target == null) {
            warn(
                    sourceFile,
                    sourceLines,
                    destination,
                    "link to '" + destination + "' points at a file that is not in this book",
                    "add it to the manifest, or make the link absolute so Docusaurus resolves it");
            return destination;
        }
        return encode(relative(outputDir, target)) + fragment;
    }

    /** An image or other supporting file, copied into the site and repointed. */
    private String assetLink(
            String destination,
            Path resolved,
            Path outputDir,
            String fragment,
            boolean image,
            Path sourceFile,
            List<String> sourceLines) {

        if (!Files.isRegularFile(resolved)) {
            warn(
                    sourceFile,
                    sourceLines,
                    destination,
                    (image ? "image" : "linked file") + " not found: " + resolved,
                    "the path is resolved relative to " + sourceFile.getFileName());
            return destination;
        }
        if (!copyAssets) {
            return destination;
        }
        Path target = assets.computeIfAbsent(resolved, this::homeFor);
        return encode(relative(outputDir, target)) + fragment;
    }

    /**
     * Where a file belongs under the output root. Something inside the book's own folder keeps the
     * shape the author gave it; anything else has no meaningful shape here and is gathered up.
     */
    private Path homeFor(Path asset) {
        if (asset.startsWith(sourceFolder)) {
            return outputRoot.resolve(sourceFolder.relativize(asset));
        }
        String name = asset.getFileName().toString();
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; !strayNames.add(candidate.toLowerCase(Locale.ROOT)); n++) {
            candidate = stem + "-" + n + extension;
        }
        return outputRoot.resolve(STRAY_ASSETS).resolve(candidate);
    }

    private void warn(
            Path sourceFile,
            List<String> sourceLines,
            String destination,
            String message,
            String hint) {
        int line = lineOf(sourceLines, destination);
        problems.add(
                new Problem(
                        Problem.Severity.WARNING,
                        sourceFile,
                        line,
                        0,
                        line > 0 ? sourceLines.get(line - 1) : null,
                        message,
                        hint));
    }

    /**
     * The line a destination was written on. The rewrite is line oriented and has no node to ask,
     * so the original is searched for the text instead - which is exact for anything but the same
     * URL written twice, where the first occurrence is close enough to find it by.
     */
    private static int lineOf(List<String> lines, String destination) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(destination)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String relative(Path from, Path to) {
        try {
            String r = from.relativize(to).toString().replace('\\', '/');
            // A sibling needs the explicit ./ or Docusaurus reads it as a doc id
            // rather than as a path relative to this file.
            return r.startsWith(".") ? r : "./" + r;
        } catch (IllegalArgumentException e) {
            return to.toString().replace('\\', '/');
        }
    }

    /** Puts back the escaping that a path needs to survive as a Markdown destination. */
    private static String encode(String path) {
        return path.replace("%", "%25").replace(" ", "%20").replace("(", "%28").replace(")", "%29");
    }

    private static boolean isMarkdown(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".md") || p.endsWith(".markdown") || p.endsWith(".mdx");
    }

    private static boolean isExternal(String destination) {
        String d = destination.toLowerCase(Locale.ROOT);
        for (String scheme : EXTERNAL_SCHEMES) {
            if (d.startsWith(scheme)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The files that have to travel with the Markdown, in the order they were first referred to.
     */
    List<Map.Entry<Path, Path>> ordered() {
        return new ArrayList<>(assets.entrySet());
    }
}
