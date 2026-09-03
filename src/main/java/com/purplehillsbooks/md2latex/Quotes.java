package com.purplehillsbooks.md2latex;

/**
 * Turns straight, non-directional quotation marks into directional ones.
 *
 * <p>TeX has no straight quote for prose. It builds the opening mark from two backticks and the
 * closing mark from two apostrophes, so a literal {@code "} left in the source sets a vertical
 * typewriter mark that looks wrong in a book. The directional characters an author's editor already
 * produces need no decision and are translated by {@link CharacterMap}; the straight ones do, and
 * that decision is made here.
 *
 * <p>The rule is positional: a mark opens when nothing precedes it, or when what precedes it is a
 * space or an opening delimiter, and closes otherwise. That gets the ordinary cases right - {@code
 * "Hello," she said, "goodbye."} - and reads a measurement like {@code 6"} as a closing mark, which
 * is the conventional stand-in for an inch sign anyway.
 *
 * <p>The straight <em>single</em> quote is treated as an apostrophe unless it sits inside a
 * quotation, because that is overwhelmingly what it is: {@code don't}, {@code the '90s}, {@code
 * Jones'}. Inside a double-quoted phrase the balance flips - a nested quotation is then the likely
 * reading - so there, and only there, a single quote in opening position becomes an opening mark.
 * An elision that keeps its apostrophe by starting with a digit, as in {@code the '90s}, is still
 * left alone.
 *
 * <p>This class is stateful and sequential, because a quotation routinely spans nodes: in {@code "a
 * **bold** word"} the two marks sit in different Text nodes with an emphasis between them, and the
 * nesting depth has to survive the crossing. One instance belongs to one run of prose.
 */
public final class Quotes {

    static final char LEFT_DOUBLE = '“';
    static final char RIGHT_DOUBLE = '”';
    static final char LEFT_SINGLE = '‘';
    static final char RIGHT_SINGLE = '’';

    /** Context at the start of a block, where a mark can only be an opening one. */
    public static final char START_OF_BLOCK = 0;

    /** The character before the next one to be considered. */
    private char prev = START_OF_BLOCK;

    /** True between an opening and a closing double quote, where nesting is expected. */
    private boolean insideDouble;

    /**
     * Resets to the start of a block. A quotation does not run across a paragraph break, so an
     * unbalanced mark in one paragraph must not leave the next one inverted.
     */
    public void startBlock() {
        prev = START_OF_BLOCK;
        insideDouble = false;
    }

    /** Records content emitted without curling - a code span - as the context that follows it. */
    public void passThrough(String text) {
        prev = (text == null || text.isEmpty()) ? 'x' : text.charAt(text.length() - 1);
    }

    /** Records a line break, after which a mark opens. */
    public void lineBreak() {
        prev = ' ';
    }

    /** Replaces every straight quote that needs it with the directional character. */
    public String directional(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> c = openingHere() ? LEFT_DOUBLE : RIGHT_DOUBLE;
                case '\'' -> {
                    if (insideDouble && openingHere() && !elisionAt(text, i)) {
                        c = LEFT_SINGLE;
                    }
                }
                default -> {}
            }
            if (c == LEFT_DOUBLE) {
                insideDouble = true;
            } else if (c == RIGHT_DOUBLE) {
                insideDouble = false;
            }
            out.append(c);
            prev = c;
        }
        return out.toString();
    }

    /** True when a mark in this position is an opening one. */
    private boolean openingHere() {
        if (prev == START_OF_BLOCK || Character.isWhitespace(prev)) {
            return true;
        }
        return switch (prev) {
                // Opening delimiters, and the dashes that introduce quoted speech.
            case '(', '[', '{', '<', LEFT_SINGLE, LEFT_DOUBLE, '–', '—', '-', '/' -> true;
            default -> false;
        };
    }

    /**
     * True when the apostrophe at {@code i} stands for dropped characters rather than opening a
     * quotation. Only the numeric form is detected - {@code the '90s} - because that one is
     * unambiguous; {@code 'twas} and {@code rock 'n' roll} are not distinguishable from an opening
     * mark by shape alone and are left to the author to write as {@code ’}.
     */
    private static boolean elisionAt(String text, int i) {
        return i + 1 < text.length() && Character.isDigit(text.charAt(i + 1));
    }
}
