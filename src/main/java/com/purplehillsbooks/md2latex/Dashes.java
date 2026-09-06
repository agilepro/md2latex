package com.purplehillsbooks.md2latex;

/**
 * Reads a run of hyphens for the dash it stands for, and spells that dash the way the target wants
 * it.
 *
 * <p>The reading is the same either way and is the interesting half. An author writing Markdown has
 * only the one key, so a run of hyphens has to be interpreted:
 *
 * <ul>
 *   <li>A single hyphen is a hyphen - {@code well-known}, {@code twenty-one} - and is left exactly
 *       as written. The one exception is a numeric range, {@code pages 3-5}, where the dash stands
 *       for "to" and an en dash is what a typesetter would set; that needs digits on both sides, so
 *       a part number like {@code A-1} is untouched.
 *   <li>Two hyphens are Markua's way of writing an em dash, and become three.
 *   <li>Three are already an em dash and are left alone.
 * </ul>
 *
 * <p>Only the spelling differs between targets. TeX has no en or em dash character and counts
 * hyphens instead - two for an en dash, three for an em dash - while a browser wants the Unicode
 * character itself. Both are covered by {@link Style}.
 *
 * <p>The Unicode dashes an author's editor may have produced instead need no reading at all. On the
 * web they are already right; for LaTeX {@code —} and {@code –} are translated by {@link
 * CharacterMap} rather than here.
 *
 * <p>Runs are found and rewritten in one pass, so nothing is read twice: {@code 3-5} becomes an en
 * dash and stops there rather than being picked up again as an em dash.
 *
 * <p>This applies to prose only. Code spans and fenced blocks keep the hyphens they were written
 * with, which is what a command line like {@code --verbose} needs.
 */
public final class Dashes {

    /**
     * How a target spells the two dashes that a run of hyphens can stand for.
     *
     * @param en the en dash, as in {@code pages 3-5}
     * @param em the em dash
     */
    public record Style(String en, String em) {

        /** Hyphen runs, which is the only spelling TeX understands. */
        public static final Style TEX = new Style("--", "---");

        /** The characters themselves, which is what a browser wants. */
        public static final Style UNICODE = new Style("–", "—");
    }

    private Dashes() {}

    /** Rewrites every run of hyphens in {@code text} to the dash it stands for, spelled for TeX. */
    public static String convert(String text) {
        return convert(text, Style.TEX);
    }

    /** Rewrites every run of hyphens in {@code text} to the dash it stands for. */
    public static String convert(String text, Style style) {
        if (text == null || text.indexOf('-') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '-') {
                out.append(c);
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && text.charAt(i) == '-') {
                i++;
            }
            out.append(dashFor(text, start, i, style));
        }
        return out.toString();
    }

    /** The spelling for the run of hyphens between {@code start} and {@code end}. */
    private static String dashFor(String text, int start, int end, Style style) {
        int length = end - start;
        if (length == 1) {
            // A range only when both sides are numeric; anything else is a hyphen.
            return digitAt(text, start - 1) && digitAt(text, end) ? style.en() : "-";
        }
        if (length == 2) {
            return style.em();
        }
        // Three is already an em dash, and a longer rule is the author's business.
        return length == 3 ? style.em() : "-".repeat(length);
    }

    private static boolean digitAt(String text, int index) {
        return index >= 0 && index < text.length() && Character.isDigit(text.charAt(index));
    }
}
