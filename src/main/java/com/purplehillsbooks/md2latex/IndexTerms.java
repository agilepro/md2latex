package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The book-wide set of index terms, gathered from the {@code indexTerms:} key in every chapter's
 * front matter.
 *
 * <p>Terms are pooled across the whole book: where a term is declared does not matter, every
 * chapter is searched for every term. Matching is whole-word and case-insensitive, so {@code tribe}
 * matches {@code Tribe} but not {@code tribes} or {@code redistribute}.
 *
 * <p>This object is also the single source of truth for whether indexing is switched on at all. If
 * it is empty, no {@code makeidx} package and no {@code \printindex} are emitted, and the book
 * compiles in one pdflatex pass exactly as before.
 */
public final class IndexTerms {

    /**
     * Characters that cannot appear in a term.
     *
     * <p>{@code ! @ | "} are makeindex's own metacharacters (sub-entry, sort key, encapsulation,
     * quote). They cannot be reliably escaped: makeindex escapes its quote char as {@code \"},
     * which LaTeX reads as an umlaut accent, and hyperref independently splits on {@code |} to
     * append {@code |hyperpage} without understanding makeindex quoting. Rejecting them outright
     * removes the whole class of problem, at no real cost for author-chosen keywords.
     */
    private static final String RESERVED = "\"|@!\\{}%";

    /** Runs of any Unicode space, collapsed so NBSP cannot defeat a phrase. */
    private static final Pattern SPACES = Pattern.compile("[\\p{Zs}\\s]+");

    /** One index term, and where it was declared, for locating warnings. */
    public record Term(
            String canonical, String latex, Pattern pattern, Path declaredIn, int declaredLine) {}

    private static final IndexTerms NONE = new IndexTerms(List.of());

    private final List<Term> terms;
    private final Set<String> used = new HashSet<>();

    private IndexTerms(List<Term> terms) {
        this.terms = List.copyOf(terms);
    }

