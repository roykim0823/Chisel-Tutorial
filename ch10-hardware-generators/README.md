# Chapter 10 — Hardware Generators

This is Chisel's superpower: because a Chisel description *is* a Scala program,
the full power of Scala runs at hardware-construction time. You don't write a
separate script to emit VHDL — the generator and the hardware are the same code.
This chapter is a tour of generator techniques: lightweight **functions** that
return hardware, **generating combinational logic/ROM tables**, configuration
with **parameters / case classes / type parameters**, **inheritance**, and
**functional programming** (`reduce`/`reduceTree`).

*Conventions: every file path is relative to
`tutorial/ch10-hardware-generators/`, and every command is run from that folder.
This chapter has no figures.*

## What's in this project

```
ch10-hardware-generators/
├── build.sbt · project/build.properties
├── src/main/scala/
│   ├── functional.scala   functions returning hardware; reduce/reduceTree; min-search
│   ├── BcdTable.scala      binary -> BCD table generated with a Scala loop
│   ├── GenHardware.scala   VecInit ROM tables (a string, a square table)
│   ├── ParamAdder.scala    width parameter + two instances
│   ├── Config.scala         case classes for parameters (+ ConfigDemo)
│   ├── ParamFunc.scala      a mux parameterized by a Chisel TYPE
│   ├── Ticker.scala         abstract base + three implementations (inheritance)
│   └── Generate.scala
└── src/test/scala/  (one test per topic)
```

---

## 10.1 A little Scala

Two variable kinds: `val` (immutable — used to *name* hardware) and `var`
(mutable — used only when *generating* hardware, never to name a component).
Key building blocks for generators: `for` loops, `if`/`else` (evaluated at
generation time — they choose *what hardware to build*, they are **not**
multiplexers), **tuples** (`(a, b)`, accessed `._1`/`._2`, for returning
multiple values), and the **`Seq`** collection.

---

## 10.2 Lightweight components with functions

A module has boilerplate; a Scala **function that returns hardware** is a
lighter alternative. It's a real generator — calling it *builds* hardware. To
return more than one value, return a **tuple**:

`src/main/scala/functional.scala`
```scala
def compare(a: UInt, b: UInt) = {
  val equ = a === b
  val gt = a > b
  (equ, gt)                       // return a tuple
}

val (equ, gt) = compare(io.a, io.b) // decompose it
```

Functions used across modules belong in a Scala `object` of utilities.

---

## 10.3 Generating combinational logic (ROM tables)

A truth table is combinational logic — a ROM addressed by its input. Build one
with **`VecInit`** and ordinary Scala:

`src/main/scala/GenHardware.scala`
```scala
val msg = "Hello World!"
val text = VecInit(msg.map(_.U))   // a String is a Seq[Char] -> a byte ROM
val squareROM = VecInit(0.U, 1.U, 4.U, 9.U, 16.U, 25.U)
val square = squareROM(n)
```

The classic example is **binary → BCD** conversion. In VHDL you'd generate this
table with an external script; in Chisel a Scala loop builds it inline:

`src/main/scala/BcdTable.scala`
```scala
val table = Wire(Vec(100, UInt(8.W)))
for (i <- 0 until 100) {
  table(i) := (((i / 10) << 4) + i % 10).U  // tens nibble | ones nibble
}
io.data := table(io.address)
```

> The same idea generates trig lookup tables, filter constants, or even a whole
> assembler for a soft CPU — all in the same language, executed during
> generation. You can also read a **file** at generation time
> (`scala.io.Source` → `VecInit(array.toIndexedSeq.map(_.U(8.W)))`), and convert
> between Chisel types with `asUInt` / `asTypeOf` (`Vec`↔`UInt`, `Bundle`↔`UInt`).

---

## 10.4 Configuration with parameters

**Simple parameter** — pass a bit width to the constructor:

`src/main/scala/ParamAdder.scala`
```scala
class ParamAdder(n: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(n.W)); val b = Input(UInt(n.W)); val c = Output(UInt(n.W))
  })
  io.c := io.a + io.b
}
// ...
val add8  = Module(new ParamAdder(8))
val add16 = Module(new ParamAdder(16))
```

**Case class** — package many parameters into one lightweight, immutable value
(optionally validated):

`src/main/scala/Config.scala`
```scala
case class Config(txDepth: Int, rxDepth: Int, width: Int)

case class SaveConf(txDepth: Int, rxDepth: Int, width: Int) {
  assert(txDepth > 0 && rxDepth > 0 && width > 0, "parameters must be larger than 0")
}
```

