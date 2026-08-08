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
| 4 | Components | ~2231–2462 | `Comp.scala`, `Adder.scala` |
| 5 | Combinational Building Blocks | ~2463–2834 | `Combinational.scala`, `EncDec.scala`, `arbiter.scala`, `Comparator.scala` |
| 6 | Sequential Building Blocks | ~2835–3492 | |
| 7 | Input Processing | ~3493–3760 | |
| 8 | Finite-State Machines | ~3761–4104 | |
| 9 | Communicating State Machines | ~4105–4445 | |
| 10 | Hardware Generators | ~4446–5154 | |
| 11 | Example Designs | ~5155–5758 | |
| 12 | Interconnect | ~5759–6234 | |
| 13 | Debugging, Testing, and Verification | ~6235–6598 | |
| 14 | Design of a Processor | ~6599–7098 | |
| 15 | A RISC-V Pipeline | ~7099–7423 | |

(Book appendices — VHDL/Verilog, Reserved Keywords, etc. — start at line ~7424
and are not turned into chapter projects.)

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

## IMPORTANT: each chapter README must be a SUPERSET of the book chapter

A chapter's `README.md` must **contain everything the book's corresponding
chapter says** — never less. It is a superset: extra explanation, the
compile/run/check mechanics, and clarifications are welcome and encouraged, but
**nothing from the original may be dropped.**

When writing or reviewing a chapter, do a **superset audit** against the book:

1. Read the chapter's full line range in `../chisel-book.tex` (see the mapping
   table above), plus the referenced code snippets (the `//- start NAME` blocks
   in `../src/**`).
2. Walk the book **section by section** and confirm each of these made it into
   the README (add whatever is missing):
   - every **section/subsection** and its explanatory prose (motivating
     examples, use cases, forward/back references like "see Section X");
   - every **code snippet** (`\shortlist{...}`), including small illustrative
     ones (e.g. the intro `for` loop) — tag non-project code `*illustrative*`;
   - every **figure** (`\includegraphics`) and **table** (reproduce tables as
     Markdown; render figures per the Figures section);
   - domain **notes and caveats** (e.g. "optimized away by synthesis", "not a
     hardware counter", "needs state to be fair"), and the **exercise**.
3. Only *then* layer on the tutorial extras (build/run/check, expected output).

Whenever you touch a chapter, re-run this audit; a README that silently omits
book content is a bug to fix, not a stylistic choice.

## SystemVerilog generation

Every **standalone design** in a chapter must be emitted by that chapter's
`Generate` entry point. "Standalone" is the operative word:

- **Do emit** every design the chapter discusses as a thing in its own right —
  and in particular, when a chapter presents N variants of one design (ch05's
  three arbiters, ch06's five counters, ch10's three `Ticker`s, ch11's five
  FIFOs), emit **all N** at the same parameters. The generated code is the
  evidence for the book's claim that the styles are equivalent, and a reader
  cannot check it if only one variant is emitted.
- **Do not emit** submodules (`Adder`, `Tx`, `Rx`, `PopCountFSM`, …). One
  `emitVerilog` writes the whole hierarchy into one file, so a submodule is
  already there. Emitting it separately is redundant and teaches the false model
  that one Chisel `Module` maps to one Verilog file.
- **Cannot be emitted:** designs the book leaves deliberately incomplete
  (ch04's `TopLevel`/`CompA`–`CompD` have empty bodies, so their outputs are
  undriven) and pure-Scala illustrations (ch03's `AbcUser*`). Where a design is
  intentionally not emitted, say so in a comment in `Generate.scala` **and** in
  the "not emitted" list at the end of `SYSTEMVERILOG-NOTES.md`.

**Verilog has no namespaces.** Two Chisel classes in different Scala packages can
emit the same module name and silently overwrite each other's `.sv` (ch11's
top-level `BubbleFifo` vs. `fifo.BubbleFifo`). Route one family to a
sub-directory with its own `--target-dir` and explain why in a comment.

After changing any `Generate.scala`, re-run `sbt "runMain Generate"` and update
the chapter README's list of emitted files — those lists go stale silently.

## `system_verilog/` — the SystemVerilog appendix

An **independent** four-level appendix (A basics → B verification → C synthesis
→ D advanced), not tied to the book chapters. One directory per level, each
write-up named `README.md` like the chapters.

`level-a-basics/` is a full sbt project on the same pinned versions, with one
source file per write-up section and a `Generate` entry point. **Every
SystemVerilog block in Level A is real captured output** — the idealized,
hand-written SV it started with was wrong in ways that mattered (`always_ff`,
`always_comb`, `input logic`, `case` statements, and `_T_*` temporaries that
firtool never emits).

Two conventions here differ from the chapters, deliberately:

- **Generated blocks DO carry a path label** (`` `generated/Adder.sv` ``),
  unlike chapter READMEs where generated output is unlabelled. The appendix is
  about the generated files themselves, so the reader needs to find them.
- **Section 1 of Level A is hand-written SV on purpose** — it teaches the
  language. It carries a blanket note saying so; do not "fix" it to match
  generated output.

Levels **A, B1–B3, and C1–C3 are all backed by runnable projects** with verified
output (7 projects, 28 designs). B and C were each split into three parts because
each covered several separate skills. **Level D is not** — UVM, formal, UPF, and
gate-level need tools this repo does not have, so its SV blocks are still
hand-written and should be *labelled* as non-reproducible rather than fabricated.

When editing any backed level, re-run `sbt "runMain Generate"` and verify each
labelled block still matches its file line-for-line.

## `SYSTEMVERILOG-NOTES.md`

Root-level companion to `SCALA-NOTES.md`, structured the same way (lettered
sections, chapters link in with `[§K.1](../SYSTEMVERILOG-NOTES.md#...)`). It is
the Chisel ↔ SystemVerilog reference: construct mappings, how to read a
generated file, what elaboration erases, and measured diffs showing which coding
styles produce identical hardware.

Every SystemVerilog block in it is **real captured output** — same rule as the
chapter READMEs, and it matters more here because the whole point is fidelity.
When adding an example, generate it and paste it; do not reconstruct from
memory. Claims about tool behavior (which flags exist, what errors look like)
must be tested — `--strip-debug-info` is a firtool option, not a Chisel one, and
guessing produced a documented flag that does not work.

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
- **No "What's in this project" file tree.** Chapters used to open with an
  annotated directory listing; it was removed because it duplicated information
  the reader gets in context. Chapter layouts are uniform and the names are
  self-describing, and every file is introduced with a path label at the point
  where it is actually discussed — which is where the reader needs it, not
  screenfuls earlier. Do not reintroduce the tree in a new chapter.
- **Coverage invariant (replaces the tree):** every file under
  `src/main/scala/` must be named in the prose, or have its declared types
  (`class`/`object`/`trait`) discussed there. Test files are accounted for in
  aggregate in the chapter's "Build, run, and check" section — the pasted
  `sbt test` output's `Suites: completed N` must match the number of files under
  `src/test/scala/` — and are named individually only when the chapter discusses
  the test itself (e.g. ch10's `ArbiterOrderTest`, all of ch13). This is
  checkable; re-run it whenever you add or rename a source file.
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
