# CLAUDE.md — `tutorial/` working notes

Guidance for working on the **hands-on Chisel tutorial** in this directory.
This is a build-it-and-run-it companion to the book *Digital Design with Chisel*
by Martin Schoeberl (`../Digital Design with Chisel - Schoeberl.pdf`). It is a
**superset** of the book: everything the book says, plus the missing
compile/run/check mechanics and the book's figures/tables.

## Goal & audience

Newbie-friendly. Each chapter is a small, self-contained project a learner can
`cd` into and run in isolation — never the giant all-at-once build in `../src`.
Explanations are detailed; build/run/check steps are explicit and verified.

## Directory layout

```
tutorial/
├── README.md                     top-level index + one-time setup
├── CLAUDE.md                     this file
├── .gitignore                    ignores build output and generated *.sv/*.v
├── ch01-introduction/
│   ├── build.sbt
│   ├── project/build.properties  sbt.version=1.12.11
│   ├── src/main/scala/...
│   └── README.md                 the chapter write-up
└── ch02-basic-components/
    ├── build.sbt
    ├── project/build.properties
    ├── src/main/scala/...
    ├── src/test/scala/...
    ├── figures/*.png             chapter diagrams (see "Figures")
    └── README.md
```

- The chapter write-up file is **`README.md`** (NOT `TUTORIAL.md` — it was
  renamed so GitHub/VS Code render it automatically when browsing the folder).
- The main book repo at `../src/` is the source of truth and stays
  **untouched**. Chapter projects are separate copies to experiment with.

## Chapter ↔ book mapping

Chapters follow the book and `../Tutorial.txt`. Book source is
`../chisel-book.tex` (read the chapter's line range there for authoritative
prose, snippets, figures, and tables):

| Ch | Title | `chisel-book.tex` lines | Key files |
|----|-------|-------------------------|-----------|
| 1 | Introduction | ~447–839 | `HelloScala.scala`, `Hello.scala` |
| 2 | Basic Components | ~840–1455 | `Logic.scala`, `RegisterFile.scala` |
| 3 | Build Process and Testing | ~1456–2230 | (not built yet) |

Book code snippets are extracted from `../src/**` between `//- start NAME` /
`//- end` markers (see `../scripts/gencode.scala`). When copying code into a
chapter project, **strip those marker comments** for readability.

## Toolchain & versions (pin these exactly)

- Java 8–21 (this machine: OpenJDK 20). sbt 1.12.11.
- **Chisel 6.5.0 / Scala 2.13.14 / chiseltest 6.0.0** — identical to the main
  `../build.sbt` active config. Pinning the same versions everywhere means sbt's
  global Coursier/Ivy cache is reused and **Chisel is never re-downloaded** per
  chapter. Do not bump versions without a reason.

Each chapter's `build.sbt` uses the CIRCT plugin form:
`addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)`.

## Commands (run from inside a chapter folder)

```
sbt "runMain HelloScala"     # run a specific entry point
sbt "runMain Hello"          # emits Hello.sv (SystemVerilog)
sbt "runMain Generate"       # ch2: emits Logic.sv + RegisterFile.sv
sbt test                     # run the ChiselTest bench(es)
```

Every chapter should be **runnable**: if the book's module has no `main`, add a
small `object X extends App { emitVerilog(...) }` (see
`ch02-basic-components/src/main/scala/Generate.scala`) so `sbt run` produces
something visible.

## IMPORTANT: verify before you document

**Always actually run `sbt`** and paste the *real* captured output into the
README's "expected output" blocks. Never hand-write expected output. The
provided commands and outputs must be reproducible.

## Version note baked into the docs

The book (older Chisel) says `emitVerilog` produces `.v` (e.g. `Hello.v`).
Chisel 6 uses CIRCT/firtool and emits **SystemVerilog `.sv`** (e.g. `Hello.sv`).
Document `.sv` wherever the book says `.v`.

## Markdown conventions

- **Chapter opening:** start each chapter `README.md` with a short **proper
  introduction** to that chapter's topic (what it covers and why) — NOT a meta
  "companion to Chapter N" note. Follow it with a one-line italic conventions
  note: paths are relative to the chapter folder and commands are run from there.
- **File-path labels:** put the exact **relative** path on its own line
  (wrapped in backticks) immediately before a code block **when that code lives
  in a project file** — e.g. a line ``` `src/main/scala/Logic.scala` ``` directly
  above the fenced ```scala``` block containing `val logic = (a & b) | c`.
- **Illustrative snippets:** code that is NOT a verbatim project file (concept
  sketches, "does-not-compile" examples, condensed cheat-sheets) is tagged
  `*illustrative*` (or an inline note) so the reader knows it isn't copied from
  a file. Every fenced Scala block must carry one label or the other.
- **Generated Verilog/SystemVerilog blocks** are program output — no path label.
- Keep the book's **tables** (Chapter 2 has the operator table and the function
  table). When adding a chapter, cross-check `chisel-book.tex` for tables and
  reproduce any that are missing.
- Cross-link between chapters using `../chNN-.../README.md`; link back to the
  index with `../README.md`.

## Figures

The book's figures are PDFs in `../figures/*.pdf` (OmniGraffle sources
alongside). Markdown needs raster images, so render them to PNG:

- Only `rsvg-convert` (SVG→PNG) ships on this machine, and `pdflatex`/poppler/
  ImageMagick are **absent**. Use **PyMuPDF** (`pip install pymupdf`) to
  rasterize PDFs. Render at ~3× for crisp output:
  ```python
  import fitz
  doc = fitz.open("../figures/logic.pdf")
  pix = doc[0].get_pixmap(matrix=fitz.Matrix(3, 3), alpha=False)
  pix.save("ch02-basic-components/figures/logic.png")
  ```
- Store PNGs in the chapter's `figures/` folder. **Visually check** each render
  (open it) before committing — confirm it isn't blank or cropped.
- **Center** each figure and cap its display width (source is 3×, so set a
  smaller `width`):
  ```html
  <p align="center">
    <img src="figures/logic.png" alt="Logic for (a & b) | c" width="460">
  </p>
  ```
- Follow each figure with an italic caption: `***Figure N** — <book caption> …*`
  plus a sentence of explanation.
- PNGs are tracked (the `.gitignore` only excludes `*.sv`/`*.v` and build dirs).

Chapter 2 figures already rendered: `logic`, `mux`, `register-reset-0`,
`vec-mux`, `vec-reg`.

## Adding a new chapter (checklist)

1. `chNN-title/` with `build.sbt` + `project/build.properties` (copy an existing
   chapter's, keep versions).
2. Copy the chapter's `.scala` (and any test) from `../src`, stripping
   `//- start/end` markers. Add a `Generate`/`App` entry point if none exists.
3. Render any figures from `../figures/*.pdf` → `chNN/figures/*.png` (PyMuPDF).
4. Write `README.md` following the conventions above.
5. **Run** `sbt test` / `sbt run` and paste real output.
6. Add the chapter to `tutorial/README.md` (table + tree) and cross-links.
7. Clean generated artifacts (`rm -f *.sv`, `rm -rf generated test_run_dir`).
