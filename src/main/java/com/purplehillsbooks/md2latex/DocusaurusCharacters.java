package com.purplehillsbooks.md2latex;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in translations from Unicode characters to Docusaurus Markdown.
 *
 * <p>This is the counterpart of {@link CharacterMap} for the web, and it is deliberately almost
 * empty. The two targets need very different tables because they have very different problems: a
 * browser sets an arrow, a Greek alpha, a checkmark and an emoji without being asked, so an entry
 * for any of them would be a translation into something no better than what the author wrote.
 * pdflatex sets none of them, which is why {@link CharacterMap} has two hundred entries and this
 * has a handful.
 *
 * <p>What is left is the small set of characters that are invisible or actively harmful in a source
 * file: zero-width spaces and byte-order marks, which turn up in text pasted from a word processor
 * and then break a word search or a heading anchor for reasons nobody can see, and the Unicode line
 * and paragraph separators, which are whitespace to a browser but not to a Markdown parser.
 *
 * <p>Quotation marks and dashes are absent on purpose. They are handled before this table is
 * consulted, by {@link Quotes} and {@link Dashes}, which decide from position what a straight quote
 * or a run of hyphens was meant to be. Both already produce the Unicode character, so the web needs
 * nothing further; only LaTeX, which cannot set those characters, goes on to translate them again.
 */
public final class DocusaurusCharacters {

    private static final Map<Integer, String> REPLACEMENTS = build();

    private DocusaurusCharacters() {}

    /** The replacement for a code point, or null when the character passes through unchanged. */
    public static String replacement(int codePoint) {
        return REPLACEMENTS.get(codePoint);
    }

    public static int size() {
        return REPLACEMENTS.size();
    }

    /** Applies the table to a run of prose. */
    public static String translate(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = null;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int width = Character.charCount(cp);
            String replacement = REPLACEMENTS.get(cp);
            if (replacement == null) {
                if (out != null) {
                    out.appendCodePoint(cp);
                }
            } else {
                if (out == null) {
                    out = new StringBuilder(text.length()).append(text, 0, i);
                }
                out.append(replacement);
            }
            i += width;
        }
        return out == null ? text : out.toString();
    }

    private static Map<Integer, String> build() {
        Map<Integer, String> m = new LinkedHashMap<>();

        // Invisible characters that survive a copy and paste and then confuse
        // anchor generation, search and word wrapping. Dropping them is safe:
        // nothing renders, so nothing is lost.
        m.put(0x200B, ""); // zero-width space
        m.put(0x200C, ""); // zero-width non-joiner
        m.put(0x200D, ""); // zero-width joiner
        m.put(0xFEFF, ""); // byte-order mark

        // Whitespace to a browser, but a line ending to some Markdown parsers
        // and neither here nor there to others. A plain space is what was meant.
        m.put(0x2028, " "); // line separator
        m.put(0x2029, " "); // paragraph separator

        // A soft hyphen inside a word is invisible until the line happens to
        // break there, which is never what a book's prose intends on the web.
        m.put(0x00AD, "");

        return m;
    }
}
