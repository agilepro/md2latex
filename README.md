# md2latex

Builds a LaTeX book from a folder of GitHub-flavored Markdown. One manifest
describes one book; the output is a master `.tex` plus one file per chapter,
ready to compile.

## Build the tool

Needs JDK 17+ and Maven.

```bash
cd converter
mvn package
```

That produces `target/md2latex.jar`. Run `mvn test` on its own to just run the
test suite.

## Run it

**1. Create a manifest.** It lives in the folder with the Markdown.  Run the command in the folder that holds the converter.

```bash
java -jar target/md2latex.jar --init docs/Morality --title "My Book"
```

This writes `docs/Morality/book.manifest`, listing every `.md` file it found,
ordered by `sidebar_position` front matter then by filename. Open it and fix the
order, delete anything that should not be in the book, and set the author.

You only need `--init` once. Running it again overwrites the manifest and loses
your edits; use `-o` to write somewhere else if you want to compare.

**2. Build the LaTeX:**

Run the command in the folder that holds the converter.

```bash
java -jar target/md2latex.jar docs/Morality/book.manifest
```

Passing the folder instead of the file works too, as long as it holds exactly
one `*.manifest`.

**3. Compile the PDF.** The exact command is printed at the end of every build:

```bash
cd latex && xelatex book.tex
```

If the book has an index, `makeindex` has to run between two passes:

```bash
cd latex && xelatex book.tex && makeindex book && xelatex book.tex
```

Add `-v` to list each chapter file as it is written, `--help` for full usage.  The LaTeX variant `xelatex` is preferred because it handles unicode characters better.

## The manifest

YAML. Every relative path is resolved against the manifest's own folder, so
chapter names are usually bare filenames. Only `title` and `chapters` are
required.

```yaml
title:    "Essentials of Moral Realism"
subtitle: "A short book"
author:   "Your Name"
date:     ""                  # empty prints no date

output:
  directory: ../../latex      # where the .tex files go
  main:      book.tex

document:
  class:       book           # book | report | article
  toc:         true
  tocDepth:    2
  numberDepth: 2

code: listings                # listings | minted | verbatim
dialect: docusaurus           # docusaurus | markua

preamble:                     # raw lines appended to the preamble
  - \usepackage{microtype}

frontMatter: 
  - introduction.md

chapters:                     # order here is the order in the book
  - chapter1.md
  - file:  definitions.md
    title: Terms of Discussion    # overrides the file's H1
  - part:  Part Two               # a \part divider, reads no file

appendices:
  - ../shared/appendix.md         # a file outside the folder is fine
```

An unrecognized key is an error rather than being silently
ignored, which catches typos like `autor:`.

## Markdown front matter

```yaml
---
sidebar_position: 3
indexTerms: tribe, eudaimonia, moral realism
---
```

`index` builds a LaTeX index. Terms are pooled across the **whole book**, so a
word declared in one chapter is indexed everywhere it appears. Matching is
whole-word and case-insensitive — `tribe` matches `Tribe` but not `tribes`, so
list variants explicitly. Only paragraphs and headings are searched, never code,
URLs, image paths, lists or tables, and each term is recorded once per section
so a common word does not become an unreadable run of page numbers.

It must be one line; a YAML list under `indexTerms:` is rejected. Terms may not
contain `" | @ ! \ { } %`. For a sub-entry, place the marker by hand:

```markdown
<!-- latex: \index{tribe!in-group} -->
```

That `<!-- latex: ... -->` comment is a general escape hatch — anything inside it
is copied straight into the output, for the occasional `\clearpage` or anything
the converter cannot express.

## What gets converted

Headings, emphasis, lists, block quotes, tables, images, footnotes, task lists,
strikethrough, links, and fenced code blocks. Docusaurus `:::tip[Title]`
admonitions become a framed block. Relative `.md` links keep their text and drop
the unresolvable target.

## Markua

Set `dialect: markua` in the manifest and two more pieces of syntax are
recognized. It is opt-in because both are ordinary prose in plain Markdown — a
line starting `A>` is just text, and `{i: ...}` is just a word in braces — so
turning them on unconditionally would silently rewrite existing documents.
Docusaurus `:::` admonitions keep working when it is on, so a folder holding
both kinds of source converts in one run.

**Blurbs**, in both the fenced form and the older line-prefix form:

```markdown
{blurb, class: warning}
Mind the gap.
{/blurb}

W> Mind the gap.
```

