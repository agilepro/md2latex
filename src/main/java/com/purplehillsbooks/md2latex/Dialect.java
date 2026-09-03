package com.purplehillsbooks.md2latex;

import java.util.Locale;

/**
 * Which non-CommonMark extensions the preprocessor should recognise.
 *
 * <p>The distinction matters because Markua claims syntax that is ordinary prose in plain Markdown:
 * a line beginning {@code A>} is just text, and {@code {i: "tribe"}} is just a word in braces.
 * Turning those meanings on unconditionally would silently rewrite existing documents, so it is
 * opt-in.
 *
 * <p>{@link #MARKUA} is additive rather than exclusive. Docusaurus {@code :::tip} admonitions keep
 * working when it is selected, so a folder holding both kinds of source converts in one run.
 */
public enum Dialect {

    /** CommonMark plus Docusaurus {@code :::} admonitions. The default. */
    DOCUSAURUS,

    /** Everything {@link #DOCUSAURUS} accepts, plus Markua blurbs and index markers. */
    MARKUA;

    /** True when Markua-specific syntax should be recognised. */
    public boolean isMarkua() {
        return this == MARKUA;
    }

    /**
     * @throws IllegalArgumentException with a message naming the valid values, which the manifest
     *     reader passes straight through to the author
     */
    public static Dialect parse(String value) {
        if (value == null || value.isBlank()) {
            return DOCUSAURUS;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "docusaurus", "commonmark", "gfm" -> DOCUSAURUS;
            case "markua", "leanpub" -> MARKUA;
            default ->
                    throw new IllegalArgumentException(
                            "dialect must be 'docusaurus' or 'markua', found '" + value + "'");
        };
    }
}
