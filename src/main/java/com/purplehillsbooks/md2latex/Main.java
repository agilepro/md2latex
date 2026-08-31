package com.purplehillsbooks.md2latex;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import com.purplehillsbooks.exception.CommonException;

/**
 * Command line entry point.
 *
 * <p>Two jobs: build the book described by a manifest, or scaffold a starter
 * manifest for a folder of Markdown.
 */
public final class Main {

    private Main() {
    }

    /**
     * Process entry point. The only reason {@link System#exit} appears here at
     * all is that {@code main} returns void, so it is the sole way to hand a
     * non-zero status back to the shell. The success path simply returns, which
     * already yields status 0.
     */
    public static void main(String[] args) {
        int status = run(args);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Testable entry point: returns the exit code rather than setting it, so
     * the whole command line can be exercised without killing the JVM.
     *
     * @return 0 on success, 1 on an I/O or manifest error, 2 on a usage error
     */
    static int run(String[] args) {
        // Checked before parsing, which would otherwise reject the empty
        // argument list as a missing manifest.
        if (args.length == 0) {
            System.out.println(Options.USAGE);
            return 0;
        }
        Options opts;
        try {
            opts = Options.parse(args);
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.println("Run 'md2latex --help' for usage.");
            return 2;
        }
        if (opts.help) {
            System.out.println(Options.USAGE);
            return 0;
        }

        try {
            return opts.initFrom != null ? scaffold(opts) : build(opts);
        } catch (Exception e) {
            CommonException.traceException(System.out, e, "md2latex: error: ");
            return 1;
        }
    }

    // ------------------------------------------------------------------

    private static int build(Options opts)
            throws Exception {
        Path manifestFile = ManifestReader.locate(opts.manifest);
        Manifest manifest = ManifestReader.read(manifestFile);

        BookBuilder.Result result = new BookBuilder(manifest).build();

        if (opts.verbose) {
            System.out.println("Manifest: " + manifestFile);
            System.out.println("Sources:  " + manifest.sourceFolder());
            System.out.println();
            for (Path chapter : result.chapterFiles()) {
                System.out.println("  " + relative(manifest.output().directory(), chapter));
            }
            System.out.println();
        }

        if (!result.warnings().isEmpty()) {
            System.err.println(result.warnings().size() + " warning(s):");
            for (Problem w : result.warnings()) {
                System.err.println();
                System.err.println(w.format());
            }
            System.err.println();
        }

        System.out.println("Wrote " + result.chapterFiles().size()
                + " chapter file(s) and 1 master document to "
                + manifest.output().directory());

        String main = manifest.output().mainFile();
        String jobName = main.endsWith(".tex")
                ? main.substring(0, main.length() - 4)
                : main;
        System.out.print("Build with:  cd \"" + manifest.output().directory()
                + "\" && pdflatex " + main);
        if (result.hasIndex()) {
            // An index needs makeindex between two pdflatex runs; one pass
            // alone leaves the index empty.
            System.out.print(" && makeindex " + jobName + " && pdflatex " + main);
        }
        System.out.println();
        return 0;
    }

    private static int scaffold(Options opts) throws IOException {
        int count = ManifestScaffold.write(opts.initFrom, opts.initTo, opts.initTitle);
        System.out.println("Wrote " + opts.initTo + " listing " + count + " chapter(s).");
        System.out.println("Review the order, then build with:  md2latex " + opts.initTo);
        return 0;
    }

    private static String relative(Path base, Path file) {
        try {
            return base.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.toString();
        }
    }
}
