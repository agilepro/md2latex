package com.purplehillsbooks.md2latex;

import java.util.List;

/**
 * Thrown when one or more source files contain something that would produce
 * LaTeX that will not compile, or that would silently drop content.
 *
 * <p>Every problem found across every chapter is collected before this is
 * thrown, so one run tells the author everything they need to fix rather than
 * revealing faults one build at a time. No output files are written when this
 * is thrown, so a failed build never leaves a half-written book behind.
 */
public class ConversionException extends Exception {

    private static final long serialVersionUID = 1L;

    private final transient List<Problem> problems;

    public ConversionException(List<Problem> problems) {
        super(buildMessage(problems));
        this.problems = List.copyOf(problems);
    }

    public List<Problem> problems() {
        return problems;
    }

    private static String buildMessage(List<Problem> problems) {
        List<Problem> errors = problems.stream().filter(Problem::isError).toList();
        List<Problem> warnings = problems.stream().filter(p -> !p.isError()).toList();

        StringBuilder b = new StringBuilder();
        b.append("conversion stopped: ")
         .append(errors.size())
         .append(errors.size() == 1 ? " problem" : " problems")
         .append(" in the Markdown would produce LaTeX that does not compile.\n");

        for (Problem p : errors) {
            b.append('\n').append(p.format()).append('\n');
        }
        if (!warnings.isEmpty()) {
            b.append("\nAlso ").append(warnings.size())
             .append(warnings.size() == 1 ? " warning" : " warnings")
             .append(":\n");
            for (Problem p : warnings) {
                b.append('\n').append(p.format()).append('\n');
            }
        }
        b.append("\nNo files were written. Fix the source and run again.");
        return b.toString();
    }
}
