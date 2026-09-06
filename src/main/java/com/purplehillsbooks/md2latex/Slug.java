package com.purplehillsbooks.md2latex;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Turns a title or a filename into something safe to use as one.
 *
 * <p>Both targets need this and both need it for the same reason, though they answer to different
 * masters: {@code \input} takes an unescaped path, so a LaTeX chapter file may not contain a space
 * or a brace, and a Docusaurus filename becomes part of a URL. Lower case, digits and hyphens
 * satisfy both without anyone having to think about it again.
 */
final class Slug {

    private Slug() {}

    /** The name of a file, without its extension, reduced to letters, digits and hyphens. */
    static String ofFile(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return of(dot > 0 ? name.substring(0, dot) : name);
    }

    /** Reduces any text to letters, digits and single hyphens, never empty. */
    static String of(String text) {
        StringBuilder safe = new StringBuilder();
        for (char c : text.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                safe.append(c);
            } else if (!safe.isEmpty() && safe.charAt(safe.length() - 1) != '-') {
                safe.append('-');
            }
        }
        while (!safe.isEmpty() && safe.charAt(safe.length() - 1) == '-') {
            safe.setLength(safe.length() - 1);
        }
        return safe.isEmpty() ? "section" : safe.toString();
    }
}
