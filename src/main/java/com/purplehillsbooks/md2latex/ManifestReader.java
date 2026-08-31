package com.purplehillsbooks.md2latex;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

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
import java.util.stream.Stream;

/**
 * Reads and validates a {@code *.manifest} file.
 *
 * <p>Manifests are hand written, so validation is deliberately strict and
 * chatty: unknown keys are rejected rather than ignored (which catches typos
 * that would otherwise silently do nothing), and every missing source file is
 * reported at once rather than one failure per run.
 *
 * <p>Key names are matched case-insensitively with {@code _} and {@code -}
 * ignored, so {@code fontSize}, {@code font_size} and {@code font-size} are
 * all accepted.
 */
public final class ManifestReader {

    private static final Set<String> TOP_LEVEL_KEYS = normalizedSet(
            "title", "subtitle", "author", "date",
            "output", "document", "code", "preamble", "chapters");

    /**
     * Keys that used to be accepted, mapped to an explanation. Reporting these
     * specifically is far more use than a generic "unknown key" complaint.
     */
    private static final Map<String, String> REMOVED_KEYS = Map.of(
            normalize("sourceDir"),
            "'sourceDir' is no longer used. A manifest lives in the folder with its "
            + "Markdown, and every relative path is resolved against that folder. "
            + "Delete the line; if a chapter lives elsewhere, give it a relative "
            + "path in 'chapters' such as ../shared/intro.md");

    private static final Set<String> OUTPUT_KEYS = normalizedSet(
            "directory", "main", "chapters");

    private static final Set<String> DOCUMENT_KEYS = normalizedSet(
            "class", "fontSize", "paperSize", "geometry",
            "toc", "tocDepth", "numberDepth", "twoSide");

    private static final Set<String> ENTRY_KEYS = normalizedSet("file", "title", "part");

    private static final Set<String> DOCUMENT_CLASSES = Set.of("book", "report", "article");

    private ManifestReader() {
    }

    /**
     * Locates a manifest given a file or a directory.
     *
     * <p>A directory is searched for exactly one {@code *.manifest}; anything
     * else is an error, because guessing which book to build would be worse
     * than asking.
     */
    public static Path locate(Path input) throws ManifestException {
        if (!Files.exists(input)) {
            throw new ManifestException("manifest not found: " + input);
        }
        if (Files.isRegularFile(input)) {
            return input;
        }
        List<Path> found;
        try (Stream<Path> files = Files.list(input)) {
            found = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".manifest"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new ManifestException("cannot read directory " + input + ": " + e.getMessage(), e);
        }
        if (found.isEmpty()) {
            throw new ManifestException("no *.manifest file found in " + input);
        }
        if (found.size() > 1) {
            StringBuilder msg = new StringBuilder(
                    found.size() + " manifest files found in " + input
                            + "; name the one you want:");
            for (Path p : found) {
                msg.append("\n  ").append(p.getFileName());
            }
            throw new ManifestException(msg.toString());
        }
        return found.get(0);
    }