Both become the same framed block as a `:::warning` admonition, headed with the
class name. `{aside}` and `{blurb, class: X}` are equivalent to `A>` and `X>`,
and `{/aside}` and `{/blurb}` both close either one. The letters are `A` aside,
`W` warning, `T` tip, `E` error, `I` information, `Q` question, `D` discussion,
`X` exercise, `B` a blurb with no heading, and `C` a centred block. A run of
prefixed lines is one blurb; a bare `A>` inside it starts a new paragraph, and
the first line without the prefix ends it. Blurb contents are still Markdown,
fenced code included. Blurbs do not nest.

`B>` needs an untitled companion to style.tex's `admonition` environment. It is
written into the master document rather than into style.tex, guarded so that a
style.tex which does define it wins, so a book made before this existed still
compiles.

**Index markers**, which place an entry exactly where you want it:

```markdown
Early societies organised themselves by tribe{i: "tribe"}.
```

The term may be quoted or bare, and `!` separates an entry from its sub-entry,
so `{i: "tribe!kinship"}` indexes *kinship* under *tribe*. Markers work
anywhere — including in headings, where the entry is moved past the closing
brace because a sectioning command is a moving argument, and inside footnotes
and table cells, where the automatic matching described above is suppressed.
They are ignored inside fenced code blocks and inside code spans, and `\{i: ...}`
is a literal one. An *indented* code block is not detected, so write a literal
marker in a fenced block.

This is the same machinery as the front-matter `indexTerms:` list and the two
mix freely. Markers are the better tool when a word appears often but should be
indexed at one particular place. `" | @` are rejected, as they are in
`indexTerms`; `!` is allowed here because you are writing the entry by hand.

Attribute lists on images, code blocks and tables, cross references, and part
and matter structure attributes are **not** supported. A `{width: 40%}` line
above an image is passed through as ordinary text.

## When it refuses

Anything that would produce LaTeX that does not compile stops the build with a
located error, and **nothing is written** — so a failed run never leaves a
half-written or stale book behind:

```
conversion stopped: 2 problems in the Markdown would produce LaTeX that does not compile.

broken.md:6:22: error: the text contains U+1F600 '😀', which pdflatex cannot typeset
    Shipped the release 😀 and it went fine.
                        ^
    hint: emoji and pictographs cannot be set by pdflatex; remove it, or
          describe it in words

broken.md:18:1: error: image 'logo.svg' is a .svg file, which pdflatex cannot include
    ![logo](logo.svg)
    ^
    hint: convert it to one of: pdf, png, jpg, jpeg, eps

No files were written. Fix the source and run again.
```

Caught this way: characters pdflatex cannot set and cannot translate, missing or
unsupported images, lists nested more than four deep, table rows wider than
their header, and `\end{verbatim}` inside a verbatim code block. Every problem
in every file is reported in one run.

## Unusual characters

Characters with an exact LaTeX equivalent are translated automatically — you do
not have to do anything. Arrows, comparison operators, Greek letters, set and
logic symbols, calculus operators, and common marks all work:

```markdown
Input → output, where α ≤ β, x ∈ S, ∀ y. Done ✓
```

becomes `\ensuremath{\rightarrow}`, `\ensuremath{\leq}`, `\ensuremath{\alpha}`
and so on. The table lives in `CharacterMap.java`; add a line there to teach it
a new character. Everything it emits uses base LaTeX or `amssymb`, both already
loaded, and `\ensuremath` so the symbol works in text and in math alike.

Characters with no sensible equivalent — emoji, pictographs, other scripts — are
still errors, because guessing at a substitution would be worse than saying so.

Two limits worth knowing:

- Translation does **not** apply inside fenced code blocks. Those are set
  verbatim, so a LaTeX command would print literally rather than being obeyed.
  The error message says as much when it sees one.
- For a one-off with no table entry, the escape hatch works inline:
  `input <!-- latex: $\rightarrow$ --> output`.

Exit codes: `0` success, `1` a manifest or conversion error, `2` a usage error.

## Layout

```
converter/
  pom.xml
  src/main/java/com/purplehillsbooks/md2latex/
      Main.java            CLI entry point
      Options.java         argument parsing and usage text
      Manifest.java        parsed manifest
      ManifestReader.java  YAML parsing and validation
      ManifestScaffold.java   --init
      BookBuilder.java     converts every chapter, then writes the file set
      Md2Latex.java        one Markdown document to a LaTeX fragment
      MarkdownLoader.java  front matter, admonitions and Markua, with a line map
      Dialect.java         which non-CommonMark syntax is recognised
      LatexVisitor.java    the AST walk that emits LaTeX
      LatexEscaper.java    escaping
      LatexSafety.java     what pdflatex can actually typeset
      IndexTerms.java      index term collection and matching
      CharacterMap.java    Unicode to LaTeX translation table
      Preamble.java        preamble and document wrapper
      Problem.java / ConversionException.java   located errors
  src/test/java/...        190 tests
```
