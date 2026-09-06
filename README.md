# md2latex

Builds a book from a folder of Markua. One manifest describes one book, and the
same source produces either or both of two outputs:

- **LaTeX** — a master `.tex` plus one file per chapter, ready to compile.
- **Docusaurus** — a folder of Markdown inside a site's `docs` tree, with the
  sidebar order, the categories and the images already in place.

The two are read by the same front end and gated by the same errors, so they
cannot drift apart or disagree about whether the source was sound.

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

**2. Build.** Run the command in the folder that holds the converter.

```bash
java -jar target/md2latex.jar docs/Morality/book.manifest
```

Passing the folder instead of the file works too, as long as it holds exactly
one `*.manifest`. Every target the manifest describes is built. To build just
one of them:

```bash
java -jar target/md2latex.jar docs/Morality --target docusaurus
```

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

latex:                        # omit the block to build no book
  directory: ../../latex      # where the .tex files go
  main:      book.tex

docusaurus:                   # omit the block to build no site
  directory: ../../site/docs/morality
  category:  "Moral Realism"  # sidebar label. Default: the book title
  position:  2                # where the section sits in the sidebar
  format:    md               # md | mdx | none
  assets:    true             # copy referenced images alongside

document:
  class:       book           # book | report | article
  toc:         true
  tocDepth:    2
  numberDepth: 2

code: listings                # listings | minted | verbatim

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

An unrecognized key is an error rather than being silently ignored, which
catches typos like `autor:`. A manifest naming neither `latex:` nor
`docusaurus:` builds LaTeX into `latex/`, which is what every manifest written
before the site target existed means. `output:` is still accepted as the old
name for `latex:`.

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
admonitions become a framed block in the book and stay as they are on the site.
For LaTeX, relative `.md` links keep their text and drop the unresolvable
target; for Docusaurus they are repointed at the generated page.

## Markua

Source is always read as Markua. There is no `dialect:` key any more —
Docusaurus `:::` admonitions are recognized as well, so a folder holding both
kinds of source converts in one run.

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

## The Docusaurus output

Name a `docusaurus:` block and the same source is written a second time, as
Markdown a site can serve. **Everything in that folder is generated and none of
it should be edited**: the Markua stays where it is and the site is built from
it, which is the whole point of keeping the two trees apart.

```
site/docs/morality/
    _category_.json           the book, as one section of the sidebar
    introduction.md
    what-morality-is.md
    part-two/                 from a manifest 'part:' divider
        _category_.json
        the-argument.md
    images/tribe.png          copied from beside the Markua
    .md2latex-generated       the list of what was generated
```

**A page keeps the name of the Markua file it came from**, exactly as written —
not lower-cased and not slugged — so the generated tree reads against its source
and anything already linking to a page still finds it. The only name that
changes is a `.markdown` extension, which becomes `.md` because Docusaurus does
not collect the longer spelling.

Order is carried by the `sidebar_position` front matter and by each folder's
`_category_.json`, so nothing needs encoding in a filename as well. Two chapters
that would land on the same page — `intro.md` and `../shared/intro.md`, say —
stop the build with both paths named, rather than one quietly overwriting the
other.

The manifest already says everything a sidebar needs, so none of it has to be
repeated in `sidebars.js`. Order comes from the chapter list, a `part:` divider
becomes a folder with its own category, and appendices come back to the top
level. A page whose chapter is later dropped from the manifest is removed on the
next run — that is what the stamp file is for, and it also clears out pages left
by an earlier version of the converter. A file the converter did not write is
never touched.

What changes on the way across:

| Markua | LaTeX | Docusaurus |
|---|---|---|
| `W>`, `{blurb, class: warning}` | `\begin{admonition}{Warning}` | `:::warning` |
| `A>` aside, `X>` exercise, `D>` discussion … | a framed block | the nearest of Docusaurus's five types, keeping its own name as the title: `:::info[Exercise]` |
| `B>` untitled blurb | `admonitionplain` | a blockquote — Docusaurus has no admonition without a heading |
| `C>` centred | `\begin{center}` | `<div align="center">`, which works as both HTML and JSX |
| `{i: "tribe"}`, `indexTerms:` | `\index{}` and `makeindex` | **dropped** — a site has no index |
| `<!-- latex: … -->` | emitted raw | dropped |
| `<!-- docusaurus: … -->` | dropped | unwrapped and emitted raw |
| `"` and `--` | `` `` ``, `''`, `---` | `“ ” – —` |
| `→ α ≤ ✓ 😀` | `\ensuremath{…}`, or an error | left alone; a browser sets them |
| `![x](images/x.png)` | path relative to the master `.tex` | copied into the site and repointed |
| `[x](other.md)` | text kept, target dropped | repointed at the generated page |

Two things worth knowing:

**`format: md` is written into every page.** Docusaurus 3 parses `.md` as MDX,
where `{` and `<` are JSX — so a stray brace or a `a < b` in ordinary prose
fails the site build with an error pointing somewhere else. The `format` key
tells Docusaurus to read the file as CommonMark instead. Set `format: mdx` if
you want JSX, or `format: none` to write no key at all for a Docusaurus too old
to know it.

**Errors are target-scoped.** An emoji and an `.svg` image stop a LaTeX build
and are perfectly fine on the web, so those checks only run for the book. A
broken image is an error for LaTeX and a warning for the site. But the gate is
shared: if either target refuses, *neither* is written, so the site can never
get ahead of a book that will not compile.

## When it refuses

Anything that would produce LaTeX that does not compile stops the build with a
located error, and **nothing is written** — so a failed run never leaves a
half-written or stale book behind:

