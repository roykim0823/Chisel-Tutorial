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

The type of a `val`/`var` is normally inferred from the assigned value, but it
can be stated explicitly:

```scala
val number: Int = 42
```
*illustrative*

A `for` loop is the classic way to drive a circuit generator. The following
loop connects the bits of a shift register one to the next:

```scala
val regVec = Reg(Vec(8, UInt(1.W)))

regVec(0) := io.din
for (i <- 1 until 8) {
  regVec(i) := regVec(i - 1)
}
```
*illustrative*

> This is *not* the most concise way to write a shift register. It is better
> to use a plain `UInt` of the right width and assign its new value with an
> expression using the `##` operator (concatenation) and proper indexing. The
> loop version above is shown purely to demonstrate a Scala `for` loop used
> for circuit generation.

A Scala **tuple** groups a sequence of possibly different types in
parentheses; fields are accessed with `._n`, starting at `1`. The following
snippet builds a tuple representing a city (zip code, name):

```scala
val city = (2000, "Frederiksberg")
val zipCode = city._1
val name = city._2
```
*illustrative*

Tuples are useful for returning more than one value from a function — see
§10.2 below.

The **`Seq`** collection (an ordered, by default immutable, sequence) is
indexed with `()`, zero-based. It is the preferred general-purpose collection
for Chisel hardware generators:

```scala
val numbers = Seq(1, 15, -2, 0)
val second = numbers(1)   // second == 15
```
*illustrative*

---

## 10.2 Lightweight components with functions

A module has boilerplate; a Scala **function that returns hardware** is a
lighter alternative. It's a real generator — calling it *builds* hardware
(the return value of a Scala function is the result of its last expression).
As a simple example, an adder function:

```scala
def adder(x: UInt, y: UInt) = {
  x + y
}
```
*illustrative*

Calling it twice creates two independent adder instances — no add operation
runs at elaboration time, the calls just build hardware:

```scala
val x = adder(a, b)
// another adder
val y = adder(c, d)
```
*illustrative*

