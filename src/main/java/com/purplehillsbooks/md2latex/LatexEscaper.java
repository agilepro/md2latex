package com.purplehillsbooks.md2latex;

/**
 * Escaping helpers for emitting LaTeX from arbitrary Markdown text.
 *
 * <p>Getting this wrong is the single most common source of "my generated document will not
 * compile" problems, so it lives in its own class and is unit tested directly.
 */
public final class LatexEscaper {

    private LatexEscaper() {}

    /**
     * Escapes the ten characters that are special to LaTeX in ordinary text.
     *
     * <p>The backslash must be handled first, otherwise the backslashes introduced by the other
     * replacements would themselves be escaped.
     */
    public static String text(String s) {
        return text(s, true);
    }

    /**
     * @param translate when true, characters with a built-in LaTeX equivalent in {@link
     *     CharacterMap} are replaced by it. Pass false for verbatim contexts, where a LaTeX command
     *     would be printed literally rather than obeyed.
     */
    public static String text(String s, boolean translate) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int width = Character.charCount(cp);

            String mapped = translate ? CharacterMap.replacement(cp) : null;
            if (mapped != null) {
                // Replacements are raw LaTeX and must not be escaped.
                out.append(mapped);
            } else if (width == 1) {
                char c = (char) cp;
                switch (c) {
                    case '\\' -> out.append("\\textbackslash{}");
                    case '{' -> out.append("\\{");
                    case '}' -> out.append("\\}");
                    case '~' -> out.append("\\textasciitilde{}");
                    case '^' -> out.append("\\textasciicircum{}");
                    case '#', '$', '%', '&', '_' -> out.append('\\').append(c);
                    default -> out.append(c);
                }
            } else {
                // Untranslated astral character; the safety check reports it.
                out.appendCodePoint(cp);
            }
            i += width;
        }
        return out.toString();
    }

    /**
     * Escapes a URL for use inside an href or url command. Far fewer characters need touching here,
     * and over-escaping breaks the link.
     *
     * <p>Note: a literal backslash-u sequence cannot appear in a Java comment, because the lexer
     * treats it as a unicode escape before comments are stripped. That is why LaTeX command names
     * are spelled out in prose here.
     */
    public static String url(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("#", "\\#");
    }

    /**
     * Escapes an author-written index term for the argument of {@code \index}.
     *
     * <p>Identical to {@link #text(String)} except that {@code !} is left alone, because makeindex
     * reads it as the separator between an entry and its sub-entry and an author writing a marker
     * by hand needs that. Splitting first and escaping each part keeps every other character safe.
     */
    public static String indexEntry(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        int from = 0;
        int bang;
        while ((bang = s.indexOf('!', from)) >= 0) {
            out.append(text(s.substring(from, bang))).append('!');
            from = bang + 1;
        }
        return out.append(text(s.substring(from))).toString();
    }

    /**
     * Prepares a file path for {@code \includegraphics}.
     *
     * <p>graphicx cannot cope with spaces or with extra dots in a filename. Wrapping the stem in
     * its own brace group is the standard workaround, so {@code some file.png} becomes <code>
     * {some file}.png</code> and the caller emits <code>\includegraphics{{some file}.png}</code>.
     */
    public static String imagePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        int dot = p.lastIndexOf('.');
        if (dot <= slash) {
            // No extension; brace the whole thing.
            return "{" + p + "}";
        }
        String stem = p.substring(0, dot);
        String ext = p.substring(dot);
        return "{" + stem + "}" + ext;
    }
}
