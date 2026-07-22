# Chapter 1 — Introduction

Welcome to the tutorial. This first chapter gets your toolchain running and
takes you from a plain-Scala "Hello World" to its hardware equivalent — a
blinking LED that you describe in Chisel and turn into synthesizable
SystemVerilog. Along the way you compile and run Chisel code with `sbt` and
generate your very first piece of hardware. No prior Verilog or VHDL experience
is assumed — Chisel can be your first hardware description language.

*Conventions: every file path is relative to `tutorial/ch01-introduction/`, and
every command is meant to be run from that folder.*

---

## 1.0 What is Chisel, in one paragraph

**Chisel** (Constructing Hardware In a Scala Embedded Language) is a *hardware
construction language*. You write Scala code; when that code runs, it does not
"do" the computation — instead it **builds a description of a hardware
circuit**. Chisel then emits that circuit as **Verilog/SystemVerilog**, which
you feed to an FPGA or ASIC toolchain (or to a simulator). Two ideas to hold
onto from the start:

1. **Chisel code generates hardware.** A line like `val logic = (a & b) | c`
   does not compute a value — it *wires up an AND gate and an OR gate*.
2. **Chisel is a Scala library.** The "language" is just Scala plus a set of
   types (`UInt`, `Bool`, `Module`, …). Anything legal in Scala is legal here,
   which is where Chisel's power (generators, parameterization) comes from.

You do **not** need to know Verilog or VHDL to follow along.

### Who this is for

Chisel (and the book this tutorial follows) targets **two groups**:

1. **Hardware designers** fluent in VHDL or Verilog — who today reach for Python,
   Java, or Tcl to *generate* hardware — can move to a single language where
   hardware generation is part of the language itself.
2. **Software programmers** curious about hardware design (increasingly relevant
   as CPUs ship with programmable fabric to accelerate software).

Chisel raises the abstraction level above traditional digital-design books so
you can build more complex, interacting systems in less time. It brings software
engineering — object-oriented and functional programming — into digital design,
and lets you describe hardware not just at the register-transfer level but as
reusable **generators**. It is perfectly fine for Chisel to be your first
hardware description language.

### What you'll need (and what you won't)

This is a tutorial in digital design and Chisel — **not** a general introduction
to digital-design fundamentals (if you need to know how a gate is built from CMOS
transistors, consult a dedicated digital-design text). It assumes:

- basic **Boolean algebra** and the **binary number system**,
- some **programming experience** in any language, and
- basic **command-line / terminal (CLI)** familiarity, since the build uses
  `sbt` and `make`.

No Verilog or VHDL knowledge is required. Verilog appears only as the
intermediate language Chisel emits for simulation and synthesis.