    public static IndexTerms none() {
        return NONE;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public List<Term> all() {
        return terms;
    }

    /**
     * Terms whose pattern matches anywhere in {@code text}, in declaration order. Does not record
     * usage; call {@link #markUsed} for that.
     */
    public List<Term> matching(String text) {
        if (terms.isEmpty() || text == null || text.isBlank()) {
            return List.of();
        }
        String haystack = normalize(text);
        List<Term> hits = new ArrayList<>();
        for (Term t : terms) {
            if (t.pattern().matcher(haystack).find()) {
                hits.add(t);
            }
        }
        return hits;
    }

    public void markUsed(Term term) {
        used.add(key(term.canonical()));
    }

    /** Terms that never matched anywhere in the book; almost always typos. */
    public List<Term> unused() {
        return terms.stream().filter(t -> !used.contains(key(t.canonical()))).toList();
    }

    // ------------------------------------------------------------------
    // Collection
    // ------------------------------------------------------------------

    /**
     * Reads the {@code indexTerms:} front matter key from every chapter and builds the pooled term
     * set.
     *
     * @param chapters chapter entries in book order, so the surviving spelling of a term is
     *     deterministic
     * @param sources file contents, already read once by the caller
     * @param problems collector for validation faults
     */
    public static IndexTerms collect(
            List<Manifest.Entry> chapters, Map<Path, String> sources, List<Problem> problems) {
        Map<String, Term> byKey = new LinkedHashMap<>();

        for (Manifest.Entry entry : chapters) {
            Path file = entry.file();
            String markdown = sources.get(file);
            if (markdown == null) {
                continue;
            }
            MarkdownLoader.Result parsed = MarkdownLoader.process(markdown);
            if (!parsed.frontMatter().containsKey("indexTerms")) {
                continue;
            }
            String raw = parsed.frontMatter().get("indexTerms");
            int line = declarationLine(parsed);
            String sourceLine = parsed.sourceLine(line);

            if (raw == null || raw.isBlank()) {
                if (looksLikeYamlList(parsed, line)) {
                    problems.add(
                            new Problem(
                                    Problem.Severity.ERROR,
                                    file,
                                    line,
                                    0,
                                    sourceLine,
                                    "'index' must be a comma-separated list on one line, "
                                            + "not a YAML list",
                                    "write it as: indexTerms: tribe, eudaimonia, moral realism"));
                } else {
                    problems.add(
                            new Problem(
                                    Problem.Severity.WARNING,
                                    file,
                                    line,
                                    0,
                                    sourceLine,
                                    "'index' is present but empty",
                                    "list the terms, or remove the key"));
                }
                continue;
            }

            for (String piece : raw.split(",")) {
                String term = normalize(piece);
                if (term.isEmpty()) {
                    continue;
                }
                if (!validate(term, file, line, sourceLine, problems)) {
                    continue;
                }
                String key = key(term);
                Term existing = byKey.get(key);
                if (existing != null) {
                    if (!existing.canonical().equals(term)) {
                        problems.add(
                                new Problem(
                                        Problem.Severity.WARNING,
                                        file,
                                        line,
                                        0,
                                        sourceLine,
                                        "index term '"
                                                + term
                                                + "' was already declared as '"
                                                + existing.canonical()
                                                + "' in "
                                                + existing.declaredIn().getFileName()
                                                + "; the first spelling is used",
                                        "use one spelling consistently"));
                    }
                    continue;
                }
                byKey.put(key, new Term(term, LatexEscaper.text(term), compile(term), file, line));
            }
        }
        return byKey.isEmpty() ? NONE : new IndexTerms(List.copyOf(byKey.values()));
    }

    private static boolean validate(
            String term, Path file, int line, String sourceLine, List<Problem> problems) {
        for (int i = 0; i < term.length(); i++) {
            char c = term.charAt(i);
            if (RESERVED.indexOf(c) >= 0) {
                problems.add(
                        new Problem(
                                Problem.Severity.ERROR,
                                file,
                                line,
                                0,
                                sourceLine,
                                "index term '"
                                        + term
                                        + "' contains '"
                                        + c
                                        + "', which makeindex reserves",
                                "index terms may not contain any of: "
                                        + RESERVED
                                        + " . For a sub-entry, place the marker by hand with "
                                        + "<!-- latex: \\index{parent!child} --> in the text"));
                return false;
            }
        }
        int bad = LatexSafety.firstUnsupportedChar(term);
        if (bad >= 0) {
            int c = term.codePointAt(bad);
            problems.add(
                    new Problem(
                            Problem.Severity.ERROR,
                            file,
                            line,
                            0,
                            sourceLine,
                            "index term '"
                                    + term
                                    + "' contains "
                                    + LatexSafety.describe(c)
                                    + ", which pdflatex cannot typeset",
                            LatexSafety.hintFor(c)));
            return false;
        }
        if (!isAscii(term)) {
            problems.add(
                    new Problem(
                            Problem.Severity.WARNING,
                            file,
                            line,
                            0,
                            sourceLine,
                            "index term '"
                                    + term
                                    + "' contains non-ASCII characters, so "
                                    + "makeindex will sort it under symbols rather than under its letter",
                            "an ASCII spelling sorts correctly; the accented form still "
                                    + "matches the text either way"));
        }
        return true;
    }

    /** 1-based line of the {@code indexTerms:} key in the original file, or 0. */
    private static int declarationLine(MarkdownLoader.Result parsed) {
        List<String> lines = parsed.originalLines();
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.startsWith("indexTerms") && t.substring(5).stripLeading().startsWith(":")) {
                return i + 1;
            }
            // Front matter ends at the closing delimiter.
            if (i > 0 && (t.equals("---") || t.equals("..."))) {
                break;
            }
        }
        return 0;
    }

    /**
     * True when the line after {@code indexTerms:} starts a YAML sequence. The front matter parser
     * is flat, so that form silently yields no terms; the manifest's own {@code preamble:} key does
     * accept it, which makes the mistake an easy one.
     */
    private static boolean looksLikeYamlList(MarkdownLoader.Result parsed, int line) {
        String next = parsed.sourceLine(line + 1);
        return next != null && next.stripLeading().startsWith("- ");
    }

    private static Pattern compile(String term) {
        // Lookarounds rather than \b so multi-word phrases and terms with
        // punctuation behave. UNICODE_CHARACTER_CLASS makes \w cover accented
        // letters, without which "Kant" would match inside "Kante".
        return Pattern.compile(
                "(?<!\\w)" + Pattern.quote(term) + "(?!\\w)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    }

    /** Collapses every run of whitespace, including NBSP, to one plain space. */
    static String normalize(String s) {
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    private static String key(String term) {
        return term.toLowerCase(Locale.ROOT);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
