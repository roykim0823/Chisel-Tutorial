# Chapter 2 — Basic Components

With the toolchain working and a first module generated in Chapter 1, this
chapter introduces the core vocabulary of digital design in Chisel: the data
types and constants, the operators that form combinational logic, the
multiplexer, and the register that gives a circuit memory. It finishes with
`Bundle` and `Vec` for grouping signals — enough to build a real processor
register file. Every later chapter is assembled from these pieces.

*Conventions: every file path is relative to `tutorial/ch02-basic-components/`,
and every command is run from that folder.*

Digital systems use **binary signals**: every wire carries one of two values.
We call them 0/1, low/high, false/true, or deasserted/asserted — all the same
thing. This chapter introduces the two building blocks from which *every*
digital circuit is made:

1. **Combinational logic** — outputs depend only on the current inputs
   (gates, multiplexers, arithmetic).
2. **Registers** (state) — outputs depend on inputs *and* the past
   (flip-flops that update on the clock edge).

Four source files anchor this chapter, and every Chisel snippet below is a
verbatim excerpt from one of them:

| File | Holds |
|------|-------|
| `src/main/scala/Logic.scala` | the types, constants, operators, `Wire`, bit extraction, and the `Mux` (§2.1–2.3, §2.7) |
| `src/main/scala/Registers.scala` | the register forms, the counter, and the `WireDefault`/`RegInit` best practices — classes `Registers` and `Defaults` (§2.4, §2.8) |
| `src/main/scala/Structure.scala` | the `Bundle` and `Vec` constructs — the `Channel` and `BundleVec` bundles and the `Structure` module (§2.5, §2.6) |
| `src/main/scala/RegisterFile.scala` | the register file itself: registers + `Vec` + `Bundle` + `Option` (§2.6) |

Only two snippets in the whole chapter are **not** project code, and both say so
where they appear: the illegal partial assignment in §2.7 (it cannot compile by
definition) and the FPGA port in Exercise 4 (it belongs to a different project).

