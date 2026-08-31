package com.purplehillsbooks.md2latex;

import java.util.Locale;
import java.util.Set;

/**
 * Rules about what pdflatex can actually typeset.
 *
 * <p>These exist so that a fault is reported against the Markdown line that
 * caused it, rather than surfacing later as an obscure TeX error against a
 * generated file the author never wrote.
 */
public final class LatexSafety {

    /**
     * Characters above Latin Extended-A that the T1 encoding plus textcomp can
     * still set. Everything else outside the supported ranges is rejected.
     */
    // Held as Integer, not Character: these are tested against a code point, and
    // a Set<Character> would silently never match an autoboxed int.
    private static final Set<Integer> EXTRA_SAFE = Set.of(
            (int) '‐', // hyphen
            (int) '‑', // non-breaking hyphen
            (int) '–', // en dash
            (int) '—', // em dash
            (int) '‘', (int) '’', (int) '‚',    // single quotes
            (int) '“', (int) '”', (int) '„',    // double quotes
            (int) '†', (int) '‡',                    // dagger, double dagger
            (int) '•',                                    // bullet
            (int) '…',                                    // ellipsis
            (int) '‰',                                    // per mille
            (int) '‹', (int) '›',                    // single guillemets
            (int) '€',                                    // euro
            (int) '™'                                     // trademark
    );

    /** Raster and vector formats pdflatex can include directly. */
    private static final Set<String> SUPPORTED_IMAGE_TYPES =
            Set.of("pdf", "png", "jpg", "jpeg", "eps", "mps");

    /**
     * LaTeX's built-in list environments nest four deep. The fifth produces
     * "Too deeply nested" and stops the run.
     */
    public static final int MAX_LIST_DEPTH = 4;

    private LatexSafety() {
    }

    /**
     * @return index of the first character pdflatex cannot set, or -1 if the
     *         whole string is safe
     */
    public static int firstUnsupportedChar(String s) {
        return firstUnsupportedChar(s, true);
    }

    /**
     * @param translate when true, a character with a built-in translation in
     *                  {@link CharacterMap} counts as supported. Pass false for
     *                  verbatim contexts, where no translation can be applied.
     * @return index of the first offending character, or -1
     */
    public static int firstUnsupportedChar(String s, boolean translate) {
        if (s == null) {
            return -1;
        }
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            if (!isSupported(cp) && !(translate && CharacterMap.contains(cp))) {
                return i;
            }
            i += Character.charCount(cp);
        }
        return -1;
    }

    private static boolean isSupported(int c) {
        if (c == '\t' || c == '\n' || c == '\r') {
            return true;
        }
        if (c >= 0x20 && c <= 0x7E) {
            return true;                       // printable ASCII
        }
        if (c >= 0xA0 && c <= 0xFF) {
            return true;                       // Latin-1 supplement, covered by T1
        }
        if (c >= 0x100 && c <= 0x17F) {
            return true;                       // Latin Extended-A, covered by T1
        }
        return EXTRA_SAFE.contains(c);
    }

    /** Human-readable form of an offending character, for an error message. */
    public static String describe(int codePoint) {
        String printable = Character.isISOControl(codePoint)
                ? ""
                : " '" + new String(Character.toChars(codePoint)) + "'";
        return String.format("U+%04X%s", codePoint, printable);
    }

    /** A hint tailored to the kind of character that was rejected. */
    public static String hintFor(int codePoint) {
        int cp = codePoint;
        if (cp >= 0x1F000 || (cp >= 0x2600 && cp <= 0x27BF)) {
            return "emoji and pictographs cannot be set by pdflatex; remove it, "
                 + "or describe it in words";
        }
        if (cp >= 0x2190 && cp <= 0x21FF) {
            return "replace the arrow with words, or with a math-mode arrow such "
                 + "as $\\rightarrow$ inside the Markdown";
        }
        if (cp >= 0x2200 && cp <= 0x22FF) {
            return "wrap mathematical symbols in $...$ so LaTeX sets them in "
                 + "math mode";
        }
        if (cp >= 0x0370 && cp <= 0x03FF) {
            return "Greek letters need math mode ($\\alpha$) or a Unicode engine; "
                 + "pdflatex cannot set them as text";
        }
        if (cp >= 0x0400 && cp <= 0x04FF || cp >= 0x0590 && cp <= 0x06FF
                || cp >= 0x3000 && cp <= 0x9FFF || cp >= 0xAC00 && cp <= 0xD7AF) {
            return "non-Latin scripts need a Unicode engine; compile with "
                 + "lualatex or xelatex, or transliterate the text";
        }
        return "replace it with an ASCII equivalent, or compile with lualatex "
             + "or xelatex instead of pdflatex";
    }

    public static boolean isSupportedImageType(String extension) {
        return SUPPORTED_IMAGE_TYPES.contains(extension.toLowerCase(Locale.ROOT));
    }

    public static String supportedImageTypes() {
        return "pdf, png, jpg, jpeg, eps";
    }

    /** File extension without the dot, lowercased; empty when there is none. */
    public static String extensionOf(String path) {
        String name = path.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        int dot = name.lastIndexOf('.');
        if (dot <= slash || dot < 0) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
