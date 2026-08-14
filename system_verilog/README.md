# Appendix — SystemVerilog for Chisel Programmers

Chisel is a hardware *construction* language: your Scala program runs, builds a
circuit, and the CIRCT/firtool backend lowers it to **SystemVerilog**. That `.sv`
file — not your Chisel — is what every downstream tool consumes. Simulators,
linters, synthesis, formal, timing, and your colleagues on the verification team
all speak SystemVerilog. When something goes wrong anywhere in that flow, the
error messages, waveforms, and reports come back in a language you may never have
written.

This appendix closes that gap, in four levels of increasing depth.

*Conventions: paths are relative to `system_verilog/`, and commands are run from
the level directory being discussed.*

---

## The twelve chapters

| Chapter | Topic | Audience | Time |
|---|---|---|---|
| **Part I — Basics** | *reading generated SystemVerilog* | | |
| [**Ch 1 — SystemVerilog Syntax**](ch01-syntax/README.md) | The language as a person writes it — the primer for everything after | every Chisel user | ~2 days |
| [**Ch 2 — Core Mappings**](ch02-core-mappings/README.md) | What `Module`, `Reg`, `Wire`, `when`, `Mux` actually become | every Chisel user | ~3 days |
| [**Ch 3 — Aggregates & Reading**](ch03-aggregates/README.md) | `Vec`, `Bundle`, `SInt`, memories, FSMs, and the toolchain artefacts | every Chisel user | ~3 days |
| **Part II — Debugging** | *finding things and understanding what you find* | | |
| [**Ch 4 — Names & Waveforms**](ch04-names-waveforms/README.md) | What survives into the SV, naming rules, finding a signal in a waveform | anyone who debugs | ~4 days |
| [**Ch 5 — Clock, Reset & Interfaces**](ch05-clock-reset/README.md) | Chisel's three implicit constructs: direction (`Flipped`), clock domains, sync/async reset | anyone who debugs | ~4 days |
| [**Ch 6 — printf, assert & Toolchain**](ch06-printf-assert/README.md) | Simulation-only constructs, SVA, and the FIRRTL/CIRCT pipeline | anyone who debugs | ~5 days |
| **Part III — Synthesis** | *getting to real hardware* | | |
| [**Ch 7 — Synthesizable RTL**](ch07-synthesizable-rtl/README.md) | The synthesizable subset, latch inference, elaboration vs `generate`, `Analog`/`inout` | anyone taping out or targeting an FPGA | ~1 week |
| [**Ch 8 — Clocking & CDC**](ch08-cdc/README.md) | Clock gating, clock-domain crossing, reset synchronizers — the things simulation cannot verify | anyone with >1 clock | ~1 week |
| [**Ch 9 — Integration & PD**](ch09-integration/README.md) | `BlackBox`, SRAM macro replacement, `dontTouch` and names, SDC, lint in CI | anyone integrating with other RTL | ~1 week |
| **Part IV — At Scale** | *verification organizations and silicon* | | |
| [**Ch 10 — Verification at Scale**](ch10-verification/README.md) | Real SVA from Chisel (`assert`/`cover`/`assume` property), coverage, formal, UVM orientation | verification-facing work | ongoing |
| [**Ch 11 — Hierarchy & Pipeline**](ch11-hierarchy/README.md) | Definition/Instance, cross-hierarchy probes, driving the CIRCT pipeline | large designs | ongoing |
| [**Ch 12 — Silicon & Organization**](ch12-silicon/README.md) | UPF, gate-level, EDA tools, naming contracts, CI — **reference only, not reproducible** | tape-out and team practice | ongoing |

**Chapters 1–3 are the prerequisite for everything else, and are best read in
order.** After that the parts are independent: pick the chapter matching the work
in front of you. Within a part the chapters are also largely independent.

Every chapter is its own self-contained sbt project, except Chapter 1 (a language
primer, nothing to generate) and Chapter 12 (reference material that needs tools
no repository can ship).

---

## How this relates to the rest of the tutorial

This appendix is **independent of the book chapters**. The 15 chapters teach you
to *write* Chisel; this appendix teaches you to read, debug, and integrate what
Chisel *emits*.

Two neighbouring documents are worth knowing about:

- [`../SYSTEMVERILOG-NOTES.md`](../SYSTEMVERILOG-NOTES.md) is the **chapter-facing**
  reference. It answers "what does the file I just generated in Chapter 6 say?"
  using output from the book's own designs, and it carries the measured
  style-equivalence diffs (`when` vs. `Mux`, generator vs. handwritten arbiter).
  Narrow, tied to the chapters.
- [`../SCALA-NOTES.md`](../SCALA-NOTES.md) covers the other side of the language —
  the Scala that runs at elaboration time.

Roughly: `SYSTEMVERILOG-NOTES.md` for "what did *this chapter* generate", this
appendix for "how do I work in SystemVerilog at all".

---

## Every example is generated, not written by hand

Each level directory that has runnable examples is a **self-contained sbt
project**, pinned to the same Chisel 6.5.0 / Scala 2.13.14 / firtool 1.62.0 as
the rest of the tutorial:

```
system_verilog/
├── README.md                  this file
├── ch01-syntax/   (no project - a language primer)
│   └── README.md
├── ch02-core-mappings/
│   ├── README.md              the write-up
│   ├── build.sbt
│   ├── project/build.properties
│   ├── src/main/scala/        one file per section of the write-up
│   └── generated/             the .sv files (git-ignored; regenerate any time)
├── ch03-aggregates/
├── ch04-names-waveforms/
├── ch05-clock-reset/
├── ch06-printf-assert/
├── ch07-synthesizable-rtl/
├── ch08-cdc/
├── ch09-integration/
├── ch10-verification/
├── ch11-hierarchy/
└── ch12-silicon/
```

To reproduce every SystemVerilog block in a level:

```
$ cd ch02-core-mappings
$ sbt "runMain Generate"          # emits generated/*.sv
$ sbt "runMain Generate list"     # show the available designs
$ sbt "runMain Generate Adder"    # just one
```

**This matters more than it sounds.** SystemVerilog *written by hand* and
SystemVerilog *emitted by firtool* look meaningfully different, and a tutorial
that shows idealized output teaches you to look for things that are not there.
The clearest case: hand-written SV uses `always_ff` and `always_comb`, and
firtool emits **neither** — see
[Ch 3 §3.6](ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb).
Every SV block in A2 and A3 is real captured output from their projects, which
is why several of them are stranger than a textbook would show. A1 is the one
part that is deliberately hand-written — it teaches the language, and says so.

Where a topic genuinely cannot be demonstrated from this repo — UVM benches,
commercial synthesis reports, UPF power intent, gate-level netlists — the text
says so rather than inventing plausible output. **[D3](ch12-silicon/README.md)
is entirely in that category and opens by saying so**; every other part is
backed by a runnable project.

---

## Toolchain

Same as the chapters: Java 8–21, sbt 1.12.11, Chisel 6.5.0, Scala 2.13.14, and
the firtool 1.62.0 that ships with Chisel. See the
[tutorial index](../README.md#prerequisites) for one-time setup.

Version pinning is deliberate: generated SystemVerilog changes between firtool
releases, sometimes in ways that would invalidate the examples here. If you
upgrade, regenerate and diff before trusting a section.
