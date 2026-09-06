package com.purplehillsbooks.md2latex;

import java.util.Locale;

/** One of the two things a manifest can be turned into. */
public enum Target {

    /** A LaTeX book: a master document and one file per chapter. */
    LATEX,

    /** A folder of Markdown inside a Docusaurus site's docs tree. */
    DOCUSAURUS;

    /**
     * @throws IllegalArgumentException with a message naming the valid values, which the command
     *     line passes straight through to the author
     */
    public static Target parse(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "latex", "tex", "pdf" -> LATEX;
            case "docusaurus", "docs", "web", "site" -> DOCUSAURUS;
            default ->
                    throw new IllegalArgumentException(
                            "target must be 'latex' or 'docusaurus', found '" + value + "'");
        };
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