> This adder is an artificial example to keep things simple — Chisel already
> provides an adder generator via the `+` operator (`UInt`'s `+(that: UInt)`).

Functions can also carry state via a register. If the function body is a
single statement, the curly braces can be omitted:

```scala
def delay(x: UInt) = RegNext(x)
```
*illustrative*

Calling the function with itself as the argument chains two registers,
producing a two-clock-cycle delay:

```scala
val delOut = delay(delay(delIn))
```
*illustrative*

> Again, too small an example to be useful on its own — `RegNext()` already
> *is* the one-cycle delay function; this just shows function composition.

Functions return only one value. To return more than one, wrap several
output wires in a Scala **tuple**:

`src/main/scala/functional.scala`
```scala
def compare(a: UInt, b: UInt) = {
  val equ = a === b
  val gt = a > b
  (equ, gt)                       // return a tuple
}
```

The tuple returned by a call can be accessed with `._n`:

```scala
val cmp = compare(inA, inB)
val equResult = cmp._1
val gtResult = cmp._2
```
*illustrative*

Or decomposed directly into named wires, as this chapter's project does:

`src/main/scala/functional.scala`
```scala
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
> generation.

### File Reading

A logic table can also be built from data read from a **file** at generation
time, using the standard Scala/Java `scala.io.Source`:

```scala
import chisel3._
import scala.io.Source

class FileReader extends Module {
  val io = IO(new Bundle {
    val address = Input(UInt(8.W))
    val data = Output(UInt(8.W))
  })

  val array = new Array[Int](256)
  var idx = 0

  // read the data into a Scala array
  val source = Source.fromFile("data.txt")
  for (line <- source.getLines()) {
    array(idx) = line.toInt
    idx += 1
  }

  // convert the Scala integer array to a Seq
  // and then into a vector of Chisel UInt
  val table = VecInit(array.toIndexedSeq.map(_.U(8.W)))

  // use the table
  io.data := table(io.address)
}
```
*illustrative*

The maybe-intimidating line is `VecInit(array.toIndexedSeq.map(_.U(8.W)))`:
`toIndexedSeq` converts the Scala `Array` to a `Seq`, which supports `map`.
`map` invokes a function on each element and returns a sequence of the
results — here `_.U(8.W)` converts each Scala `Int` to a Chisel `UInt`
literal of 8 bits. `VecInit` then builds a Chisel `Vec` from that `Seq` of
Chisel values. The same pattern (`msg.map(_.U)`, above) is what turns the
`"Hello World!"` string into a byte ROM — a Scala/Java `String` is itself a
`Seq[Char]`, so `map` works on it directly.

### Type Conversion

All Chisel types are ultimately just a collection of bits, so converting
between them is easy. A `Vec` of bytes can be packed into a `UInt` (the first
element lands in the low bits):

```scala
val vec = Wire(Vec(4, UInt(8.W)))
val word = vec.asUInt
```
*illustrative*

and unpacked back with `asTypeOf`:

```scala
val vec2 = word.asTypeOf(Vec(4, UInt(8.W)))
```
*illustrative*

A `Bundle` converts to a `UInt` the same way:

```scala
class MyBundle extends Bundle {
  val a = UInt(8.W)
  val b = UInt(16.W)
}

val bundle = Wire(new MyBundle)
val word2 = bundle.asUInt
```
*illustrative*

```scala
val bundle2 = word2.asTypeOf(new MyBundle)
```
*illustrative*

and the same conversion can zero-initialize every field of a bundle at once:

```scala
val bundle3 = 0.U.asTypeOf(new MyBundle)
```
*illustrative*

> **Bit order caveat:** a `Bundle`'s fields are packed in the *opposite* order
> from a `Vec`'s elements — the **last** declared field (`b` above) lands in
> the **low** bits of the `UInt`, followed by the second-to-last, and so on.

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

An object of the case class is created by calling the constructor; fields are
immutable and read by name:

`src/main/scala/Config.scala`
```scala
val param = Config(4, 2, 16)

println("The width is " + param.width)
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

> **Caveat:** both mux paths must be of the *same* type `T`. Mixing types
> compiles (Scala can't always tell `T` apart at the call site) but fails at
> **runtime**, e.g. mixing a `UInt` true-path with an `SInt` false-path:
> ```scala
> val resErr = myMux(selA, 5.U, 10.S)   // runtime error: types don't match
> ```
> *illustrative*

For the "complex" `resB` case above, `ComplexIO` is a two-field `Bundle`, and
a `Bundle` *constant* is built by wiring up each field of a `Wire`:

`src/main/scala/ParamFunc.scala`
```scala
class ComplexIO extends Bundle {
  val d = UInt(10.W)
  val b = Bool()
}
```
```scala
val tVal = Wire(new ComplexIO)
tVal.b := true.B
tVal.d := 42.U
val fVal = Wire(new ComplexIO)
fVal.b := false.B
fVal.d := 13.U

// The multiplexer with a complex type
val resB = myMux(selB, tVal, fVal)
```

The first version of `myMux` used `WireDefault` to build a wire of type `T`
*with* a default value. If a plain wire of the type is wanted without an
initial value, use `fPath.cloneType` to get the Chisel type instead:

`src/main/scala/ParamFunc.scala`
```scala
def myMuxAlt[T <: Data](sel: Bool, tPath: T, fPath: T): T = {

  val ret = Wire(fPath.cloneType)
  ret := fPath
  when (sel) {
    ret := tPath
  }
  ret
}
```

### Modules with Type Parameters

Whole **modules**, not just functions, can be parameterized by a Chisel type.
A network-on-chip router that should not hard-code its payload format adds a
type parameter `T` to the module constructor (and takes one constructor
argument of that type); the number of ports is a second, ordinary `Int`
parameter:

```scala
class NocRouter[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val address = Input(Vec(n, UInt(8.W)))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address
  // ...
}
```
*illustrative*

Define the payload type as an ordinary `Bundle`, then instantiate the router
with an instance of that type and the port count:

```scala
class Payload extends Bundle {
  val data = UInt(16.W)
  val flag = Bool()
}

val router = Module(new NocRouter(new Payload, 2))
```
*illustrative*

### Parameterized Bundles

The router above needs two separate parallel vectors (address, data). A
cleaner design is a `Bundle` that is itself parameterized. The naive attempt
looks like this:

```scala
class Port[T <: Data](dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}
```
*illustrative*

This compiles, but a constructor parameter becomes a public field of the
class — and when Chisel needs to clone the `Bundle`'s type (e.g. inside a
`Vec`), that public field gets in the way. The fix is to mark the parameter
`private`:

```scala
class Port[T <: Data](private val dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}
```
*illustrative*

With that fixed `Bundle`, the router's ports become a single parameterized
type, and it is instantiated by wrapping the payload type in a `Port`:

```scala
class NocRouter2[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address
  // ...
}

val router = Module(new NocRouter2(new Port(new Payload), 2))
```
*illustrative*

### Optional Ports

Some IO ports should only exist under a configuration flag. Example: a
register file for a 32-bit RISC core, with an optional debug port that
exposes every register (useful for the tester, wasteful in the final
design). The `debug: Boolean` constructor parameter decides — via Scala's
`Option` (`Some`/`None`) — whether the port exists at all:

```scala
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
*illustrative — we built this exact register file (without the optional port)
in [Chapter 2](../ch02-basic-components/README.md).*

On the tester side, the optional port is unwrapped the same way, with `.get`:

```scala
dut.io.dbgPort.get(4).expect(123.U)
```
*illustrative*

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

The tester takes `[T <: Ticker]`, so it accepts any implementation:

`src/test/scala/TickerTest.scala`
```scala
trait TickerTestFunc {
  def testFn[T <: Ticker](dut: T, n: Int) = {
    // -1 means that no ticks have been seen yet
    var count = -1
    for (_ <- 0 to n * 3) {
      // Check for correct output
      if (count > 0)
        dut.io.tick.expect(false.B)
      else if (count == 0)
        dut.io.tick.expect(true.B)

      // Reset the counter on a tick
      if (dut.io.tick.peekBoolean())
        count = n-1
      else
        count -= 1
      dut.clock.step()
    }
  }
}
```

`testFn` has three effective parameters: (1) the type parameter `[T <: Ticker]`
itself, which accepts `Ticker` or any subclass, (2) `dut`, the design under
test, of type `T` or a subtype thereof, and (3) `n`, the number of clock
cycles expected between ticks. It waits for the first tick (the exact start
point may differ between implementations), then checks that `tick` repeats
every `n` cycles.

**Recommended workflow:** get the *simplest* ticker (`UpTicker`) and the
tester itself working and correct first — `println` debugging is fine at this
stage — before trusting the tester to check the other, trickier
implementations (`DownTicker`, `NerdTicker`). Once confident, run just the
ticker tests with:

```
$ sbt "testOnly TickerTest"
```

---

## 10.6 Functional programming

Combine hardware with higher-order functions. Start with the simplest case,
summing a `Vec`: define an `add` function and fold the vector with Scala's
`reduce`, which combines the first two elements, then combines that result
with the next, and so on until one value remains:

```scala
def add(a: UInt, b: UInt) = a + b

val sum = vec.reduce(add)
```
*illustrative*

The combining function can just as well be an anonymous **function literal**
instead of a named `def`. The syntax for a function literal is parameters in
parentheses, followed by `=>`, followed by the body:

```scala
(param) => function body
```
*illustrative*

With Scala's `_` wildcard standing in for the two operands, the whole thing
collapses to one line:

```scala
val sum = vec.reduce(_ + _)
```
*illustrative*

`reduce` builds a *chain* of adders. For a sum, a chain isn't ideal — a
*tree* has a shorter combinational delay. **`reduceTree`** builds that
balanced tree instead, and is what this chapter's project actually uses:

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

Here `zipWithIndex` turns the `Vec[UInt]` into a Scala `Vector` of tuples
`(UInt, Int)`; the result is *still* a Scala `Vector`, not a Chisel `Vec` —
so it must use `reduce`, **not** `reduceTree`, which only exists on Chisel's
`Vec`.

To keep using `reduceTree`, swap the Scala tuple for a Chisel **`MixedVec`**
(a fixed-size, indexable collection whose elements can have different
types — like a tuple, but usable as an actual Chisel collection):

```scala
val scalaVector = vec.zipWithIndex
  .map((x) => MixedVecInit(x._1, x._2.U(8.W)))
val resFun2 = VecInit(scalaVector)
  .reduceTree((x, y) => Mux(x(0) < y(0), x, y))

val minVal = resFun2(0)
val minIdx = resFun2(1)
```
*illustrative*

Converting the Scala `Vector` of `MixedVec`s into a Chisel `Vec` (via
`VecInit`) makes `reduceTree` available again, at the cost of an extra
conversion step. `resFun2` ends up a two-element `MixedVec`, indexed like an
ordinary `Vec`.

The `FunctionalMinTester` checks the hardware against a **pure-Scala reference
model** (`ScalaFunctionalMin.findMin`) — a powerful testing pattern.

### An Arbitration Tree

`reduceTree` also builds an arbitration tree out of nothing but 2:1 arbiters:

```scala
class Arbiter[T <: Data: Manifest](n: Int, private val gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new DecoupledIO(gen)))
    val out = new DecoupledIO(gen)
  })

  io.out <> io.in.reduceTree((a, b) => arbitrateSimp(a, b))
}
```
*illustrative*

The input is a `Vec` of ready/valid (`DecoupledIO`) interfaces, the output a
single ready/valid interface. All that's left is a function that arbitrates
between exactly two requests.

#### Simple Arbitration

The combinational priority arbiter from earlier chapters can't be reused
directly here: with a ready/valid interface, a combinational path from
`ready` to `valid` isn't allowed, so the winning request's data must be
**registered**. The following 2:1 arbitration function assumes a requester
holds `valid` until it is read (acknowledged by `ready`), and that `ready`
can be asserted one cycle after `valid` is seen:

```scala
def arbitrateSimp(a: DecoupledIO[T], b: DecoupledIO[T]) = {

  val regData = Reg(gen)
  val regEmpty = RegInit(true.B)
  val regReadyA = RegInit(false.B)
  val regReadyB = RegInit(false.B)

  val out = Wire(new DecoupledIO(gen))

  when (a.valid & regEmpty & !regReadyB) {
    regReadyA := true.B
  } .elsewhen (b.valid & regEmpty & !regReadyA) {
    regReadyB := true.B
  }
  a.ready := regReadyA
  b.ready := regReadyB

  when (regReadyA) {
    regData := a.bits
    regEmpty := false.B
    regReadyA := false.B
  }
  when (regReadyB) {
    regData := b.bits
    regEmpty := false.B
    regReadyB := false.B
  }

  out.valid := !regEmpty
  when (out.ready) {
    regEmpty := true.B
  }

  out.bits := regData
  out
}
```
*illustrative*

Four registers do the work: `regData` holds the output data, `regEmpty`
flags that the data register is empty, and `regReadyA`/`regReadyB` are the
registered `ready` signals for the two inputs. When the data register is
empty and one input is `valid`, `ready` is asserted (registered) for *that*
input only — there is just one data register, so only one input can be
accepted at a time. Once a registered `ready` fires, the input is still
assumed `valid`, so its data is captured, `regEmpty` is cleared, and the
`ready` flag resets. The output is `valid` whenever the data register is not
empty; once the receiver asserts `ready`, the register empties again. Note
this always favors input `a` when both are pending — it is a **priority**
arbiter, not a fair one.

#### Fair Arbitration

A priority arbiter lets a high-priority requester dominate. A **fair** 2:1
arbiter instead remembers who won last time, using a small state machine
with two idle states (so each input gets a turn) and two "has data" states:

```scala
def arbitrateFair(a: DecoupledIO[T], b: DecoupledIO[T]) = {
  object State extends ChiselEnum {
    val idleA, idleB, hasA, hasB = Value
  }
  import State._
  val regData = Reg(gen)
  val regState = RegInit(idleA)
  val out = Wire(new DecoupledIO(gen))
  a.ready := regState === idleA
  b.ready := regState === idleB
  out.valid := (regState === hasA || regState === hasB)
  switch(regState) {
    is (idleA) {
      when (a.valid) {
        regData := a.bits
        regState := hasA
      } otherwise {
        regState := idleB
      }
    }
    is (idleB) {
      when (b.valid) {
        regData := b.bits
        regState := hasB
      } otherwise {
        regState := idleA
      }
    }
    is (hasA) {
      when (out.ready) {
        regState := idleB
      }
    }
    is (hasB) {
      when (out.ready) {
        regState := idleA
      }
    }
  }
  out.bits := regData
  out
}
```
*illustrative*

One data register plus one state register are enough. In `idleA`, only input
`a` is accepted (`ready` for `a` only); if `a` isn't valid, the state moves on
to `idleB` so `b` gets a chance next. Once a request is accepted the state
moves to `hasA`/`hasB`; when the consumer takes the output (`out.ready`), the
state returns to the *other* input's idle state — guaranteeing the next
winner alternates rather than letting one input starve the other. (With just
one data register, the arbiter can only be ready for one input at a time; a
second data register would be needed to accept both inputs in the same
cycle.) Building an `Arbiter` out of these functions and `reduceTree` gives a
whole arbitration tree essentially "for free" — see `ArbiterTree.scala` in
the main repo for the full runnable version.

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
