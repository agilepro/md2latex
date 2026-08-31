package com.purplehillsbooks.md2latex;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in translations from Unicode characters to LaTeX.
 *
 * <p>pdflatex cannot typeset most characters outside Latin Extended-A, but many
 * of them have an exact, unambiguous LaTeX equivalent: an arrow is
 * {@code \rightarrow}, a Greek alpha is {@code \alpha}. Those need no decision
 * from the author, so they are translated here rather than being rejected.
 *
 * <p>Characters with no sensible equivalent - emoji, pictographs, other scripts
 * - are deliberately absent, and remain conversion errors reported against the
 * line that used them.
 *
 * <p>Every command used here comes from base LaTeX or from {@code amssymb},
 * both of which the generated preamble already loads. Math symbols are wrapped
 * in {@code \ensuremath} so they work whether or not the surrounding text is
 * already in math mode.
 *
 * <p>Keyed on code point rather than {@code char} so characters outside the
 * basic plane are handled whole rather than as surrogate halves.
 */
public final class CharacterMap {

    private static final Map<Integer, String> REPLACEMENTS = build();

    private CharacterMap() {
    }

    /** The LaTeX for a code point, or null when there is no translation. */
    public static String replacement(int codePoint) {
        return REPLACEMENTS.get(codePoint);
    }

    public static boolean contains(int codePoint) {
        return REPLACEMENTS.containsKey(codePoint);
    }

    public static int size() {
        return REPLACEMENTS.size();
    }

    /** Code point to replacement, for documentation and tests. */
    public static Map<Integer, String> entries() {
        return new LinkedHashMap<>(REPLACEMENTS);
    }