**Type parameter** — parameterize by a Chisel *type*. `[T <: Data]` accepts any
Chisel type, so one mux works for a `UInt` or a whole `Bundle` (this is how
Chisel's own `Mux` is generic):

`src/main/scala/ParamFunc.scala`
```scala
def myMux[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
  val ret = WireDefault(fPath)
  when (sel) { ret := tPath }
  ret
}

val resA = myMux(io.selA, 5.U, 10.U)        // with a UInt
val resB = myMux(io.selB, tVal, fVal)       // with a ComplexIO Bundle
```

> **Beyond this chapter's runnable set:** the book also parameterizes *modules*
> by type (a network-on-chip router `class Router[T <: Data](dt: T, n: Int)`),
> uses **parameterized bundles** (with a `private` type parameter so `cloneType`
> works), and shows **optional ports** via `Option`/`Some`/`None` (the debug port
> on a register file — we built exactly that in Chapter 2). Those are verbose, so
> we describe them here and keep the project focused.

---

## 10.5 Inheritance

`Module` is a Scala class, so use inheritance to share an interface. An abstract
`Ticker` fixes the `io`; three subclasses implement tick generation differently
(up, down, nerd) — and a single generic test drives all of them:

`src/main/scala/Ticker.scala`
```scala
abstract class Ticker(n: Int) extends Module {
  val io = IO(new Bundle { val tick = Output(Bool()) })
}
class UpTicker(n: Int)   extends Ticker(n) { /* count up   */ }
class DownTicker(n: Int) extends Ticker(n) { /* count down */ }
class NerdTicker(n: Int) extends Ticker(n) { /* count to -1 */ }
```

The tester takes `[T <: Ticker]`, so it accepts any implementation
(`src/test/scala/TickerTest.scala`).

---

## 10.6 Functional programming

Combine hardware with higher-order functions. `reduce` folds a collection with
a binary op (a *chain*); **`reduceTree`** builds a balanced *tree* (shorter
combinational delay):

`src/main/scala/functional.scala`
```scala
val sum = vec.reduceTree(_ + _)                       // sum via an adder tree
val min = vec.reduceTree((x, y) => Mux(x < y, x, y))  // minimum via a mux tree
```

To find the **minimum and its index**, carry both through the reduction — with
a `Bundle`, or with Scala **tuples** + `zipWithIndex`:

`src/main/scala/functional.scala`
```scala
val resFun = vec.zipWithIndex
  .map((x) => (x._1, x._2.U))
  .reduce((x, y) => (Mux(x._1 < y._1, x._1, y._1), Mux(x._1 < y._1, x._2, y._2)))
```

The `FunctionalMinTester` checks the hardware against a **pure-Scala reference
model** (`ScalaFunctionalMin.findMin`) — a powerful testing pattern.

> The book extends this to build an **arbitration tree** from 2:1 arbiters via
> `reduceTree` (both a simple priority version and a *fair* stateful one). It's
> an advanced, lengthy example — see the book and `ArbiterTree.scala` in the main
> repo.

---

## 10.7 Build, run, and check

```
$ sbt test
```

Expected tail (10 tests):

```
[info] Tests: succeeded 10, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits `BcdTable.sv`, `GenHardware.sv`, `UseAdder.sv`, `ParamFunc.sv`,
`FunctionalMin.sv`, and `UpTicker.sv`. And the case-class demo:

```
$ sbt "runMain ConfigDemo"
...
The width is 16
```

---

## 10.8 Recap

- Chisel generators run Scala at construction time — `for`/`if`/collections
  choose and build hardware.
- **Functions** return hardware (tuples for multiple outputs); **`VecInit`**
  builds ROM/logic tables from Scala data or files.
- Parameterize by **value** (constructor args, case classes) or by **type**
  (`[T <: Data]`) for functions and modules; use `Option` for optional ports.
- Use **inheritance** to share an interface and test many variants with one
  bench.
- **`reduce`/`reduceTree`** + function literals compose hardware functionally;
  check against a **Scala reference model**.

## 10.9 Exercise

Generate a sine lookup table with a few lines of Scala (`math.sin`, scaled to
`UInt`) and index it with a counter. Then write a `reduceTree`-based generator
(e.g. a wide OR, a max-finder, or a popcount) and test it against a Scala
reference model.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 9 — Communicating State Machines](../ch09-communicating-state-machines/README.md)**.
Next: **[Chapter 11 — Example Designs](../ch11-example-designs/README.md)**.
