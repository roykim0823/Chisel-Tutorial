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

1. **Change the blink rate.** Edit `CNT_MAX` in `src/main/scala/Hello.scala`
   (e.g. blink at 2 Hz or 0.5 Hz), re-run `sbt "runMain Hello"`, and confirm the
   constant in `Hello.sv` changes accordingly.
2. **Print instead of write.** Run `sbt "runMain HelloString"` and read the
   SystemVerilog in the terminal. Compare it to the `Hello.sv` file.
3. **Tidy output.** Run `sbt "runMain HelloOption"` and find the generated file
   under `generated/`.
4. **(Stretch, from the book)** Make the LED on for 200 ms of every second
   (a short "sign-of-life" flash) instead of a 50/50 blink. Hint: use a second
   constant for the point at which you flip `blkReg`, separate from the counter
   reset value.

Next: **[Chapter 2 — Basic Components](../ch02-basic-components/README.md)**.