```
conversion stopped: 2 problems in the Markdown would produce output that does not work.

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

## Quotation marks

TeX has no straight quote for prose: it builds the opening mark from two
backticks and the closing mark from two apostrophes. Quotes are translated to
that spelling automatically, whichever way you type them.

| You write | Output | Renders as |
|---|---|---|
| `“` | `` `` `` | opening double |
| `”` | `''` | closing double |
| `‘` | `` ` `` | opening single |
| `’` | `'` | closing single, apostrophe |
| `"` | `` `` `` or `''` | whichever the position calls for |

The straight `"` is the only one needing a decision, and it is positional: it
opens at the start of a paragraph, after a space, or after an opening bracket or
dash, and closes anywhere else. So

```markdown
"Hello," she said, "and goodbye."
```

comes out as ``` ``Hello,'' she said, ``and goodbye.'' ```. A quotation spanning
emphasis works too — the two marks sit in different places in the document but
the decision still sees what came before, so `A "quote with **bold** inside"`
opens and closes correctly.

### Nested quotations

A single-quoted word inside a double-quoted phrase works, typed either way:

```markdown
"He said 'hello' to me"      →   ``He said `hello' to me''
“He said ‘hello’ to me”      →   ``He said `hello' to me''
```

Two things make this work. First, a straight `'` is normally an apostrophe —
`don't`, `Jones'` — but **inside** a double-quoted phrase, one in opening
position is read as a nested quotation instead. Outside a quotation it is always
left alone, so `The '90s were good` keeps its apostrophe; so does `"Back in the
'90s,"`, because an apostrophe before a digit is an elision, never a quotation.

Second, when the marks meet, a thin space `\,` is inserted between them. This
matters: TeX reads runs of apostrophes greedily, so the closing single of a
nested quotation followed by the closing double around it — `'` then `''` —
would otherwise be read as `''` then `'` and **print the two marks in the wrong
order**. So:

```markdown
"he said 'hello'"            →   ``he said `hello'\,''
"'hello' he said"            →   ``\,`hello' he said''
```

which is also how a typesetter would set them.

The elisions that stay ambiguous are `'twas` and `rock 'n' roll` *inside* a
quotation — nothing in the shape distinguishes those from an opening mark. Write
them with `’` if you have any.

### Left alone

- **Straight quotes inside code**, both `` `inline` `` and fenced blocks. Code
  keeps the characters you wrote.
- **The straight single quote outside a quotation.** TeX already sets `'` as a
  right single quote, which is what `don't`, `the '90s` and `Jones'` all need.

A measurement like `6"` becomes a closing mark, which is the conventional
stand-in for an inch sign. For a genuine prime, write `′` or `″`. If you need a
literal straight quote in prose, the escape hatch works: `<!-- latex: " -->`.

## Dashes

TeX tells the three dashes apart by how many hyphens are written, and a keyboard
has only the one key, so a run of hyphens is read for what it means:

| You write | Output | Renders as |
|---|---|---|
| `well-known` | `-` | hyphen |
| `pages 3-5` | `--` | en dash, the numeric range |
| `--` | `---` | em dash |
| `—` | `---` | em dash |
| `–` | `--` | en dash |

A single hyphen stays a hyphen unless there is a **digit on both sides**, which
makes it a range: `pages 3-5` and `2020-2021` become en dashes, while `A-1` and
`catch-22` stay hyphenated. Two hyphens are Markua's way of writing an em dash
and become three; three are already an em dash and are left alone.

Each run is read once, so `3-5` becomes an en dash and stops there rather than
being picked up again as an em dash. If you want an em dash between numerals,
write `3--5` — the range rule needs a *single* hyphen, so two is always taken as
you asking for an em dash.

Code keeps the hyphens you wrote, in both `` `inline` `` spans and fenced
blocks, so a command line like `` `git log --oneline` `` is safe. **Write
command-line flags in code spans**: `--verbose` in ordinary prose becomes an em
dash followed by `verbose`, because there is no way to tell it from an author
asking for a dash.

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

The source is read once and written twice. `MarkdownLoader` finds the Markua and
hands each construct to a `MarkuaSink`; which sink is plugged in is the whole of
the difference between the two targets.

```
converter/
  pom.xml
  src/main/java/com/purplehillsbooks/md2latex/
      Main.java            CLI entry point
      Options.java         argument parsing and usage text
      Build.java           runs the targets, then writes if none refused
      BuildPlan.java       what a build intends to put on disk
      Target.java          latex | docusaurus
      Manifest.java        parsed manifest
      ManifestReader.java  YAML parsing and validation
      ManifestScaffold.java   --init

    read once, by both targets
      MarkdownLoader.java  front matter, blurbs and markers, with a line map
      InlineScanner.java   prose told apart from code, URLs, tags and markers
      MarkuaSink.java      where what the reader finds is sent
      Quotes.java          straight quotes made directional by position
      Dashes.java          hyphen, en dash and em dash told apart
      IndexTerms.java      index term collection and matching
      Slug.java            safe file names
      Problem.java / ConversionException.java   located errors

    the LaTeX target
      LatexMarkerSink.java Markua to HTML markers the parser can see
      BookBuilder.java     the manifest as a set of .tex files
      Md2Latex.java        one document to a LaTeX fragment
      LatexVisitor.java    the AST walk that emits LaTeX
      LatexEscaper.java    escaping
      LatexSafety.java     what pdflatex can actually typeset
      CharacterMap.java    Unicode to LaTeX translation table
      Preamble.java        preamble and document wrapper

    the Docusaurus target
      DocusaurusSink.java  Markua to Docusaurus Markdown
      DocusaurusBuilder.java  the manifest as a docs folder and its sidebar
      Md2Docusaurus.java   one document, front matter and all
      DocusaurusAssets.java   links repointed, images copied
      DocusaurusCharacters.java   the much shorter web translation table
  src/test/java/...        297 tests
```
