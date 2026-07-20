# Digital Design with Chisel — Hands-on Tutorial

An easy-to-follow, **build-it-and-run-it** tutorial for learning
[Chisel](https://www.chisel-lang.org/), based on Martin Schoeberl's excellent
open-source book **Digital Design with Chisel**
([schoeberl/chisel-book](https://github.com/schoeberl/chisel-book)).

The book teaches the concepts beautifully, but it shows code as isolated
snippets and leaves the "how do I actually compile, run, and check this?"
details implicit. This tutorial fills that gap. For each chapter you get:

- a **self-contained sbt project** you can build and run on its own,
- the **exact commands** to build, run, and test,
- the **expected output** so you know it worked,
- **detailed explanations** and the book's figures, inline.

Think of it as a *superset* of the book's chapters: everything the book says,
plus the missing mechanics. It is an independent repository — you don't need the
book's source to use it (though the book is the ideal companion read).

---

## How this differs from the original `chisel-book` repo

This tutorial is derived from [`schoeberl/chisel-book`](https://github.com/schoeberl/chisel-book)
but is organized for *learning by doing* rather than for building the book:

| | Original `chisel-book` | This tutorial |
|---|---|---|
| **Structure** | One big sbt project compiling ~80 source files at once | One **independent sbt project per chapter** — compile & run just what you're learning |
| **Docs** | The LaTeX book (`chisel-book.tex`) | A **Markdown `README.md` in every chapter** with exact commands + real captured output |
| **Code** | All examples, some needing external files/tools | A **runnable, tested subset** per chapter; anything needing external programs, ELF loaders, or Verilator is *documented and excluded* so every project builds clean |
| **Verilog** | Book text says `emitVerilog` writes `.v` | Chisel 6 emits **SystemVerilog `.sv`** (noted throughout — see below) |
| **Figures** | LaTeX/PDF | The book's schematics rendered to **PNG** and shown inline (LaTeX-only waveforms are described in prose) |
| **Versions** | Several build configs in one file | Pinned everywhere to **Chisel 6.5.0 / Scala 2.13.14 / chiseltest 6.0.0** |

Nothing here modifies the original book or its sources; the chapter projects are
fresh, standalone copies you can freely experiment with.

---

## Prerequisites

You only need two things:

1. a **Java JDK, version 8–21** (17 is a safe, current choice), and
2. **sbt**, the Scala build tool.

That's it — sbt downloads the correct Scala compiler and Chisel libraries
automatically on the first build. `git` is handy for cloning but not required.

Pick **one** of the installation routes below, then jump to
[Verify your setup](#verify-your-setup).

### Option A — one tool for everything (any OS): SDKMAN

[SDKMAN](https://sdkman.io/) installs and manages both the JDK and sbt on macOS,
Linux, and Windows (WSL / Git-Bash):

```
$ curl -s "https://get.sdkman.io" | bash
$ source "$HOME/.sdkman/bin/sdkman-init.sh"
$ sdk install java 17.0.13-tem     # Eclipse Temurin JDK 17
$ sdk install sbt
```

(Prefer [Coursier](https://get-coursier.io/)? Its `cs setup` also installs a JDK
+ sbt + Scala on every platform.)

### Option B — native package manager

**macOS** (with [Homebrew](https://brew.sh/)):

```
$ brew install temurin sbt git      # temurin = Eclipse Temurin JDK
```

**Ubuntu / Debian** — a JDK from apt, then sbt from its official repository:

```
$ sudo apt-get update
$ sudo apt-get install -y openjdk-17-jdk git curl gnupg
# add the official sbt apt repository (one time):
$ echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" \
    | sudo tee /etc/apt/sources.list.d/sbt.list
$ curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
    | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/scalasbt-release.gpg
$ sudo apt-get update
$ sudo apt-get install -y sbt
```

**Fedora / RHEL:**

```
$ sudo dnf install -y java-17-openjdk-devel git
$ sudo dnf install -y sbt        # or use SDKMAN if sbt isn't packaged
```

**Windows** (with [winget](https://learn.microsoft.com/windows/package-manager/);
[Scoop](https://scoop.sh/) or [Chocolatey](https://chocolatey.org/) work too):

```
> winget install EclipseAdoptium.Temurin.17.JDK
> winget install sbt.sbt
> winget install Git.Git
```

### Option C — Docker (Ubuntu 24.04)

For a throwaway, fully reproducible environment, the repo ships a
[`Dockerfile`](Dockerfile) based on Ubuntu 24.04 with JDK 17, sbt, `make`, and
the optional `verilator`/`z3` tools. It also pre-downloads Chisel so the first
build inside the container is fast:

```
$ docker build -t chisel-tutorial .
$ docker run -it --rm chisel-tutorial        # drops you into /tutorial
# then, inside the container:
$ make test CH=ch01-introduction
```

To work on your own checkout instead of the copy baked into the image, mount it:

```
$ docker run -it --rm -v "$PWD":/tutorial chisel-tutorial
```

With Docker you can skip the "Verify your setup" and "Optional tools" steps
below — the image already has everything.

### Verify your setup

Open a **new** terminal (so PATH changes take effect) and check both tools:

```
$ java -version
openjdk version "17.0.13" ...

$ sbt --version
sbt runner version: 1.x.x
```

Both commands should print a version. If `java` reports a version **above 21**,
install a JDK in the 8–21 range (Chisel's Scala compiler plugin doesn't yet
support the newest JDKs) — SDKMAN (Option A) makes switching easy:
`sdk install java 17.0.13-tem && sdk default java 17.0.13-tem`.

### Optional tools

None of these are needed to build or test the chapters; they come up in a few
places:

- **[GTKWave](https://gtkwave.sourceforge.net/)** — to view `.vcd` waveforms
  (Chapters 3, 6, 13). `brew install gtkwave` / `sudo apt install gtkwave`.
- **[Verilator](https://www.veripool.org/verilator/)** — an alternative,
  faster ChiselTest simulation backend (Chapter 13). Default is Treadle, which
  needs nothing extra. `brew install verilator` / `sudo apt install verilator`.
- **[Z3](https://github.com/Z3Prover/z3)** — the SMT solver for formal
  verification (`verify`, Chapter 13). `brew install z3` / `sudo apt install z3`.

> Chisel 6 bundles the `firtool` Verilog backend, so there is **nothing extra to
> install** to generate SystemVerilog — it just works after sbt is set up.

### An IDE (optional)

An editor is enough, but an IDE with a background compiler speeds up coding.
Both work well; open a **chapter folder** (the one containing `build.sbt`) as
the project root:

- **VS Code** with the [Metals](https://scalameta.org/metals/) extension.
- **IntelliJ IDEA** with the Scala plugin (*File → Open* the chapter's
  `build.sbt`).

---

## How this is organized

Each chapter lives in its own folder and is a **complete, independent sbt
project**. You don't build one giant thing — you `cd` into a chapter and work
there. Every chapter folder follows the same shape:

```
chNN-name/
├── build.sbt                 this chapter's build definition (pinned versions)
├── project/build.properties  pins the sbt version
├── src/main/scala/           the Chisel source
├── src/test/scala/           the test bench(es)          (most chapters)
├── figures/                  the chapter's diagrams (PNG) (most chapters)
└── README.md                 the chapter write-up — start here
```

See each chapter's own `README.md` for its exact file list and walkthrough; the
full chapter list is in the [Chapters](#chapters) table below.

> **Why separate projects?** A per-chapter project compiles only that chapter,
> so the edit → build → run loop is fast and the scope is small.

> **Does each project re-download Chisel? No.** sbt stores downloaded libraries
> in a single **global cache** (Coursier/Ivy, under `~/.cache/coursier` and
> `~/.ivy2`). Every chapter pins the *same* versions, so Chisel is downloaded
> **once** and reused by every chapter afterward.

---

## Building and running

### One chapter

```
$ cd ch01-introduction
$ sbt test                   # run this chapter's tests
$ sbt "runMain HelloScala"   # run a specific entry point
$ sbt "runMain Generate"     # (most chapters) emit SystemVerilog
```

The first `sbt` command in a session is slow (JVM boot + first-time downloads);
everything after is fast. To avoid repeated JVM boots, use the interactive
shell:

```
$ sbt
sbt:ch01-introduction> test
sbt:ch01-introduction> runMain Generate
sbt:ch01-introduction> exit
```

### All chapters at once

A top-level `Makefile` drives every chapter:

```
$ make test          # run `sbt test` in every chapter
$ make test CH=ch08-finite-state-machines   # just one chapter
$ make clean         # remove all build artifacts (target/, *.sv, test_run_dir/, ...)
$ make list          # list the chapter folders
```

Continuous integration (GitHub Actions, `.github/workflows/test.yml`) runs
`make test` on every push.

---

## Version note: Verilog vs. SystemVerilog

The book (written for older Chisel) says `emitVerilog` produces a `.v` file such
as `Hello.v`. Modern Chisel (6.x, used here) uses the CIRCT/firtool backend and
produces a **SystemVerilog** file with a `.sv` extension, e.g. `Hello.sv`. The
content is equivalent for our purposes; look for `.sv` wherever the book says
`.v`.

---

## Chapters

The chapters follow the book and build on one another, so working through them
in order is the smoothest path — early chapters set up the toolchain and the
building blocks that the later system and processor designs reuse. That said,
each chapter is a standalone project, so you can also jump straight to a topic.
Click a chapter to open its walkthrough.

| Chapter | What you'll build & run |
|--------|--------------------------|
| [`ch01-introduction/`](ch01-introduction/README.md) | A Scala "Hello World", then a blinking-LED hardware module; generate its SystemVerilog. |
| [`ch02-basic-components/`](ch02-basic-components/README.md) | Combinational logic, a multiplexer, registers, `Bundle`/`Vec`, and a 32×32 register file; run a test bench and generate SystemVerilog. |
| [`ch03-build-and-testing/`](ch03-build-and-testing/README.md) | sbt build, packages, Verilog generation and tool flow; ScalaTest, ChiselTest (`poke`/`step`/`expect`), waveforms, and `printf` debugging. |
| [`ch04-components/`](ch04-components/README.md) | Modules and ports, instantiating and connecting components, a counter from an adder + register, nested hierarchy, an ALU with `switch`/`is`, and the `<>` bulk-connect operator. |
| [`ch05-combinational-building-blocks/`](ch05-combinational-building-blocks/README.md) | `when`/`.elsewhen`/`.otherwise`/`WireDefault`, decoder and encoder, a priority arbiter (three styles), priority encoder, and a comparator. |
| [`ch06-sequential-building-blocks/`](ch06-sequential-building-blocks/README.md) | Registers and enables, counters (five styles) and tick generation, a one-shot timer, PWM, shift registers, and `SyncReadMem` memory with forwarding. |
| [`ch07-input-processing/`](ch07-input-processing/README.md) | Metastability and the two-flip-flop synchronizer, debouncing, majority-voting noise filter, rising-edge detection, and synchronizing the reset signal. |
| [`ch08-finite-state-machines/`](ch08-finite-state-machines/README.md) | Moore and Mealy FSMs with `ChiselEnum`: an alarm FSM plus a rising-edge detector done both ways, and when to prefer each. |
| [`ch09-communicating-state-machines/`](ch09-communicating-state-machines/README.md) | Factoring FSMs (a light flasher), a state machine with a datapath (FSMD popcount), and the ready/valid `DecoupledIO` handshake. |
| [`ch10-hardware-generators/`](ch10-hardware-generators/README.md) | Functions returning hardware, ROM/logic-table generation (BCD), parameters/case classes/type parameters, inheritance, and functional programming (`reduceTree`, min-search). |
| [`ch11-example-designs/`](ch11-example-designs/README.md) | A bubble FIFO, generalized ready/valid FIFOs (five implementations, one test), and a modular UART with a loopback test. |
| [`ch12-interconnect/`](ch12-interconnect/README.md) | On-chip bus concepts, a pipelined handshake (`PipeCon`), a counter IO device, and a memory-mapped bridge to a ready/valid stream. |
| [`ch13-debugging-testing-verification/`](ch13-debugging-testing-verification/README.md) | Waveform/printf debugging, readable tests with functions and tags, internal-signal access via `BoringUtils`, fork/join, backends, assertions, and formal verification. |
| [`ch14-design-of-a-processor/`](ch14-design-of-a-processor/README.md) | The Leros accumulator processor: ISA, an ALU with accumulator (tested vs. a Scala model), instruction decoder, and data memory; the FSMD datapath explained. |
| [`ch15-a-risc-v-pipeline/`](ch15-a-risc-v-pipeline/README.md) | The Wildcat 3-stage pipelined RISC-V (RV32I): datapath as functions (ALU, decoder, register file), instruction ROM and CSRs, with the full `ThreeCats` CPU generated to Verilog. |


---

## Going further: contributing Chisel

*(Distilled from the book's "Contributing to Chisel" chapter.)*

Once you're writing your own Chisel, share it as a **library**, not copy-pasted
source — "two copies are never the same," and copies are painful to keep in
sync. Compiled Chisel is just platform-independent Java class files, so the
Scala/Java ecosystem (Maven Central, versioning) is ideal for sharing hardware.

- **Use a library** by adding it to `build.sbt`, e.g. the community collection
  [`ip-contributions`](https://github.com/freechipsproject/ip-contributions)
  (which includes UART and FIFO designs like the ones in Chapter 11):
  ```
  libraryDependencies += "edu.berkeley.cs" % "ip-contributions" % "0.5.4"
  ```
- **Publish a library** to Maven Central via [Sonatype](https://central.sonatype.org/)
  (register a `groupId`, add signing/credentials, then `sbt publishSigned`).
- **Contribute** your circuit to `ip-contributions`, or improvements to
  [Chisel itself](https://www.chisel-lang.org/), with a GitHub pull request.

Contributions to *this tutorial* are welcome too: if you spot an error, an
unclear explanation, or a command that doesn't reproduce, please open an issue
or a pull request.

---

## License and attribution

This tutorial is derived from the open-source
[`schoeberl/chisel-book`](https://github.com/schoeberl/chisel-book) by
**Martin Schoeberl** (Technical University of Denmark). The Chisel example code
carries the upstream **Simplified BSD (BSD 2-Clause)** license; see
[`LICENSE`](LICENSE). The explanatory prose accompanies the book *Digital Design
with Chisel* — please consult the [original repository](https://github.com/schoeberl/chisel-book)
for the book's own licensing, and cite the book if you build on this material.