> **Quick start — jump to [§2.11](#211-build-run-and-check) to build, run, and
> test right now**, then come back for the concepts. Or read top-to-bottom.

---

## 2.1 Chisel types and constants

Chisel has three bit-vector data types:

| Type   | Meaning |
|--------|---------|
| `Bits` | a raw vector of bits (few operations; rarely used directly) |
| `UInt` | the same bits interpreted as an **unsigned** integer |
| `SInt` | the same bits interpreted as a **signed** integer (two's complement) |

Declaring a type with a width (the `.W` suffix makes a Scala `Int` into a
Chisel width):

`src/main/scala/Logic.scala`
```scala
Bits(8.W)   // 8-bit raw vector
UInt(8.W)   // 8-bit unsigned integer
SInt(10.W)  // 10-bit signed integer
```

A width need not be a literal — you can cast a **Scala** `Int` variable `n` to a
Chisel `Width` with `.W` and use it wherever a width is expected. This is the
first hint of Chisel's generator power (a width computed at build time):

`src/main/scala/Logic.scala`
```scala
n.W
Bits(n.W)
```

**Constants** are made by converting a Scala number with `.U` (unsigned),
`.S` (signed), or `.B` (Bool):

`src/main/scala/Logic.scala`
```scala
0.U          // a UInt constant 0
-3.S         // an SInt constant -3
3.U(4.W)     // a 4-bit constant of value 3
```

> If the `3.U` / `4.W` notation looks odd, read it as a typed integer constant —
> like `3L` for a `long` in C, Java, and Scala.

> **Pitfall (from the book):** to give a constant a width you must use `.W`.
> Writing `1.U(32)` does **not** make a 32-bit constant — `(32)` is read as
> *bit-extraction at index 32*, giving a single 0 bit. Always write `1.U(32.W)`.

Chisel benefits from **Scala's type inference**, and in many places the type — and
the bit width — can be left out for Chisel to infer. That is why a Chisel
description is often more concise and readable than the equivalent VHDL or
Verilog. (Even so, spelling out widths at creation is good practice; see §2.8.)

*Scala note — `new` & type inference → [§1.2.2](../ch01-introduction/README.md#122-types-are-inferred-but-you-can-state-them).*

Non-decimal constants use a string prefixed by `h` (hex), `o` (octal), or `b`
(binary); underscores group digits and are ignored:

`src/main/scala/Logic.scala`
```scala
"hff".U         // 255 in hex
"o377".U        // 255 in octal
"b1111_1111".U  // 255 in binary
```

Characters (ASCII) can be constants too, and `Bool` has `true.B` / `false.B`:

`src/main/scala/Logic.scala`
```scala
val aChar = 'A'.U   // 65
Bool(); true.B; false.B
```

*Scala note — literals (char `'A'`, hex/binary strings, no octal, `L` suffix) → [§1.2.3](../ch01-introduction/README.md#123-literals).*

All of the constructs above live together in `src/main/scala/Logic.scala` —
open it and match each line to the notes here.

---

## 2.2 Combinational circuits

Chisel uses the familiar C/Java/Scala operators. This one line builds an AND
gate feeding an OR gate:

`src/main/scala/Logic.scala`
```scala
val logic = (a & b) | c
```

<p align="center">
  <img src="figures/logic.png" alt="Logic for the expression (a & b) | c" width="460">
</p>

***Figure 2.1** — Logic for the expression `(a & b) | c`. The Chisel expression
and the schematic are one and the same: `&` builds the AND gate, `|` builds the
OR gate. The wires may carry a single bit or a whole vector of bits — the same
code works for both.*

You did not declare `logic`'s type or width — Chisel **infers** both from the
expression. Remember: this creates *gates*, it does not compute a number.

**Bitwise and arithmetic operators.** Chapter 2's full set, each one built as a
named `val` so the module holds one instance of every operator:

`src/main/scala/Logic.scala`
```scala
val a_and_b = a & b // bitwise and of a and b
val a_or_b = a | b  // bitwise or of a and b
val a_xor_b = a ^ b // bitwise xor of a and b
val a_not = ~a      // bitwise negation of a
```

`src/main/scala/Logic.scala`
```scala
val a_plus_b = a + b  // addition of a and b
val a_minus_b = a - b // subtraction of b from a
val neg_a = -a        // negate a
val a_mul_b = a * b   // multiplication of a and b
val a_div_b = a / b   // division of a by b
val a_mod_b = a % b   // modulo operation of a by b
```

Width rules: add/subtract → max of the two widths; multiply → sum of widths;
divide/modulo → width of the numerator.

**Full operator and function tables** (from the book, for reference):

| Operator | Description | Types |
|----------|-------------|-------|
| `* / %` | multiply, divide, modulo | UInt, SInt |
| `+ -` | add, subtract | UInt, SInt |
| `=== =/=` | equal, not-equal | UInt, SInt → Bool |
| `> >= < <=` | comparison | UInt, SInt → Bool |
| `<< >>` | shift left / right (SInt sign-extends) | UInt, SInt |
| `~` | NOT | UInt, SInt, Bool |
| `& \| ^` | AND, OR, XOR | UInt, SInt, Bool |
| `!` | logical NOT | Bool |
| `&& \|\|` | logical AND, OR | Bool |

> **Note** equality is `===` (three equals) and inequality is `=/=`, because
> Scala already uses `==`/`!=` for object comparison.

> **Operator precedence.** Chisel's operator precedence is a side effect of how
> the hardware tree is built as the Scala operators execute, so it follows
> **Scala's** precedence — which is *similar but not identical* to Java/C (and
> different again from VHDL, where all logic operators share one precedence and
> evaluate left-to-right). When in doubt, **use parentheses.** See
> [Scala Notes §J.1](../ch01-introduction/README.md#129-operators-are-method-calls) —
> Chisel's operators are ordinary Scala method calls used infix.

| Function | Description | Types |
|----------|-------------|-------|
| `v.andR v.orR v.xorR` | AND/OR/XOR reduction of all bits | UInt, SInt → Bool |
| `v(n)` | extract a single bit | UInt, SInt |
| `v(end, start)` | extract a bit field | UInt, SInt |
| `Fill(n, v)` | replicate a bit string n times | UInt, SInt |
| `a ## b` | concatenate | UInt, SInt |
| `Cat(a, b, ...)` | concatenate | UInt, SInt |

### Wire and the `:=` update operator

A signal can be declared as a `Wire` first, then driven with `:=`:

`src/main/scala/Logic.scala`
```scala
val w = Wire(UInt())
w := a & b
```

### Bit extraction, sub-fields, concatenation

`src/main/scala/Logic.scala`
```scala
val sign = x(31)             // single bit at index 31
val lowByte = largeWord(7, 0) // bits 7 down to 0 (a sub-field)
val word = highByte ## lowByte // concatenate: high byte then low byte
```

---

## 2.3 Multiplexer

A **multiplexer** ("mux") selects one of several inputs. In its most basic
form it chooses between two.

<p align="center">
  <img src="figures/mux.png" alt="A basic 2:1 multiplexer" width="240">
</p>

***Figure 2.2** — A basic 2:1 multiplexer. The select signal `sel` chooses which
input reaches the output `y`: when `sel` is true the `T` input (`a`) is passed,
otherwise the `F` input (`b`).*

The 2:1 mux is so common that Chisel provides `Mux`:

`src/main/scala/Logic.scala`
```scala
val sel = b === c
val result = Mux(sel, a, b)   // sel==true → a, else → b
```

`sel` must be a `Bool`; `a` and `b` can be any Chisel type as long as they
match. With logic, arithmetic, and a mux you can express *any* combinational
circuit (Chapter 4 adds nicer abstractions like `when`/`switch`).

---

## 2.4 Registers (state)

A **register** is a group of D flip-flops. It is implicitly connected to the
global `clock` and updates on the rising edge. If you give it an initial value,
it also gets a synchronous reset to that value.

<p align="center">
  <img src="figures/register-reset-0.png" alt="A D flip-flop register with a synchronous reset to 0" width="320">
</p>

***Figure 2.3** — A `RegInit(0.U)` register. Input `d` and the reset value `0`
feed a multiplexer selected by `reset`; its output goes to the D input of the
flip-flop, whose output `q` updates on the rising edge of `clock`. Chisel wires
`clock` and `reset` implicitly — you never declare them.*

The standard forms, all three in `Registers`:

`src/main/scala/Registers.scala`
```scala
val reg = RegInit(0.U(8.W)) // 8-bit register, resets to 0
reg := d                    // drive its input; read it just by name (reg)

val r2 = RegNext(d)         // register whose input is d (no reset value)
val r3 = RegNext(d, 0.U)    // input d, resets to 0
```

**Naming convention:** postfix register names with `Reg` (e.g. `cntReg`,
`blkReg` from Chapter 1) so readers can tell state from combinational wires.
Following Java/Scala custom, use **CamelCase** for multi-word identifiers, with
functions and variables starting lower-case and classes/types (a `Module` name,
say) upper-case. Names are otherwise up to you — pick descriptive ones — but a
handful of words are **reserved** (listed in the book's *Reserved Keywords*
appendix).

### Counting

Counting clock cycles is how you measure time in hardware. A counter that runs
0→9 and wraps:

`src/main/scala/Registers.scala`
```scala
val cntReg = RegInit(0.U(8.W))
cntReg := cntReg + 1.U
when(cntReg === 9.U) {
  cntReg := 0.U
}
```

(You saw exactly this pattern drive the blinking LED in Chapter 1.)

---

## 2.5 Structure with `Bundle` and `Vec`

Chisel groups related signals two ways:

- **`Bundle`** — named fields of possibly different types (like a C `struct` or
  VHDL `record`). You define one by extending `Bundle`. Every `io = IO(new
  Bundle { ... })` you have written *is* a bundle.
- **`Vec`** — an indexable collection of the **same** type (like an array).
  A `Vec` serves three purposes: (1) dynamic (hardware) indexing = a multiplexer;
  (2) register files (multiplexing the read, generating the write-enable); and
  (3) parameterizing the number of ports of a `Module`. For collections of
  *generator* data (or hardware elements you don't index in hardware) use a Scala
  `Seq` instead.

Both create **new, user-defined Chisel types** and can be nested arbitrarily.

Everything in this section is real code in `src/main/scala/Structure.scala`: two
bundle types (`Channel`, `BundleVec`) and a `Structure` module that builds each
construct and routes it to an output port so a test can see it. Like
`Logic.scala`, it starts by aliasing its ports to bare names (`val x = io.x`,
`val sel = io.sel`, …), which is why the snippets below read `m(0) := x` rather
than `m(0) := io.x`.

### Bundle

Define a bundle by extending `Bundle` and listing its fields as `val`s:

`src/main/scala/Structure.scala`
```scala
class Channel() extends Bundle {
  val data = UInt(32.W)
  val valid = Bool()
}
```

Create one with `new`, wrap it in a `Wire`, and access fields with dot notation:

`src/main/scala/Structure.scala`
```scala
val ch = Wire(new Channel())
ch.data := 123.U
ch.valid := true.B

val b = ch.valid
```

A bundle can also be referenced as a whole:

`src/main/scala/Structure.scala`
```scala
val channel = ch
```

### Vec

A combinational `Vec` is created with a size and an element type, and **wrapped
in a `Wire`**; individual elements are accessed with `(index)`:

`src/main/scala/Structure.scala`
```scala
val v = Wire(Vec(3, UInt(4.W)))

v(0) := 1.U
v(1) := 3.U
v(2) := 5.U

val index = 1.U(2.W)
val a = v(index) // dynamic index = a multiplexer
```

A combinational `Vec` indexed by a signal is **literally a multiplexer**. For
example, connecting three wires `x`, `y`, `z` into a `Vec` and reading it with a
`select` signal picks one of them onto `muxOut`:

`src/main/scala/Structure.scala`
```scala
val m = Wire(Vec(3, UInt(8.W)))
m(0) := x
m(1) := y
m(2) := z
val muxOut = m(select)
```

<p align="center">
  <img src="figures/vec-mux.png" alt="A vector wrapped in a Wire is just a multiplexer" width="300">
</p>

***Figure 2.4** — A `Vec` wrapped in a `Wire` is just a multiplexer. The three
inputs `x`, `y`, `z` become the mux inputs `0`, `1`, `2`; `select` chooses which
one drives `muxOut`.*

Like `WireDefault`, **`VecInit`** gives a `Vec` default values. The following is
a 3:1 multiplexer with three constant defaults (the width, 3 bits, is set on the
first constant), which a `when` can overwrite (three more 2:1 muxes); the last
line selects one input. `VecInit` **already returns hardware**, so — unlike a
plain `Vec` — it need not be wrapped in a `Wire`:

`src/main/scala/Structure.scala`
```scala
val defVec = VecInit(1.U(3.W), 2.U, 3.U)
when(cond) {
  defVec(0) := 4.U
  defVec(1) := 5.U
  defVec(2) := 6.U
}
val vecOut = defVec(sel)
```

`VecInit` can be fed **signals**, not just constants — here wires `d`, `e`, `f`
drive the three `Vec` inputs:

`src/main/scala/Structure.scala`
```scala
val defVecSig = VecInit(d, e, f)
val vecOutSig = defVecSig(sel)
```

Wrapping a `Vec` in a **register** instead gives an array of registers with
one write port and one read port:

<p align="center">
  <img src="figures/vec-reg.png" alt="A vector of registers" width="640">
</p>

***Figure 2.5** — A vector of (three) registers. The write index `wrIdx` drives a
decoder that enables exactly one register to capture `din`; the read index
`rdIdx` selects one register's output onto `dout` through a multiplexer. This is
exactly the structure the register file below scales up to 32 entries.*

Both concepts come together in the register file below, which **is** a file in
this project.

### Combining `Bundle` and `Vec`

Bundles and vectors mix freely. A **`Vec` of a `Bundle`** type takes the bundle
as its element prototype:

`src/main/scala/Structure.scala`
```scala
val vecBundle = Wire(Vec(8, new Channel()))
```

A **`Bundle` containing a `Vec`** field:

`src/main/scala/Structure.scala`
```scala
class BundleVec extends Bundle {
  val field = UInt(8.W)
  val vector = Vec(4, UInt(8.W))
}
```

For a **register of a bundle type that needs a reset value**, first build a
`Wire` of the bundle, set its fields, then pass it to `RegInit`:

`src/main/scala/Structure.scala`
```scala
val initVal = Wire(new Channel())

initVal.data := 0.U
initVal.valid := false.B

val channelReg = RegInit(initVal)
```

Combining `Bundle`s and `Vec`s lets you define your own powerful data-structure
abstractions.

---

## 2.6 A register file (registers + `Vec` + `Bundle`)

A processor's register file is a classic use of a `Vec` of registers with
dynamic read/write addressing. This one has 32 registers, each 32 bits — as in
a 32-bit RISC-V.

`src/main/scala/RegisterFile.scala`
```scala
import chisel3._

class RegisterFile(debug: Boolean) extends Module {
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rd = Input(UInt(5.W))
    val wrData = Input(UInt(32.W))
    val wrEna = Input(Bool())
    val rs1Val = Output(UInt(32.W))
    val rs2Val = Output(UInt(32.W))
    val dbgPort = if (debug)
      Some(Output(Vec(32, UInt(32.W)))) else None
  })
  val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  io.rs1Val := regfile(io.rs1)
  io.rs2Val := regfile(io.rs2)
  when(io.wrEna) {
    regfile(io.rd) := io.wrData
  }
  if (debug) {
    io.dbgPort.get := regfile
  }
}
```

*Scala note — `if` as an expression → [§1.2.4](../ch01-introduction/README.md#124-everything-is-an-expression); `Seq`/`List`/`Array` builders → [§1.2.11](../ch01-introduction/README.md#1211-collections-seq-list-array); `Option`/`Some`/`None` → [§F.1](../SCALA-NOTES.md#f1-option--some--none--get).*

Read this carefully — it packs several ideas:

- **`Vec` of registers with reset:**
  `RegInit(VecInit(Seq.fill(32)(0.U(32.W))))`. `Seq.fill(32)(...)` is a *Scala*
  sequence of 32 zero constants (generator data), `VecInit` turns it into a
  Chisel `Vec`, and `RegInit` makes that the reset value of 32 registers.
- **Dynamic read = mux:** `regfile(io.rs1)` reads whichever register `rs1`
  addresses — that index becomes a 32:1 multiplexer in hardware.
- **Dynamic write = decoder + enables:** `when(io.wrEna){ regfile(io.rd) := ...}`
  writes the addressed register only when write-enable is high.
- **Scala vs. Chisel `if`/`when`:** `when` is *Chisel* — it builds a hardware
  conditional (a mux/enable) that exists every cycle. `if (debug)` is *Scala* —
  it runs once at build time and decides *whether the hardware even exists*.
- **Optional port via `Option`:** `dbgPort` is `Some(...)` only when
  `debug` is true, else `None`. This is a *generator* feature — the same class
  produces two different circuits (with or without a debug port) depending on a
  constructor argument. This is the kind of thing Verilog/VHDL cannot do
  cleanly and where Chisel shines.

The `Seq.fill(32)(0.U(32.W))` above resets *every* register to the same value.
When you instead want **distinct reset values per register**, list them in the
`VecInit` directly (and you can still connect each element's input separately):

`src/main/scala/Structure.scala`
```scala
val initReg = RegInit(VecInit(0.U(3.W), 1.U, 2.U))
val resetVal = initReg(sel)
initReg(0) := d
initReg(1) := e
initReg(2) := f
```

---

## 2.7 Pitfall: no partial assignment (and the workaround)

In Chisel 3+ you may **not** assign to a slice of a signal. This is illegal and
fails during elaboration:

*illustrative — DOES NOT COMPILE*
```scala
val assignWord = Wire(UInt(16.W))
assignWord(7, 0)  := lowByte   // ✗ partial assignment not allowed
assignWord(15, 8) := highByte  // ✗
```

**Workaround A — a local `Bundle`, then `asUInt`** (present in `Logic.scala`):

`src/main/scala/Logic.scala`
```scala
val assignWord = Wire(UInt(16.W))

class Split extends Bundle {
  val high = UInt(8.W)
  val low = UInt(8.W)
}

val split = Wire(new Split())
split.low := lowByte
split.high := highByte
assignWord := split.asUInt
```

**Workaround B — a `Vec` of `Bool`, then `asUInt`** (also in `Logic.scala`):

`src/main/scala/Logic.scala`
```scala
val vecResult = Wire(Vec(4, Bool()))
vecResult(0) := data(0)
vecResult(1) := data(1)
vecResult(2) := data(2)
vecResult(3) := data(3)
val uintResult = vecResult.asUInt
```

---

## 2.8 `Wire`, `Reg`, `IO`, and `=` vs. `:=`

`UInt`/`SInt`/`Bits` are just *types* — by themselves they are not hardware.
They become hardware only when wrapped:

- `Wire(...)` → combinational logic
- `Reg(...)` / `RegInit(...)` → a register (flip-flops)
- `IO(...)` → a module's ports

You **name** a hardware object with Scala's `=`, and you **drive** an existing
object with Chisel's `:=`:

`src/main/scala/Logic.scala`
```scala
val w = Wire(UInt())
w := a & b
```

The first line uses Scala's `=` to *create and name* the hardware; the second
uses Chisel's `:=` to *drive a value onto* hardware that already exists. (This
`Wire` leaves its width to inference — the third best practice below says to
spell it out, as `Defaults` does.)

(Scala also has mutable `var`, but it is useless for describing hardware — you
name hardware once with `val` and drive it with `:=`.)

Best practices the book stresses:

- Give combinational `Wire`s a **default value** so they are assigned on every
  path (an unassigned combinational signal would be a latch, which Chisel
  rejects). `WireDefault` folds the default into the declaration:

  `src/main/scala/Registers.scala`
  ```scala
  val number = WireDefault(10.U(4.W))
  ```
- Give registers a **reset value** (`RegInit`) so simulation/verification is
  deterministic:

  `src/main/scala/Registers.scala`
  ```scala
  val reg = RegInit(0.S(8.W))
  ```
  (Leaving a register undefined at reset can save some load on the reset wire,
  but known reset values simplify testing and verification.)
- Specify **bit widths** at creation even though Chisel can infer them.

---

## 2.9 Chisel *generates* hardware (the mental model)

This is the single most important idea in the book. Chisel code *looks* like
Java/C, but it does not execute statement-by-statement to compute a result.
Instead, when your Scala program runs, each Chisel statement **adds a node** to
a graph of hardware components and wires them together. That graph is the
circuit; Chisel emits it as SystemVerilog. Once built, **all of it runs in
parallel, every clock cycle.**

So when you read Chisel, don't imagine a program running — **imagine the
gates and flip-flops being drawn on a page.** Each `Wire`/`Reg`/operator is a
piece of that drawing.

---

## 2.10 The `Logic` module as a whole

`src/main/scala/Logic.scala` collects the combinational examples above into one
module. Its ports let a test observe each construct's result:

`src/main/scala/Logic.scala` (the ports)
```scala
val io = IO(new Bundle {
  val a = Input(UInt(1.W))
  val b = Input(UInt(1.W))
  val c = Input(UInt(1.W))
  val out    = Output(UInt(1.W))   // (a & b) | c
  val cat    = Output(UInt(16.W))  // highByte ## lowByte
  val ch     = Output(UInt(8.W))   // 'A'.U = 65
  val word   = Output(UInt(16.W))  // Bundle-based partial assignment
  val result = Output(UInt(4.W))   // Vec[Bool]-based assembly
})
```

Generating it produces exactly these ports in SystemVerilog (see §2.11):

```systemverilog
module Logic(
  input         clock, reset, io_a, io_b, io_c,
  output        io_out,
  output [15:0] io_cat,
  output [7:0]  io_ch,
  output [15:0] io_word,
  output [3:0]  io_result
);
```

---

## 2.11 Build, run, and check

Three things you can do in this project. Run them from this folder.

### (a) Run the test bench — the fastest way to *check* the hardware

```
$ sbt test
```

Expected output:

```
[info] StructureTest:
[info] RegistersTest:
[info] Structure
[info] - should mux with a Vec and default with a VecInit
[info] Registers
[info] - should delay its input by one cycle and count 0 to 9
[info] LogicTest:
[info] Logic
[info] - should pass
[info] RegisterFileTest:
[info] RegisterFile
[info] - should have a debug port
[info] RegisterFile
[info] - should work without the debug port
[info] Run completed in 1 second, 219 milliseconds.
[info] Total number of tests run: 5
[info] Suites: completed 4, aborted 0
[info] Tests: succeeded 5, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[success] Total time: 4 s
```

Five tests in four suites, all green. (`StructureTest` and `RegistersTest` print
their headers first because sbt runs suites in parallel — the order varies
between runs and means nothing.)

### (b) Generate the SystemVerilog — to *see* the hardware

`src/main/scala/Generate.scala`
```scala
import chisel3._

object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Logic(), opts)
  emitVerilog(new RegisterFile(true), opts)
  emitVerilog(new Structure(), opts)
  emitVerilog(new Registers(), opts)
  emitVerilog(new Defaults(), opts)
}
```

```
$ sbt "runMain Generate"
```

This writes five files into `generated/`: **`Logic.sv`**,
**`RegisterFile.sv`**, **`Structure.sv`**, **`Registers.sv`**, and
**`Defaults.sv`**. Open them and match the ports to the `io` bundles. Three
things to look for:

- In `RegisterFile.sv`, 32 outputs `io_dbgPort_0 … io_dbgPort_31` — the debug
  `Vec` flattened into individual ports.
- In `Structure.sv`, no `Channel` or `BundleVec` type anywhere. A `Bundle` is a
  *type*, not hardware: it gets no `.sv` of its own and survives only as
  flattened signals. The bundle register `channelReg` becomes one signal,
  `reg [31:0] channelReg_data` — and its `valid` field is missing entirely,
  because no output reads it.
- In `Registers.sv`, the counter as a clocked process. Note it is
  `always @(posedge clock)`, **not** `always_ff` — firtool does not emit
  `always_ff`, whatever hand-written SystemVerilog style guides recommend.
- `Defaults.sv` is almost empty: both ports come out as constants
  (`assign io_number = 4'hA;` and `assign io_reg = 8'h0;`) and the register is
  gone, because nothing drives it. §2.9 again — hardware nothing depends on is
  not built.

### (c) Understand the test bench

Testing gets a chapter of its own next
([Chapter 3](../ch03-build-and-testing/README.md)) and a deeper one at the end
([Chapter 13](../ch13-debugging-testing-verification/README.md)). What follows is
the short version — enough to *read, run, and debug* every test bench from here
through Chapter 12, starting with `src/test/scala/LogicTest.scala`:

`src/test/scala/LogicTest.scala`
```scala
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LogicTest extends AnyFlatSpec with ChiselScalatestTester {
  "Logic" should "pass" in {
    test(new Logic) { dut =>
      dut.io.a.poke(1.U)
      dut.io.b.poke(0.U)
      dut.io.c.poke(1.U)
      dut.clock.step()
      dut.io.out.expect(1.U)      // (a & b) | c = (1 & 0) | 1 = 1
      dut.io.cat.expect("hff01".U) // highByte ## lowByte = 0xff ## 0x01
      dut.io.ch.expect(65.U)       // 'A' in ASCII
      dut.io.word.expect("hff01".U)
      dut.io.result.expect(5.U)    // the 4 bits of 5.U reassembled
    }
  }
}
```

- **`poke`** drives a value onto an input port.
- **`step()`** advances the simulated clock by one cycle.
- **`expect`** asserts an output equals a value; a mismatch fails the test.

**Which of those words are Chisel?** None of them is new *syntax* — every line
above is an ordinary Scala method call, and the vocabulary comes from four
different places:

| In a test bench | Provided by | Arrives with |
|-----------------|-------------|--------------|
| `class … extends … with …`, the `{ dut => … }` block | the **Scala language** itself | — |
| `AnyFlatSpec`, `"Logic" should "pass" in { … }` | **ScalaTest**, a general-purpose Scala test library | `org.scalatest.flatspec.AnyFlatSpec` |
| `ChiselScalatestTester`, `test(…)`, `poke`, `expect`, `clock.step()` | **chiseltest**, a separate library that drives a simulator | `chiseltest._` |
| `.U`, `.B`, `io`, the module being tested | **chisel3** | `chisel3._` |

Two consequences worth absorbing now, because they remove most of the mystery:

- `should` and `in` are **methods, not keywords**. `"Logic" should "pass"` is
  `"Logic".should("pass")` — Scala's infix notation just lets you drop the dot
  and the parentheses. Likewise `poke` and `expect` are methods chiseltest
  *adds* to Chisel ports; plain `chisel3` has no `poke` at all.
- `dut` is a **name you picked**. `test(new Logic) { dut => … }` hands your block
  a live, running instance: chiseltest elaborates the module, starts a simulator,
  loans you the instance, and tears it down when the block ends. Later chapters
  call it `c` instead; nothing changes.

*Scala note — the `{ dut => … }` block is a function literal →
[§D.1](../SCALA-NOTES.md#d1-function-literals-lambdas-and-the--arrow); the
`should` / `in` chain → [§I](../SCALA-NOTES.md#i-scalatest-dsl-reads-like-english-is-really-scala).*

`src/test/scala/RegisterFileTest.scala` does the same for the register file:
it writes `123` to register 4, steps the clock, then checks that reading
register 4 (`rs1Val`) returns `123` **and** that the debug port shows `123` at
index 4. A second test builds the module *without* the debug port
(`new RegisterFile(false)`) and confirms it still works — demonstrating the
`Option`-based generator from §2.6.

**Running less than everything.** `sbt test` runs both suites here, but later
projects have many (Chapter 12 has eight), and while debugging you want one:

```
$ sbt "testOnly LogicTest"
[info] LogicTest:
[info] Logic
[info] - should pass
[info] Run completed in 1 second, 1 millisecond.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

`-z` narrows further, to individual tests *inside* a suite. It matches any
substring of a test's full name — the subject line plus the clause after
`should`:

```
$ sbt 'testOnly RegisterFileTest -- -z "without the debug port"'
[info] RegisterFileTest:
[info] RegisterFile
[info] RegisterFile
[info] - should work without the debug port
[info] Run completed in 782 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

The bare `RegisterFile` line with nothing under it is the *other* test in the
suite, which the filter skipped. One caveat: a filter that matches **nothing**
is not an error — it reports `Total number of tests run: 0` and still exits
green, so read that count rather than the `[success]`. The full set of filters
(`-t`, tags, packages, suite globs) is in
[§13.2.2](../ch13-debugging-testing-verification/README.md#1322-selecting-tests-with-tags--and-the-other-filters).

**Reading a failure.** Break the design on purpose — change line 51 of
`src/main/scala/Logic.scala` from `val logic = (a & b) | c` to
`val logic = a & b & c` — and the bench says:

```
[info] LogicTest:
[info] Logic
[info] - should pass *** FAILED ***
[info]   In step 1: io_out=0 (0x0) did not equal expected=1 (0x1) at (LogicTest.scala:12) (LogicTest.scala:12)
[info] Tests: succeeded 0, failed 1, canceled 0, ignored 0, pending 0
[info] *** 1 TEST FAILED ***
```

Four pieces of information are packed into that one line, and they are the whole
debugging loop for the next ten chapters:

- **`In step 1`** — how many `clock.step()` calls had happened when the check
  ran, so you know *which cycle* to look at.
- **`io_out`** — the port's *generated* name. Chisel flattens `io.out` to
  `io_out`, which is exactly the name to search for in `Logic.sv` or in a
  waveform.
- **`=0 (0x0) did not equal expected=1 (0x1)`** — actual first, then expected,
  each in decimal and hex.
- **`(LogicTest.scala:12)`** — the failing `expect` line, *not* the `poke` that
  caused it. It appears twice because ScalaTest appends its own location to the
  one chiseltest already reported.

**What Chapters 3 and 13 add.** Come back to these when you need them; nothing
below is required to follow Chapters 4–12.

| Topic | Where |
|-------|-------|
| ScalaTest and ChiselTest properly, from the ground up | [§3.2.1](../ch03-build-and-testing/README.md#321-scalatest-the-foundation), [§3.2.2](../ch03-build-and-testing/README.md#322-chiseltest) |
| VCD waveforms (`WriteVcdAnnotation`) and `printf` debugging | [§3.2.3](../ch03-build-and-testing/README.md#323-waveforms), [§3.2.4](../ch03-build-and-testing/README.md#324-printf-debugging) |
| Factoring a bench into a `def` or a reusable `trait` | [§13.2.1](../ch13-debugging-testing-verification/README.md#1321-use-functions) |
| Tags, and every way to select which tests run | [§13.2.2](../ch13-debugging-testing-verification/README.md#1322-selecting-tests-with-tags--and-the-other-filters) |
| Reaching *internal* signals with `BoringUtils` | [§13.2.3](../ch13-debugging-testing-verification/README.md#1323-accessing-internal-signals-with-boringutils) |
| Concurrent stimulus with `fork`/`join` | [§13.2.4](../ch13-debugging-testing-verification/README.md#1324-multithreaded-testing-forkjoin) |
| Hardware `assert` and formal verification | [§13.3](../ch13-debugging-testing-verification/README.md#133-assertions), [§13.4](../ch13-debugging-testing-verification/README.md#134-formal-verification) |

---

## 2.12 Recap

- Every circuit is combinational logic + registers.
- `UInt`/`SInt`/`Bits` become hardware only inside `Wire`, `Reg`, or `IO`.
- `=` names hardware; `:=` drives it.
- `Mux` selects; a `Vec` indexed by a signal is a mux; a `Vec` of `Reg` is a
  register array.
- `Bundle` groups named signals; `Option` + Scala `if` let one module generate
  different hardware (the debug port).
- Chisel **builds** a parallel hardware graph — it does not run like software.
- A test bench is three libraries stacked on Scala: ScalaTest supplies
  `should`/`in`, chiseltest supplies `test`/`poke`/`expect`/`step` — none of it
  is Chisel syntax (§2.11(c)).

---

## 2.13 Exercises

1. **Break a test on purpose.** In `src/main/scala/Logic.scala` change line 51
   from `val logic = (a & b) | c` to `val logic = a & b & c`, run `sbt test`, and
   read the failure message (decoded in [§2.11(c)](#c-understand-the-test-bench)).
   Then revert.

   Now try a break that *doesn't* fail: swap the mux inputs on line 84 to
   `Mux(sel, b, a)` and run `sbt test` again — still green. Why? `val result` on
   that line is never connected to a port, so no output depends on it and no test
   can see it. Confirm it with `sbt "runMain Generate"`: `Logic.sv` has no mux at
   all, and `io_result` (driven from the *other* `result` at line 108) is the
   constant `assign io_result = 4'h5;`. Dead hardware is optimized away — the
   §2.9 mental model in action, and a reminder that a passing test only covers
   what its ports can observe.
2. **Add an XOR output.** Add `val xor = Output(UInt(1.W))` to `Logic`'s `io`,
   drive it with `io.xor := a ^ b`, regenerate with `sbt "runMain Generate"`,
   and find the new port in `Logic.sv`. (Optionally add an `expect` for it in
   `LogicTest.scala`.)
3. **Shrink the register file.** Change `RegisterFile` from 32 to 8 registers
   (the `Seq.fill(32)` and the `Vec(32, …)`), regenerate, and confirm
   `RegisterFile.sv` now has `io_dbgPort_0 … io_dbgPort_7`.
4. **From the book (FPGA):** take the Chapter 1 blinking-LED project (from
   `chisel-examples`), copy it to a new folder, and add switch inputs to its `io`
   bundle:

   ```scala
   val sw = Input(UInt(2.W))
   ```
   *illustrative*

   You must also **assign the FPGA pins** for those switches — see the pin
   assignments in a Quartus project file such as the DE2-115 board's
   [`alu.qsf`](https://github.com/schoeberl/chisel-examples/blob/master/quartus/altde2-115/alu.qsf)
   (this can also be done in the tool's GUI). Then work up in three steps:

   1. **Prove inputs work first.** Drop *all* blinking logic and wire **one
      switch straight to the LED**. Compile, configure the board: can you switch
      the LED on and off? If yes, inputs are working; if not, debug the pin
      configuration before going further.
   2. **A combinational function.** Use **two switches**, `AND` them onto the
      LED, then **change the function** (try OR, XOR).
   3. **A multiplexer.** Use **three switches**: one as the select signal and the
      other two as the inputs of a 2:1 mux driving the LED.

   No board? Express each step here and inspect the generated SystemVerilog
   instead. (Chapter 3 introduces a testing framework so you can check circuits
   *without* an FPGA and physical switches.)

Back to the **[tutorial index](../README.md)**.
Next: **[Chapter 3 — Build Process and Testing](../ch03-build-and-testing/README.md)**.
