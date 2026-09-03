package com.purplehillsbooks.md2latex;

import java.util.Map;

/**
 * Maps Markdown info strings onto language names the {@code listings} package actually knows.
 *
 * <p>This matters more than it looks. Passing {@code language=json} to lstlisting is a hard LaTeX
 * error, and JSON, YAML, JavaScript, TypeScript and Markdown are all absent from the built-in set,
 * so an unfiltered pass-through of Markdown info strings reliably produces documents that will not
 * compile. Anything unrecognised falls back to an unhighlighted listing.
 */
public final class ListingsLanguages {

    private static final Map<String, String> KNOWN =
            Map.ofEntries(
                    Map.entry("ada", "Ada"),
                    Map.entry("awk", "Awk"),
                    Map.entry("bash", "bash"),
                    Map.entry("sh", "bash"),
                    Map.entry("shell", "bash"),
                    Map.entry("zsh", "bash"),
                    Map.entry("console", "bash"),
                    Map.entry("c", "C"),
                    Map.entry("cpp", "C++"),
                    Map.entry("c++", "C++"),
                    Map.entry("cxx", "C++"),
                    Map.entry("csharp", "[Sharp]C"),
                    Map.entry("cs", "[Sharp]C"),
                    Map.entry("delphi", "Delphi"),
                    Map.entry("erlang", "erlang"),
                    Map.entry("fortran", "Fortran"),
                    Map.entry("gnuplot", "Gnuplot"),
                    Map.entry("haskell", "Haskell"),
                    Map.entry("html", "HTML"),
                    Map.entry("xhtml", "HTML"),
                    Map.entry("java", "Java"),
                    Map.entry("lisp", "Lisp"),
                    Map.entry("lua", "Lua"),
                    Map.entry("make", "make"),
                    Map.entry("makefile", "make"),
                    Map.entry("matlab", "Matlab"),
                    Map.entry("ml", "ML"),
                    Map.entry("objectivec", "[Objective]C"),
                    Map.entry("objc", "[Objective]C"),
                    Map.entry("octave", "Octave"),
                    Map.entry("pascal", "Pascal"),
                    Map.entry("perl", "Perl"),
                    Map.entry("php", "PHP"),
                    Map.entry("prolog", "Prolog"),
                    Map.entry("python", "Python"),
                    Map.entry("py", "Python"),
                    Map.entry("r", "R"),
                    Map.entry("ruby", "Ruby"),
                    Map.entry("rb", "Ruby"),
                    Map.entry("sas", "SAS"),
                    Map.entry("scilab", "Scilab"),
                    Map.entry("sql", "SQL"),
                    Map.entry("tcl", "tcl"),
                    Map.entry("tex", "TeX"),
                    Map.entry("latex", "TeX"),
                    Map.entry("vbscript", "VBScript"),
                    Map.entry("verilog", "Verilog"),
                    Map.entry("vhdl", "VHDL"),
                    Map.entry("xml", "XML"),
                    Map.entry("xslt", "XSLT"),
                    Map.entry("xsl", "XSLT"));

    private ListingsLanguages() {}

    /**
     * @return the listings language name, or {@code null} when the language is unknown and the
     *     listing should be emitted without highlighting.
     */
    public static String resolve(String infoString) {
        if (infoString == null || infoString.isBlank()) {
            return null;
        }
        String first = infoString.trim().split("\\s+")[0].toLowerCase();
        return KNOWN.get(first);
    }
}
