package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts one Markua document into the Markdown a Docusaurus site reads.
 *
 * <p>The body is done by {@link MarkdownLoader} driving a {@link DocusaurusSink}, which is where
 * all the translation lives. What is left for this class is the front matter, and it is more than
 * it looks:
 *
 * <ul>
 *   <li>Keys the author wrote are kept <em>verbatim</em>, as the lines they were written on, rather
 *       than being parsed and printed again. A site's front matter holds things the flat parser
 *       here does not model - a list of tags, a nested sidebar entry - and re-emitting those from a
 *       string map would quietly turn them into strings.
 *   <li>{@code indexTerms} is dropped, because it means nothing to a website.
 *   <li>{@code sidebar_position} is replaced. The manifest decides the order of the book, and a
 *       position left over from before it did would fight with it.
 *   <li>{@code format} is set so that Docusaurus reads the file as CommonMark instead of MDX. This
 *       is the single most useful thing the converter does for the site build: prose that is
 *       entirely ordinary in a book - a stray brace, a less-than sign between two words - is an
 *       expression to MDX and fails the build with an error pointing at the wrong thing.
 * </ul>
 */
public final class Md2Docusaurus {

    /** An ATX heading, which is the only form a title override can be applied to. */
    private static final Pattern HEADING =
            Pattern.compile("^(\\s{0,3}#{1,6})(\\s+)(.*?)\\s*#*\\s*$");

    /** Fenced code delimiter, so that a # inside a code block is not read as a heading. */
    private static final Pattern CODE_FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})(.*)$");

    /** Front matter keys this converter owns, whatever the source said. */
    private static final Set<String> DROPPED_KEYS = Set.of("indexterms", "sidebar_position");

    /**
     * The finished file, the reading of its source, and whether a requested title override actually
     * landed on a heading.
     */
    public record Converted(String markdown, MarkdownLoader.Result source, boolean titleApplied) {}

    private final Manifest.Docusaurus settings;

    public Md2Docusaurus(Manifest.Docusaurus settings) {
        this.settings = settings;
    }

    /**
     * @param titleOverride replaces the text of the first level-1 heading and becomes the front
     *     matter title, or null to keep whatever the Markdown says
     * @param sidebarPosition this file's place among its siblings, from the manifest
     * @param links how link and image destinations are repointed for their new home
     */
    public Converted convert(
            String markdown,
            Path sourceFile,
            String titleOverride,
            int sidebarPosition,
            DocusaurusSink.LinkResolver links) {

        MarkdownLoader.Result source = MarkdownLoader.process(markdown, new DocusaurusSink(links));

        List<String> body = new ArrayList<>(List.of(source.body().split("\n", -1)));
        boolean titleApplied = titleOverride != null && retitle(body, titleOverride);

        StringBuilder out = new StringBuilder();
        out.append("---\n");
        writeFrontMatter(out, source, titleOverride, sidebarPosition);
        out.append("---\n\n");
        out.append(tidy(body));
        return new Converted(out.toString(), source, titleApplied);
    }

    // ------------------------------------------------------------------
    // Front matter
    // ------------------------------------------------------------------

    private void writeFrontMatter(
            StringBuilder out,
            MarkdownLoader.Result source,
            String titleOverride,
            int sidebarPosition) {

        Set<String> owned = new LinkedHashSet<>(DROPPED_KEYS);
        if (titleOverride != null) {
            out.append("title: ").append(yaml(titleOverride)).append('\n');
            owned.add("title");
        }
        out.append("sidebar_position: ").append(sidebarPosition).append('\n');
        if (settings.writesFormat()) {
            out.append("format: ").append(settings.format()).append('\n');
            owned.add("format");
        }
        for (String line : authorsFrontMatter(source, owned)) {
            out.append(line).append('\n');
        }
    }

    /**
     * The author's own front matter lines, minus the keys this converter sets itself.
     *
     * <p>A dropped key takes its continuation lines with it, so removing a {@code indexTerms} that
     * somebody wrote as a block does not leave its values behind as stray YAML.
     */
    private static List<String> authorsFrontMatter(
            MarkdownLoader.Result source, Set<String> owned) {
        List<String> all = source.originalLines();
        List<String> kept = new ArrayList<>();
        if (all.isEmpty() || !all.get(0).trim().equals("---")) {
            return kept;
        }
        boolean dropping = false;
        for (int i = 1; i < all.size(); i++) {
            String line = all.get(i);
            if (line.trim().equals("---")) {
                break;
            }
            boolean continuation =
                    !line.isBlank() && Character.isWhitespace(line.charAt(0))
                            || line.stripLeading().startsWith("- ");
            if (continuation) {
                if (!dropping) {
                    kept.add(line);
                }
                continue;
            }
            int colon = line.indexOf(':');
            String key = colon <= 0 ? "" : line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            dropping = owned.contains(key);
            if (!dropping) {
                kept.add(line);
            }
        }
        return kept;
    }

    /** Quotes a YAML scalar defensively; a book title routinely holds a colon. */
    private static String yaml(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ------------------------------------------------------------------
    // Body
    // ------------------------------------------------------------------

    /**
     * Replaces the text of the first level-1 heading, which is what a manifest {@code title:}
     * overrides.
     *
     * @return false when there was no such heading, which the caller reports and repairs
     */
    private static boolean retitle(List<String> body, String title) {
        boolean inCode = false;
        String fence = null;
        for (int i = 0; i < body.size(); i++) {
            Matcher f = CODE_FENCE.matcher(body.get(i));
            if (f.matches()) {
                if (!inCode) {
                    inCode = true;
                    fence = f.group(1).substring(0, 1);
                } else if (f.group(1).startsWith(fence)) {
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                continue;
            }
            Matcher h = HEADING.matcher(body.get(i));
            if (h.matches() && h.group(1).strip().length() == 1) {
                body.set(i, h.group(1) + h.group(2) + title);
                return true;
            }
        }
        return false;
    }

    /**
     * Squeezes the blank lines that block rewriting leaves behind.
     *
     * <p>A blurb is opened and closed with a blank line on either side so that whatever surrounded
     * it still parses, which routinely puts two or three together. Markdown does not care, but the
     * generated file is meant to be readable next to its source. Blank lines inside fenced code are
     * left alone, being content rather than spacing.
     */
    private static String tidy(List<String> lines) {
        StringBuilder out = new StringBuilder();
        boolean inCode = false;
        String fence = null;
        int blanks = 0;
        for (String line : lines) {
            Matcher f = CODE_FENCE.matcher(line);
            if (f.matches()) {
                if (!inCode) {
                    inCode = true;
                    fence = f.group(1).substring(0, 1);
                } else if (f.group(1).startsWith(fence)) {
                    inCode = false;
                }
            }
            if (!inCode && line.isBlank()) {
                blanks++;
                continue;
            }
            if (blanks > 0 && !out.isEmpty()) {
                out.append('\n');
            }
            blanks = 0;
            out.append(line).append('\n');
        }
        return out.toString().stripLeading();
    }
}