> **On Chisel and Scala.** Chisel is not a big language — its core constructs fit
> on [one page](https://github.com/freechipsproject/chisel-cheatsheet/releases/latest/download/chisel_cheatsheet.pdf)
> and can be learned in a few days (it is smaller than VHDL/Verilog, which carry
> many legacies). Its power comes from being embedded in **Scala**, "a language
> that grows on you." You do not need to learn Scala first; Chapter 10 gives the
> Scala-for-hardware-designers primer. This tutorial is neither a Scala textbook
> nor a Chisel language reference.

Every code example in this tutorial (as in the book) is compiled and tested, so
snippets should be free of syntax errors, and the examples aim to show not just
working Chisel but **good hardware-description style**.

> **Toolchain setup** (Java JDK 8–21, `sbt`, an optional IDE, and the `$`-prompt
> convention used in the command blocks) is a one-time step covered in the
> [tutorial index / README](../README.md#prerequisites). For building on a real
> FPGA you also need a vendor synthesis tool — see the exercise in §1.6.

---

## 1.1 What's in this project

```
ch01-introduction/
├── build.sbt                         ← declares Scala + Chisel dependencies
├── project/build.properties          ← pins the sbt version (1.12.11)
└── src/main/scala/
    ├── HelloScala.scala              ← plain-Scala hello (NOT hardware)
    └── Hello.scala                   ← the blinking-LED hardware module
```

By Scala/sbt convention, application source lives under `src/main/scala/`.
sbt finds and compiles everything there automatically — you never list files
manually.

---

## 1.2 Hello World (this is Scala, *not* hardware)

Every language book starts with "Hello World". Here is the first attempt.

`src/main/scala/HelloScala.scala`
```scala
object HelloScala extends App {
  println("Hello Chisel World!")
}
```

- `object HelloScala` — a Scala singleton object. `extends App` makes its body
  runnable as a program (the body *is* the `main`).
- `println(...)` — ordinary console printing.

### Build & run

```
$ sbt "runMain HelloScala"
```

`runMain <name>` tells sbt exactly which entry point to run. (Plain `sbt run`
also works, but this project has several runnable objects, so `sbt run` would
stop and ask you to pick one — `runMain` avoids the prompt.)

### Expected output

```
[info] compiling 2 Scala sources to .../target/scala-2.13/classes ...
[info] done compiling
[info] running HelloScala
Hello Chisel World!
[success] Total time: 3 s
```

### The point of this example

Look closely: **there is no hardware here.** No `Module`, no `UInt`, no clock.
This program runs on the JVM and prints a string, exactly like a "Hello World"
in Java or Python. It is *not* a representative hardware example — it only
proves your toolchain (Java + sbt + Scala) works. So what *is* a hardware
"Hello World"? A blinking LED.

---

## 1.3 The real Hello World: a blinking LED

The hardware equivalent of "Hello World" is the smallest useful, *visible*
design: an LED that blinks. If you can make an LED blink, your whole
toolchain — description, synthesis, and the board — works.

`src/main/scala/Hello.scala`
```scala
import chisel3._

class Hello extends Module {
  val io = IO(new Bundle {
    val led = Output(UInt(1.W))
  })
  val CNT_MAX = (50000000 / 2 - 1).U

  val cntReg = RegInit(0.U(32.W))
  val blkReg = RegInit(0.U(1.W))

  cntReg := cntReg + 1.U
  when(cntReg === CNT_MAX) {
    cntReg := 0.U
    blkReg := ~blkReg
  }
  io.led := blkReg
}
```

You are **not** expected to understand every detail yet — Chapter 2 unpacks
`Module`, `IO`, `Reg`, and `when`. But here is the idea, line by line:

- `import chisel3._` — brings the Chisel types and functions into scope.
- `class Hello extends Module` — a hardware **module** (a reusable block with
  ports), the Chisel equivalent of a Verilog `module` or VHDL `entity`.
- `val io = IO(new Bundle { val led = Output(UInt(1.W)) })` — the module's
  ports. Here, a single 1-bit **output** named `led`.
- `val CNT_MAX = (50000000/2 - 1).U` — a constant. The board's clock ticks
  ~50 million times per second (50 MHz). To toggle the LED at **1 Hz** (on for
  half a second, off for half a second) we count half of 50 million cycles.
  The `.U` turns the Scala integer into an unsigned hardware constant.
- `val cntReg = RegInit(0.U(32.W))` — a **register** (32 flip-flops) that
  resets to 0. This is our counter.
- `val blkReg = RegInit(0.U(1.W))` — a 1-bit register holding the LED state.
- `cntReg := cntReg + 1.U` — **every clock cycle**, the counter increments.
  (`:=` means "drive this hardware", not Scala assignment — see Chapter 2.)
- `when(cntReg === CNT_MAX) { … }` — when the counter reaches the top, reset it
  to 0 and **flip** the LED bit (`~blkReg`). That flip every half-second is the
  blink.
- `io.led := blkReg` — connect the LED register to the output port.

> Everything inside a `Module` describes hardware that runs **in parallel**,
> every clock cycle — not top-to-bottom like a normal program.

### Generating the hardware (SystemVerilog)

A `Module` by itself is just a description. To turn it into synthesizable
SystemVerilog we call `emitVerilog`. This project provides a small runnable
object that does exactly that:

`src/main/scala/Hello.scala` (same file, further down)
```scala
object Hello extends App {
  emitVerilog(new Hello())
}
```

Build and run it:

```
$ sbt "runMain Hello"
```

### Expected output

```
[info] running Hello
[success] Total time: 1 s
```

and a new file **`Hello.sv`** appears in this folder.

> **Book vs. reality:** the book calls this file `Hello.v`. Chisel 6 emits
> **SystemVerilog** (`Hello.sv`) via the CIRCT/firtool backend. Same idea, new
> extension.

### Read the generated hardware

Open `Hello.sv` and find the module (skip the randomization macros at the top):

```systemverilog
module Hello(
  input  clock,
         reset,
  output io_led
);

  reg [31:0] cntReg;
  reg        blkReg;
  always @(posedge clock) begin
    if (reset) begin
      cntReg <= 32'h0;
      blkReg <= 1'h0;
    end
    else begin
      automatic logic _GEN;
      _GEN = cntReg == 32'h17D783F;      // 24999999 = 50000000/2 - 1
      if (_GEN)
        cntReg <= 32'h0;
      else
        cntReg <= cntReg + 32'h1;
      blkReg <= _GEN ^ blkReg;
    end
  end
  assign io_led = blkReg;
endmodule
```

Two things worth noticing, both mentioned in the book's exercise:

1. **`clock` and `reset` appear as inputs — but you never wrote them.** Chisel
   adds them implicitly and wires every register to them for you. That is why
   the Chisel source has no clock or reset in its `io` bundle.
2. `32'h17D783F` is hexadecimal for `24999999` = `50000000/2 - 1`, i.e. your
   `CNT_MAX`. The generated hardware matches your description exactly.

### Two more entry points (variations)

`src/main/scala/Hello.scala` also defines:

```scala
object HelloOption extends App {
  emitVerilog(new Hello(), Array("--target-dir", "generated"))
}

object HelloString extends App {
  val s = getVerilogString(new Hello())
  println(s)
}
```

- `sbt "runMain HelloOption"` → writes the SystemVerilog into a `generated/`
  subfolder instead of the current directory (handy for keeping output tidy).
- `sbt "runMain HelloString"` → prints the SystemVerilog straight to the
  console instead of writing a file.

Try each and watch where the output goes.

---

## 1.4 Running it on real hardware or in simulation (optional)

This project only *generates* the hardware; it does not include an FPGA
project or a simulation of the LED (the book does that in the companion
`chisel-examples` repo). For the full board experience:

- **On an FPGA:** feed `Hello.sv` to Intel Quartus or AMD Vivado, assign the
  `clock`, `reset`, and `io_led` pins for your board, compile, and program the
  device. See the book's Chapter 1 exercise for the details.
- **In simulation:** the book lowers the clock constant from `50000000` to
  `50000` (so the blink happens within a short simulation) and runs `sbt test`
  against a tester. We introduce Chisel testing properly in Chapter 2's test
  bench and in the book's Chapter 3.

---

## 1.5 Recap

- Chisel is a Scala library that **builds** hardware; running Chisel code emits
  Verilog/SystemVerilog.
- `sbt "runMain X"` builds and runs the entry point `X`.
- A plain-Scala `println` program (`HelloScala`) is *not* hardware — it just
  proves the toolchain works.
- The blinking LED (`Hello`) is the real hardware "Hello World": a counter, a
  toggling register, and one output.
- `clock` and `reset` are added implicitly by Chisel.

---

## 1.6 Exercises

These first three exercises use only this project:

1. **Change the blink rate.** Edit `CNT_MAX` in `src/main/scala/Hello.scala`
   (e.g. blink at 2 Hz or 0.5 Hz), re-run `sbt "runMain Hello"`, and confirm the
   constant in `Hello.sv` changes accordingly.
2. **Print instead of write.** Run `sbt "runMain HelloString"` and read the
   SystemVerilog in the terminal. Compare it to the `Hello.sv` file.
3. **Tidy output.** Run `sbt "runMain HelloOption"` and find the generated file
   under `generated/`.

### 4. Get a real LED blinking on an FPGA (the book's exercise)

The book's introduction exercise runs the blinking LED on an actual board. It
uses the companion **[`chisel-examples`](https://github.com/schoeberl/chisel-examples)**
repo (a superset of what's here), where `hello-world/` is set up as a minimal
project:

```
$ git clone https://github.com/schoeberl/chisel-examples.git
$ cd chisel-examples/hello-world/
$ sbt run
```

After the initial download this produces the Verilog file (`Hello.v` in the
book's older Chisel; `.sv` today). **Explore it:** it has two inputs `clock` and
`reset` and one output `io_led`, even though the Chisel module declares none of
them — Chisel adds `clock`/`reset` implicitly and wires every register to them,
so in most designs you never deal with these low-level details by hand.

Next, build it for a board:

1. Set up an FPGA **project file** for your synthesis tool, **assign the pins**,
   **compile** the Verilog, and **configure** the FPGA with the resulting
   bitfile. ("Compile" here really means: synthesize the logic, place and route,
   run timing analysis, and generate a bitfile.)
2. You need a vendor synthesis tool. Intel's **Quartus Prime Lite** and AMD's
   **Vivado WebPACK** are free for small/medium FPGAs (both Windows/Linux, not
   macOS); **[F4PGA](https://f4pga.org/)** is a fully open-source alternative for
   selected FPGAs. Consult the tool's manual for the exact steps — the
   `chisel-examples` repo ships ready-made **Quartus projects** (folder
   `quartus/`) for popular boards such as the DE2-115. If yours is supported,
   open the project, press **Play** to compile, then **Programmer** to configure
   the board.

**Congratulations — you have your first Chisel design running on an FPGA!** If the
LED doesn't blink, check the **reset**: on the DE2-115 the reset input is wired to
switch **SW0**.

Now change the blinking frequency and rebuild. Blink rates and patterns convey
different "emotions": a slow blink says *everything is OK*, a fast blink signals
*alarm*. Explore which frequencies best express each.

As a harder extension, make the LED on for **200 ms of every second** (a short
"sign-of-life" flash). Decouple the LED toggle from the counter reset — use a
**second constant** for the point at which you flip `blkReg`, separate from the
counter's reset value. What emotion does this pattern produce — alarming, or more
a sign of life?

### 5. No board? Simulate it

You can run the blinking LED without hardware, using Chisel's simulation. To keep
the simulation short, **lower the clock constant in the Chisel code from
`50000000` to `50000`**, then:

```
$ sbt test
```

The tester runs for one million clock cycles. Because the perceived blink rate
depends on your host's simulation speed, you may need to experiment with the
assumed clock frequency to actually *see* the simulated LED blink. (This project
has no test bench of its own; the runnable tester lives in the `chisel-examples`
`hello-world` project. We introduce Chisel testing properly in Chapter 2's test
bench and in Chapter 3.)

---

## 1.7 Source access, the book, and further reading

**Source & the book.** This tutorial is derived from Martin Schoeberl's
open-source book *Digital Design with Chisel*
([`schoeberl/chisel-book`](https://github.com/schoeberl/chisel-book)), which is
free as a PDF and available in print from
[Amazon](https://www.amazon.com/dp/168933603X/). All of the book's code examples
are compiled and CI-tested, and larger designs are collected in
[`chisel-examples`](https://github.com/schoeberl/chisel-examples) and
[`ip-contributions`](https://github.com/freechipsproject/ip-contributions). Found
a typo or error (here or in the book)? A GitHub pull request or issue is the most
convenient way to fix it. The book repo also ships LaTeX **slides** and
**lab exercises** for a 13-week
[Digital Electronics](http://www2.imm.dtu.dk/courses/02139/) course at DTU, and
builds end-to-end with a single `make`.

**Further reading** for digital design and Chisel:

- **[Digital Design: A Systems Approach](http://www.cambridge.org/es/academic/subjects/engineering/circuits-and-systems/digital-design-systems-approach)**
  — a digital-design textbook by William J. Dally and R. Curtis Harting
  (Verilog and VHDL editions). Several later chapters' exercises cite it.
- The **[Chisel home page](https://www.chisel-lang.org/)** — the official place
  to download and learn Chisel.
- The **[Digital Electronics 2](http://www2.imm.dtu.dk/courses/02139/)** course
  at DTU — slides for a 13-week Chisel-based course (source in the book repo).
- **[schoeberl/chisel-lab](https://github.com/schoeberl/chisel-lab)** — Chisel
  exercises for that course; also good for self-study alongside the book.
- **[chisel-empty](https://github.com/schoeberl/chisel-empty)** — a minimal
  starter project (an adder + a test), usable as a GitHub template.
- The **[Chisel3 Cheat Sheet](https://github.com/freechipsproject/chisel-cheatsheet/releases/latest/download/chisel_cheatsheet.pdf)**
  — the main Chisel constructs on a single page.
- Scott Beamer's **[Agile Hardware Design](https://classes.soe.ucsc.edu/cse228a/Winter24/)**
  course — advanced Chisel; [lectures](https://github.com/agile-hw/lectures) are
  runnable Jupyter notebooks.
- **[ChiselTest](https://github.com/ucb-bar/chiseltest)** — the testing library,
  in its own repository.
- The **[Generator Bootcamp](https://github.com/freechipsproject/chisel-bootcamp)**
  — a Chisel course focused on hardware generators, as a Jupyter notebook.
- The **[Chisel Tutorial](https://github.com/ucb-bar/chisel-tutorial)** — a
  ready project of small exercises with testers and solutions (a bit outdated).
- A **[Chisel Style Guide](https://github.com/ccelio/chisel-style-guide)** by
  Christopher Celio.

Back to the **[tutorial index](../README.md)**.
Next: **[Chapter 2 — Basic Components](../ch02-basic-components/README.md)**.
