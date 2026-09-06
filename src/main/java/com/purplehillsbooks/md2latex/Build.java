package com.purplehillsbooks.md2latex;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs a manifest through whichever targets were asked for.
 *
 * <p>The order matters, and it is the same order it has always been: convert everything, then look
 * at the problems, then write. Both targets read the same Markdown and share one problem list, so a
 * book that will not compile stops the site being written too. Two outputs that disagree about
 * whether the source was sound would be worse than either failing on its own.
 */
public final class Build {

    /** What the run produced, for the caller to report. */
    public record Result(
            Set<Target> targets,
            Path latexMain,
            List<Path> latexChapters,
            boolean hasIndex,
            List<Path> docusaurusFiles,
            int assetCount,
            List<Problem> warnings,
            List<Path> written) {}

    private Build() {}

    /**
     * @param wanted the targets to build, or null for every one the manifest describes
     * @throws ConversionException when the Markdown would produce output that does not work, in
     *     which case nothing at all has been written
     */
    public static Result run(Manifest manifest, Set<Target> wanted) throws Exception {
        Set<Target> targets = resolve(manifest, wanted);

        // Read every chapter once. Both targets want the same bytes, and the
        // index needs the whole book before the first chapter is converted.
        Map<Path, String> sources = new LinkedHashMap<>();
        for (Manifest.Entry entry : manifest.sourceEntries()) {
            sources.put(entry.file(), Files.readString(entry.file(), StandardCharsets.UTF_8));
        }

        List<Problem> problems = new ArrayList<>();
        BuildPlan writes = new BuildPlan();

        BookBuilder.Plan latex = null;
        if (targets.contains(Target.LATEX)) {
            latex = new BookBuilder(manifest, problems).plan(sources);
            writes.addAll(latex.writes());
        }

        DocusaurusBuilder.Plan docs = null;
        if (targets.contains(Target.DOCUSAURUS)) {
            docs = new DocusaurusBuilder(manifest, problems).plan(sources);
            writes.addAll(docs.writes());
        }

        // Nothing is written until every target has converted cleanly.
        if (problems.stream().anyMatch(Problem::isError)) {
            throw new ConversionException(problems);
        }

        List<Path> written = writes.write();

        return new Result(
                targets,
                latex == null ? null : latex.mainFile(),
                latex == null ? List.of() : latex.chapterFiles(),
                latex != null && latex.hasIndex(),
                docs == null ? List.of() : docs.documents(),
                docs == null ? 0 : docs.assetCount(),
                List.copyOf(problems),
                written);
    }

    /**
     * Which targets to run: the ones asked for, or everything the manifest describes.
     *
     * <p>Asking for a target the manifest says nothing about is a mistake worth naming, because the
     * alternative is a run that reports success having produced nothing.
     */
    private static Set<Target> resolve(Manifest manifest, Set<Target> wanted)
            throws ManifestException {
        Set<Target> available = EnumSet.noneOf(Target.class);
        if (manifest.hasLatex()) {
            available.add(Target.LATEX);
        }
        if (manifest.hasDocusaurus()) {
            available.add(Target.DOCUSAURUS);
        }
        if (wanted == null || wanted.isEmpty()) {
            return available;
        }
        for (Target t : wanted) {
            if (!available.contains(t)) {
                throw new ManifestException(
                        manifest.manifestFile().getFileName()
                                + ": --target "
                                + t
                                + " was asked for, but the manifest has no '"
                                + t
                                + ":' section saying where it should go");
            }
        }
        return EnumSet.copyOf(wanted);
    }
}