    public static Manifest read(Path manifestFile) throws ManifestException {
        Path file = manifestFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new ManifestException("manifest not found: " + file);
        }
        Path base = file.getParent();

        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ManifestException("cannot read " + file + ": " + e.getMessage(), e);
        }

        Object root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            root = new Yaml(new SafeConstructor(options)).load(text);
        } catch (YAMLException e) {
            throw new ManifestException(file.getFileName() + ": invalid YAML - " + e.getMessage(), e);
        }

        if (root == null) {
            throw new ManifestException(file.getFileName() + ": manifest is empty");
        }
        if (!(root instanceof Map<?, ?> rawMap)) {
            throw new ManifestException(file.getFileName()
                    + ": expected a mapping of settings at the top level, found "
                    + typeName(root));
        }

        String where = file.getFileName().toString();
        YamlMap top = new YamlMap(rawMap, where, "");
        top.rejectRemovedKeys();
        top.rejectUnknownKeys(TOP_LEVEL_KEYS);

        String title = top.requireString("title");
        String subtitle = top.optionalString("subtitle", null);
        String author = top.optionalString("author", null);
        String date = top.optionalString("date", null);

        Manifest.Output output = readOutput(top.optionalMap("output"), base);
        Manifest.Document document = readDocument(top.optionalMap("document"), where);
        CodeStyle codeStyle = readCodeStyle(top, where);
        List<String> preamble = top.optionalStringList("preamble");

        // Chapter paths resolve against the manifest's own directory.
        List<Manifest.Entry> entries = readEntries(top, base, where);

        return new Manifest(file, title, subtitle, author, date,
                output, document, codeStyle, preamble, entries);
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    /** Default output folder. Deliberately not "build", which Docusaurus claims. */
    private static final String DEFAULT_OUTPUT_DIR = "latex";

    private static Manifest.Output readOutput(YamlMap out, Path base) throws ManifestException {
        if (out == null) {
            return new Manifest.Output(
                    base.resolve(DEFAULT_OUTPUT_DIR).normalize(), "book.tex");
        }
        out.rejectUnknownKeys(OUTPUT_KEYS);
        Path directory = base.resolve(
                out.optionalString("directory", DEFAULT_OUTPUT_DIR)).normalize();
        String main = out.optionalString("main", "book.tex");
        if (!main.endsWith(".tex")) {
            main = main + ".tex";
        }
        return new Manifest.Output(directory, main);
    }

    private static Manifest.Document readDocument(YamlMap doc, String where)
            throws ManifestException {
        if (doc == null) {
            return new Manifest.Document("book", "11pt", "a4paper", "margin=1in",
                    true, 2, 2, false);
        }
        doc.rejectUnknownKeys(DOCUMENT_KEYS);
        String documentClass = doc.optionalString("class", "book").toLowerCase(Locale.ROOT);
        if (!DOCUMENT_CLASSES.contains(documentClass)) {
            throw new ManifestException(where + ": document.class must be one of "
                    + DOCUMENT_CLASSES + ", found '" + documentClass + "'");
        }
        return new Manifest.Document(
                documentClass,
                doc.optionalString("fontSize", "11pt"),
                doc.optionalString("paperSize", "a4paper"),
                doc.optionalString("geometry", "margin=1in"),
                doc.optionalBoolean("toc", true),
                doc.optionalInt("tocDepth", 2),
                doc.optionalInt("numberDepth", 2),
                doc.optionalBoolean("twoSide", false));
    }

    private static CodeStyle readCodeStyle(YamlMap top, String where) throws ManifestException {
        String code = top.optionalString("code", "listings");
        try {
            return CodeStyle.parse(code);
        } catch (IllegalArgumentException e) {
            throw new ManifestException(where + ": " + e.getMessage(), e);
        }
    }

    private static List<Manifest.Entry> readEntries(YamlMap top, Path sourceDir, String where)
            throws ManifestException {
        List<Object> chapterList = top.requireList("chapters");
        if (chapterList.isEmpty()) {
            throw new ManifestException(where + ": 'chapters' must list at least one file");
        }

        List<Manifest.Entry> entries = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (int i = 0; i < chapterList.size(); i++) {
            Object item = chapterList.get(i);
            String position = "chapters[" + i + "]";

            if (item instanceof String s) {
                addChapter(entries, missing, sourceDir, s, null, where, position);
            } else if (item instanceof Map<?, ?> m) {
                YamlMap entry = new YamlMap(m, where, position);
                entry.rejectUnknownKeys(ENTRY_KEYS);

                String part = entry.optionalString("part", null);
                String fileName = entry.optionalString("file", null);

                if (part != null && fileName != null) {
                    throw new ManifestException(where + ": " + position
                            + " sets both 'part' and 'file'; a part divider takes no source file");
                }
                if (part != null) {
                    entries.add(Manifest.Entry.part(part));
                } else if (fileName != null) {
                    addChapter(entries, missing, sourceDir, fileName,
                            entry.optionalString("title", null), where, position);
                } else {
                    throw new ManifestException(where + ": " + position
                            + " needs either a 'file' or a 'part' key");
                }
            } else {
                throw new ManifestException(where + ": " + position
                        + " must be a filename or a mapping, found " + typeName(item));
            }
        }

        if (!missing.isEmpty()) {
            StringBuilder msg = new StringBuilder(where + ": "
                    + missing.size() + " source file(s) listed in the manifest do not exist:");
            for (String m : missing) {
                msg.append("\n  ").append(m);
            }
            msg.append("\n(paths are resolved against sourceDir: ").append(sourceDir).append(')');
            throw new ManifestException(msg.toString());
        }
        if (entries.stream().allMatch(Manifest.Entry::isPart)) {
            throw new ManifestException(where + ": 'chapters' contains only part dividers "
                    + "and no source files");
        }
        return entries;
    }

    private static void addChapter(List<Manifest.Entry> entries,
                                   List<String> missing,
                                   Path sourceDir,
                                   String fileName,
                                   String titleOverride,
                                   String where,
                                   String position) throws ManifestException {
        if (fileName.isBlank()) {
            throw new ManifestException(where + ": " + position + " has an empty filename");
        }
        Path resolved = sourceDir.resolve(fileName).normalize();
        if (!Files.isRegularFile(resolved)) {
            missing.add(fileName + "  (" + position + ")");
            return;
        }
        entries.add(Manifest.Entry.newChapter(resolved, titleOverride));
    }

    // ------------------------------------------------------------------
    // YAML access with useful error messages
    // ------------------------------------------------------------------

    /**
     * A YAML mapping with typed accessors. Keys are normalized so that case and
     * {@code _}/{@code -} separators do not matter, while the original spelling
     * is retained for error messages.
     */
    private static final class YamlMap {

        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, String> originalNames = new LinkedHashMap<>();
        private final String where;
        private final String prefix;

        YamlMap(Map<?, ?> raw, String where, String context) throws ManifestException {
            this.where = where;
            this.prefix = context.isEmpty() ? "" : context + ".";
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String original = String.valueOf(e.getKey());
                String key = normalize(original);
                if (values.containsKey(key)) {
                    throw new ManifestException(where + ": duplicate key '" + original
                            + "' in " + (context.isEmpty() ? "the top level" : context));
                }
                values.put(key, e.getValue());
                originalNames.put(key, original);
            }
        }

        /** Reports keys that were valid in an earlier version, with guidance. */
        void rejectRemovedKeys() throws ManifestException {
            for (String key : values.keySet()) {
                String explanation = REMOVED_KEYS.get(key);
                if (explanation != null) {
                    throw new ManifestException(where + ": " + explanation);
                }
            }
        }

        void rejectUnknownKeys(Set<String> allowed) throws ManifestException {
            Set<String> unknown = new LinkedHashSet<>();
            for (String key : values.keySet()) {
                if (!allowed.contains(key)) {
                    unknown.add(originalNames.get(key));
                }
            }
            if (!unknown.isEmpty()) {
                throw new ManifestException(where + ": unknown key(s) "
                        + unknown + (prefix.isEmpty() ? " at the top level" : " in " + trimDot())
                        + ". Recognised keys here: " + allowed);
            }
        }

        String requireString(String key) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                throw new ManifestException(where + ": required key '" + prefix + key
                        + "' is missing");
            }
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                throw new ManifestException(where + ": '" + prefix + key + "' must not be empty");
            }
            return s;
        }

        String optionalString(String key, String fallback) {
            Object v = values.get(normalize(key));
            if (v == null) {
                return fallback;
            }
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? fallback : s;
        }

        boolean optionalBoolean(String key, boolean fallback) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                return fallback;
            }
            if (v instanceof Boolean b) {
                return b;
            }
            String s = String.valueOf(v).trim().toLowerCase(Locale.ROOT);
            return switch (s) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> throw new ManifestException(where + ": '" + prefix + key
                        + "' must be true or false, found '" + s + "'");
            };
        }

        int optionalInt(String key, int fallback) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                return fallback;
            }
            if (v instanceof Number n) {
                return n.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException e) {
                throw new ManifestException(where + ": '" + prefix + key
                        + "' must be a whole number, found '" + v + "'", e);
            }
        }

        YamlMap optionalMap(String key) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                return null;
            }
            if (!(v instanceof Map<?, ?> m)) {
                throw new ManifestException(where + ": '" + prefix + key
                        + "' must be a mapping, found " + typeName(v));
            }
            return new YamlMap(m, where, prefix + key);
        }

        List<Object> requireList(String key) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                throw new ManifestException(where + ": required key '" + prefix + key
                        + "' is missing");
            }
            if (!(v instanceof List<?> list)) {
                throw new ManifestException(where + ": '" + prefix + key
                        + "' must be a list, found " + typeName(v));
            }
            return new ArrayList<>(list);
        }

        List<String> optionalStringList(String key) throws ManifestException {
            Object v = values.get(normalize(key));
            if (v == null) {
                return List.of();
            }
            if (v instanceof String s) {
                return List.of(s);
            }
            if (!(v instanceof List<?> list)) {
                throw new ManifestException(where + ": '" + prefix + key
                        + "' must be a list of strings, found " + typeName(v));
            }
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }

        private String trimDot() {
            return prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        }
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private static Set<String> normalizedSet(String... keys) {
        Set<String> set = new LinkedHashSet<>();
        for (String k : keys) {
            set.add(normalize(k));
        }
        return set;
    }

    private static String typeName(Object o) {
        if (o == null) {
            return "nothing";
        }
        if (o instanceof Map) {
            return "a mapping";
        }
        if (o instanceof List) {
            return "a list";
        }
        return "a " + o.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }
}
