package com.purplehillsbooks.md2latex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything a build intends to put on disk, gathered before any of it happens.
 *
 * <p>A conversion that fails must not leave a half-written or stale book behind, and once there are
 * two targets it must not leave a fresh site beside a book that would not compile either. So no
 * target writes as it goes: each converts entirely into memory, adds what it wants here, and the
 * whole lot is written only after every target has converted without error.
 */
public final class BuildPlan {

    /** A file to be created, with its complete contents. */
    public record NewFile(Path target, String contents) {}

    /** A file to be copied verbatim - an image, or anything else a chapter refers to. */
    public record Copy(Path source, Path target) {}

    private final List<NewFile> files = new ArrayList<>();
    private final List<Copy> copies = new ArrayList<>();
    private final List<Path> obsolete = new ArrayList<>();
    private final List<Path> pruneUnder = new ArrayList<>();

    public void add(Path target, String contents) {
        files.add(new NewFile(target, contents));
    }

    public void copy(Path source, Path target) {
        copies.add(new Copy(source, target));
    }

    /** A file left behind by an earlier run that this one no longer generates. */
    public void removeStale(Path target) {
        obsolete.add(target);
    }

    /**
     * Asks for empty directories under {@code root} to be tidied away once everything else is done.
     * Removing the last page from a folder should not leave the folder behind.
     */
    public void pruneEmptyDirectoriesUnder(Path root) {
        pruneUnder.add(root);
    }

    /** Folds another target's plan into this one, so that one write covers both. */
    public void addAll(BuildPlan other) {
        files.addAll(other.files);
        copies.addAll(other.copies);
        obsolete.addAll(other.obsolete);
        pruneUnder.addAll(other.pruneUnder);
    }

    /**
     * Carries out the plan.
     *
     * <p>Stale files go first, so that a file which has moved is removed before it is written back
     * in its new place rather than after.
     *
     * @return every path written, in the order it was added
     */
    public List<Path> write() throws IOException {
        for (Path stale : obsolete) {
            Files.deleteIfExists(stale);
        }
        List<Path> written = new ArrayList<>(files.size() + copies.size());
        for (NewFile f : files) {
            Files.createDirectories(f.target().getParent());
            Files.writeString(f.target(), f.contents(), StandardCharsets.UTF_8);
            written.add(f.target());
        }
        for (Copy c : copies) {
            Files.createDirectories(c.target().getParent());
            Files.copy(c.source(), c.target(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            written.add(c.target());
        }
        for (Path root : pruneUnder) {
            prune(root);
        }
        return written;
    }

    /**
     * Removes directories under {@code root} that nothing is left in, deepest first so that a
     * folder emptied only by the removal of an empty child goes too. The root itself always stays.
     */
    private static void prune(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        List<Path> directories = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isDirectory).filter(p -> !p.equals(root)).forEach(directories::add);
        }
        // Deepest first: sorting by path length is enough, since every one of
        // these shares the same root.
        directories.sort((a, b) -> b.toString().length() - a.toString().length());
        for (Path directory : directories) {
            try (var entries = Files.list(directory)) {
                if (entries.findAny().isEmpty()) {
                    Files.deleteIfExists(directory);
                }
            }
        }
    }
}
