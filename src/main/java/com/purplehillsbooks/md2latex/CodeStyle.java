package com.purplehillsbooks.md2latex;

/** How fenced code blocks should be rendered. */
public enum CodeStyle {

    /**
     * The {@code listings} package. Needs no special compiler flags, but only
     * knows a fixed set of languages (see {@link ListingsLanguages}).
     */
    LISTINGS,

    /**
     * The {@code minted} package. Much better highlighting and far broader
     * language coverage via Pygments, but requires Python plus running
     * pdflatex with {@code -shell-escape}.
     */
    MINTED,

    /**
     * Plain {@code verbatim}. Maximum portability, no highlighting, and it
     * breaks if the code itself contains {@code \end{verbatim}}.
     */
    VERBATIM;

    public static CodeStyle parse(String s) {
        return switch (s.toLowerCase()) {
            case "listings", "lstlisting" -> LISTINGS;
            case "minted" -> MINTED;
            case "verbatim" -> VERBATIM;
            default -> throw new IllegalArgumentException(
                    "Unknown code style '" + s + "' (expected listings, minted or verbatim)");
        };
    }
}