    private static Map<Integer, String> build() {
        Map<Integer, String> m = new LinkedHashMap<>();

        // ---- Arrows ------------------------------------------------------
        math(m, '\u2190', "leftarrow");
        math(m, '\u2191', "uparrow");
        math(m, '\u2192', "rightarrow");
        math(m, '\u2193', "downarrow");
        math(m, '\u2194', "leftrightarrow");
        math(m, '\u2195', "updownarrow");
        math(m, '\u21D0', "Leftarrow");
        math(m, '\u21D1', "Uparrow");
        math(m, '\u21D2', "Rightarrow");
        math(m, '\u21D3', "Downarrow");
        math(m, '\u21D4', "Leftrightarrow");
        math(m, '\u21A6', "mapsto");
        math(m, '\u27F5', "longleftarrow");
        math(m, '\u27F6', "longrightarrow");
        math(m, '\u27F7', "longleftrightarrow");
        math(m, '\u27F9', "Longrightarrow");

        // ---- Comparison and arithmetic -----------------------------------
        math(m, '\u2260', "neq");
        math(m, '\u2264', "leq");
        math(m, '\u2265', "geq");
        math(m, '\u226A', "ll");
        math(m, '\u226B', "gg");
        math(m, '\u2248', "approx");
        math(m, '\u2245', "cong");
        math(m, '\u2261', "equiv");
        math(m, '\u221D', "propto");
        math(m, '\u2213', "mp");
        math(m, '\u2217', "ast");
        math(m, '\u22C5', "cdot");
        math(m, '\u2219', "bullet");
        math(m, '\u221A', "surd");
        math(m, '\u221E', "infty");

        // ---- Set theory and logic ----------------------------------------
        math(m, '\u2208', "in");
        math(m, '\u2209', "notin");
        math(m, '\u220B', "ni");
        math(m, '\u2282', "subset");
        math(m, '\u2283', "supset");
        math(m, '\u2286', "subseteq");
        math(m, '\u2287', "supseteq");
        math(m, '\u222A', "cup");
        math(m, '\u2229', "cap");
        math(m, '\u2205', "emptyset");
        math(m, '\u2200', "forall");
        math(m, '\u2203', "exists");
        math(m, '\u2204', "nexists");          // amssymb
        math(m, '\u2227', "land");
        math(m, '\u2228', "lor");
        math(m, '\u2234', "therefore");        // amssymb
        math(m, '\u2235', "because");          // amssymb
        math(m, '\u22A2', "vdash");
        math(m, '\u22A8', "models");

        // ---- Calculus and larger operators -------------------------------
        math(m, '\u2211', "sum");
        math(m, '\u220F', "prod");
        math(m, '\u222B', "int");
        math(m, '\u2202', "partial");
        math(m, '\u2207', "nabla");

        // ---- Greek, lower case -------------------------------------------
        String[] lower = {
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
            "iota", "kappa", "lambda", "mu", "nu", "xi", null, "pi",
            "rho", null, "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega"
        };
        for (int i = 0; i < lower.length; i++) {
            if (lower[i] != null) {
                math(m, (char) ('\u03B1' + i), lower[i]);
            }
        }
        math(m, '\u03D1', "vartheta");
        math(m, '\u03D5', "varphi");
        math(m, '\u03F1', "varrho");
        math(m, '\u03C2', "varsigma");

        // ---- Greek, upper case (only those LaTeX defines) ------------------
        math(m, '\u0393', "Gamma");
        math(m, '\u0394', "Delta");
        math(m, '\u0398', "Theta");
        math(m, '\u039B', "Lambda");
        math(m, '\u039E', "Xi");
        math(m, '\u03A0', "Pi");
        math(m, '\u03A3', "Sigma");
        math(m, '\u03A5', "Upsilon");
        math(m, '\u03A6', "Phi");
        math(m, '\u03A8', "Psi");
        math(m, '\u03A9', "Omega");

        // ---- Marks and shapes --------------------------------------------
        math(m, '\u2713', "checkmark");        // amssymb
        math(m, '\u2714', "checkmark");
        math(m, '\u2717', "times");
        math(m, '\u2718', "times");
        math(m, '\u2715', "times");
        math(m, '\u25A1', "square");           // amssymb
        math(m, '\u25A0', "blacksquare");      // amssymb
        math(m, '\u25CB', "circ");
        math(m, '\u25B3', "triangle");
        math(m, '\u2605', "bigstar");          // amssymb
        math(m, '\u2606', "star");
        math(m, '\u2661', "heartsuit");
        math(m, '\u2665', "heartsuit");
        math(m, '\u2666', "diamondsuit");
        math(m, '\u2663', "clubsuit");
        math(m, '\u2660', "spadesuit");
        math(m, '\u266F', "sharp");
        math(m, '\u266D', "flat");

        // ---- Text-mode punctuation and symbols ----------------------------
        // Characters at or below U+017F are already setable by T1 and are
        // deliberately left alone here.
        math(m, '\u2032', "prime");
        text(m, '\u2033', "\\ensuremath{\\prime\\prime}");
        text(m, '\u2044', "/");
        text(m, '\u2212', "\\ensuremath{-}");
        text(m, '\u2027', "\\ensuremath{\\cdot}");
        text(m, '\u2043', "-");
        text(m, '\u25E6', "\\ensuremath{\\circ}");
        text(m, '\u2023', "\\ensuremath{\\blacktriangleright}");

        // Spaces that would otherwise vanish or break lines oddly.
        text(m, '\u2002', "\\enspace{}");
        text(m, '\u2003', "\\quad{}");
        text(m, '\u2009', "\\,");
        text(m, '\u200B', "");                 // zero-width space: drop
        text(m, '\uFEFF', "");                 // byte-order mark: drop

        // Fractions LaTeX has no single glyph for.
        text(m, '\u2153', "\\ensuremath{1/3}");
        text(m, '\u2154', "\\ensuremath{2/3}");
        text(m, '\u2155', "\\ensuremath{1/5}");
        text(m, '\u215B', "\\ensuremath{1/8}");

        return m;
    }

    /** A math symbol, wrapped so it works in text and in math mode alike. */
    private static void math(Map<Integer, String> m, char c, String command) {
        m.put((int) c, "\\ensuremath{\\" + command + "}");
    }

    private static void text(Map<Integer, String> m, int codePoint, String latex) {
        m.put(codePoint, latex);
    }
}
