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
The book's Chapter 10 has no figures; the arbiter timing diagrams in
[§10.6.2](#1062-an-arbitration-tree) are additions of this tutorial, recorded from real
simulation runs.*

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

A `for` loop is the classic way to drive a circuit generator. The following
loop connects the bits of a shift register one to the next:

```scala
val regVec = Reg(Vec(8, UInt(1.W)))

regVec(0) := io.din
for (i <- 1 until 8) {
  regVec(i) := regVec(i - 1)
}
```


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

*Scala note — Scala tuples → [§G.2](../SCALA-NOTES.md#g2-tuples).*

Tuples are useful for returning more than one value from a function — see
§10.2 below.

The **`Seq`** collection (an ordered, by default immutable, sequence) is
indexed with `()`, zero-based. It is the preferred general-purpose collection
for Chisel hardware generators:

```scala
val numbers = Seq(1, 15, -2, 0)
val second = numbers(1)   // second == 15
```

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

*Scala note — a `def` method → [§C.4](../SCALA-NOTES.md#c4-def-methods), and a block's value is its last expression → [§C.5](../SCALA-NOTES.md#c5-block-as-expression-implicit-return).*

Calling it twice creates two independent adder instances — no add operation
runs at elaboration time, the calls just build hardware:

```scala
val x = adder(a, b)
// another adder
val y = adder(c, d)
```

> This adder is an artificial example to keep things simple — Chisel already
> provides an adder generator via the `+` operator (`UInt`'s `+(that: UInt)`).

Functions can also carry state via a register. If the function body is a
single statement, the curly braces can be omitted:

```scala
def delay(x: UInt) = RegNext(x)
```


Calling the function with itself as the argument chains two registers,
producing a two-clock-cycle delay:

```scala
val delOut = delay(delay(delIn))
```


> Again, too small an example to be useful on its own — `RegNext()` already
> *is* the one-cycle delay function; this just shows function composition.

Functions return only one value. To return more than one, wrap several
output wires in a Scala **tuple**:

`src/main/scala/FunctionalComp.scala`
```scala
  def compare(a: UInt, b: UInt) = {
    val equ = a === b
    val gt = a > b
    (equ, gt)   // return a tuple
  }
```

The tuple returned by a call can be accessed with `._n`:

```scala
val cmp = compare(inA, inB)
val equResult = cmp._1
val gtResult = cmp._2
```

Or decomposed directly into named wires, as this chapter's project does:

`src/main/scala/FunctionalComp.scala`
```scala
  val (equ, gt) = compare(io.a, io.b)   // decompose the tuple
```

Functions used across modules belong in a Scala `object` of utilities.

---

## 10.3 Generating combinational logic (ROM tables)

A truth table is combinational logic — a ROM addressed by its input. Build one
with **`VecInit`** and ordinary Scala:

`src/main/scala/GenHardware.scala`
```scala
  // A Scala String is a Seq[Char]; map each char to a UInt -> a ROM of bytes.
  val msg = "Hello World!"
  // VecInit takes a Seq of UInts and returns a Vec of UInts.
  val text = VecInit(msg.map(_.U))
  val len = msg.length.U

  // A small square-lookup ROM.
  val n = io.squareIn
  val squareROM = VecInit(0.U, 1.U, 4.U, 9.U, 16.U, 25.U)
  val square = squareROM(n)
```

*Scala note — `map`/`reduce`/`zip`/`zipWithIndex` → [§F.3](../SCALA-NOTES.md#f3-map--foreach--reduce--zip--zipwithindex), and a `String` as a `Seq[Char]` → [§F.4](../SCALA-NOTES.md#f4-string-as-a-seqchar).*

The classic example is **binary → BCD** conversion. In VHDL you'd generate this
table with an external script; in Chisel a Scala loop builds it inline:

`src/main/scala/BcdTable.scala`
```scala
  val table = Wire(Vec(100, UInt(8.W)))

  // Convert binary i to BCD: tens digit in the upper nibble, ones in the lower.
  for (i <- 0 until 100) {
    table(i) := (((i / 10) << 4) + i % 10).U
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

  // convert the Scala Array to a Scala sequence Seq
  val table = VecInit(array.toIndexedSeq.map(_.U(8.W)))

  // use the table
  io.data := table(io.address)
}
```

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
between them is easy. A `Vec` of bytes can be packed into a 32-bit `UInt` (the first
element lands in the low bits):

```scala
val vec = Wire(Vec(4, UInt(8.W)))
val word = vec.asUInt
```

and unpacked back with `asTypeOf`:

```scala
val vec2 = word.asTypeOf(Vec(4, UInt(8.W)))
```

A `Bundle` converts to a `UInt` the same way:

```scala
class MyBundle extends Bundle {
  val a = UInt(8.W)
  val b = UInt(16.W)
}

val bundle = Wire(new MyBundle)
val word2 = bundle.asUInt

val bundle2 = word2.asTypeOf(new MyBundle)
```

and the same conversion can zero-initialize every field of a bundle at once:

```scala
val bundle3 = 0.U.asTypeOf(new MyBundle)
```

> **Bit order caveat:** a `Bundle`'s fields are packed in the *opposite* order
> from a `Vec`'s elements — the **last** declared field (`b` above) lands in
> the **low** bits of the `UInt`, followed by the second-to-last, and so on.

---

## 10.4 Configuration with parameters

A generator earns its keep when one description can produce a whole *family* of
circuits. The knobs are plain Scala — values and types passed to a constructor —
and they come in four flavours, in rising order of power:

| Flavour | What gets configured | Where |
|---------|----------------------|-------|
| Simple parameter | a number: bit width, depth, port count | [10.4.1](#1041-simple-parameters) |
| `case class` | many parameters bundled (and validated) as one value | [10.4.2](#1042-grouping-parameters-in-a-case-class) |
| Type parameter | the Chisel *type* a function, module, or bundle works on | [10.4.3](#1043-functions-with-type-parameters), [10.4.4](#1044-modules-with-type-parameters), [10.4.5](#1045-parameterized-bundles) |
| `Option` port | whether a port exists at all | [10.4.6](#1046-optional-ports) |

All four share one property worth holding on to: they are resolved while the
hardware is being *constructed*, so by the time Verilog exists the parameter is
gone — it has been baked into widths, module counts, and port lists.

### 10.4.1 Simple parameters

The simplest knob is an ordinary constructor argument. `n` below is a Scala
`Int`, in scope throughout the class body, and it reaches the hardware through
`n.W`, which turns an `Int` into the `Width` that `UInt(...)` expects:

`src/main/scala/ParamAdder.scala`
```scala
class ParamAdder(n: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(n.W))
    val b = Input(UInt(n.W))
    val c = Output(UInt(n.W))
  })

  io.c := io.a + io.b
}
```

Note there is no `val` in front of `n`. The parameter is only read while the
module is being built, so it does not need to survive as a field — Scala keeps
it as a `private[this]` value, which is exactly what you want (a public
`val`-parameter of a *`Bundle`* actually causes trouble; see
[10.4.5](#1045-parameterized-bundles)).

Two differently-sized adders then come from the same generator — inside
`UseAdder`:

`src/main/scala/ParamAdder.scala`
```scala
  val add8 = Module(new ParamAdder(8))
  val add16 = Module(new ParamAdder(16))
```

**A Chisel parameter is not a Verilog `parameter`.** In Verilog or VHDL the knob
stays in the generated code (`parameter WIDTH = 8`, a VHDL generic) and the
*downstream* tool specializes it. Chisel resolves it first: each distinct
argument elaborates its own concrete module, and the emitted SystemVerilog
contains no `n` anywhere. Generate it and look at the top of
`generated/UseAdder.sv` (comments trimmed):

```
$ sbt "runMain Generate UseAdder"
...
emitting generated/UseAdder.sv
```

Run bare, `Generate` emits every design in the chapter; naming one keeps the
output to a single file — see [§10.7](#107-build-run-and-check) for the list of
names and for a way to emit one module without going through `Generate` at all.

```
module ParamAdder(	// src/main/scala/ParamAdder.scala:4:7
  input  [7:0] io_a,
               io_b,
  output [7:0] io_c
);

  assign io_c = io_a + io_b;
endmodule

module ParamAdder_1(	// src/main/scala/ParamAdder.scala:4:7
  input  [15:0] io_a,
                io_b,
  output [15:0] io_c
);

  assign io_c = io_a + io_b;
endmodule
```

One source class, two Verilog modules: `ParamAdder` (from `n = 8`) and
`ParamAdder_1` (from `n = 16`), each with its widths already hard-wired. Chisel
appends `_1`, `_2`, … to keep the names unique. Both modules point back to the
same source line, `ParamAdder.scala:4:7` — a useful reminder that the two are
one generator seen twice.

Further down the same file, the instantiation site inside `UseAdder` shows the
second thing the parameter decided — where signals get **truncated**:

```
  ParamAdder add8 (	// src/main/scala/ParamAdder.scala:22:20
    .io_a (io_x[7:0]),	// src/main/scala/ParamAdder.scala:29:13
    .io_b (io_y[7:0]),	// src/main/scala/ParamAdder.scala:30:13
    .io_c (_add8_io_c)
  );
  ParamAdder_1 add16 (	// src/main/scala/ParamAdder.scala:23:21
    .io_a (io_x),
    .io_b (io_y),
    .io_c (_add16_io_c)
  );
```

`add8` is fed the same 16-bit `io_x`/`io_y` as `add16`, but its ports are 8 bits
wide, so Chisel inserts `[7:0]` slices. That is a real behavioural difference
produced purely by a constructor argument, and it is what the test below
exploits to tell the two instances apart.

**Naming and defaulting parameters.** Since these are plain Scala arguments,
they get Scala's call-site conveniences: a default value lets callers omit the
common case, and named arguments keep a long parameter list readable and
unswappable:

```scala
class ParamAdder(n: Int = 32) extends Module { ... }

val a = Module(new ParamAdder())          // 32 bits
val b = Module(new ParamAdder(n = 8))     // 8 bits, spelled out
```

*Scala note — default arguments → [§C.7](../SCALA-NOTES.md#c7-default-arguments); named arguments → [§C.6](../SCALA-NOTES.md#c6-named-arguments).*

**Rejecting nonsense early.** Nothing stops a caller from writing
`new ParamAdder(0)`, which would elaborate a zero-width adder. A `require` at
the top of the module makes the constructor refuse instead:

```scala
class ParamAdder(n: Int) extends Module {
  require(n > 0, "width must be positive, got " + n)
  ...
}
```

Because `require` runs before any hardware is built, a bad value fails at
elaboration with a plain Scala exception rather than producing a broken circuit:

```
java.lang.IllegalArgumentException: requirement failed: width must be positive, got 0
```

*Scala note — `require` as a precondition → [§J.4](../SCALA-NOTES.md#j4-require).*
The same idea applied to a whole parameter set is
[10.4.2](#1042-grouping-parameters-in-a-case-class).

**How do you test a parameter?** There is nothing to poke: `n` is consumed at
*generation* time and has vanished by the time the simulation starts. What you
*can* observe is the one place the width shows through — an n-bit `+` keeps n
bits, so the carry out is dropped and the sum wraps modulo 2ⁿ. The bench builds
one test per width with an ordinary Scala `for` loop, the same trick the
generator itself uses:

`src/test/scala/ParamAdderTest.scala`
```scala
  for (n <- Seq(4, 8, 16)) {
    val mask = (1 << n) - 1

    s"ParamAdder($n)" should s"add modulo 2^$n" in {
      test(new ParamAdder(n)) { dut =>
        val cases = Seq((0, 0), (1, 2), (mask, 0), (mask, 1), (mask, mask), (mask / 2, mask / 2 + 1))
        for ((a, b) <- cases) {
          dut.io.a.poke(a.U)
          dut.io.b.poke(b.U)
          // An n-bit `+` keeps n bits: the carry out is dropped.
          dut.io.c.expect(((a + b) & mask).U)
        }
      }
    }
  }
```

`UseAdder` is checked the same way. Both instances are fed the full 16-bit
`io.x`/`io.y`, but `add8`'s ports are only 8 bits wide, so those connections
**truncate** — and since the result is `add16.io.c | add8.io.c`, the two
instances are told apart from outside: with `x = 0x00ff, y = 1` the 8-bit adder
wraps to `0x00` while the 16-bit one produces `0x0100`.

`src/test/scala/ParamAdderTest.scala`
```scala
      def check(x: Int, y: Int): Unit = {
        val sum16 = (x + y) & 0xffff
        val sum8 = ((x & 0xff) + (y & 0xff)) & 0xff
        dut.io.x.poke(x.U)
        dut.io.y.poke(y.U)
        dut.io.res.expect((sum16 | sum8).U)
      }
```

```
$ sbt "testOnly ParamAdderTest"
[info] ParamAdderTest:
[info] ParamAdder(4)
[info] - should add modulo 2^4
[info] ParamAdder(8)
[info] - should add modulo 2^8
[info] ParamAdder(16)
[info] - should add modulo 2^16
[info] UseAdder
[info] - should combine an 8-bit and a 16-bit instance of the same generator
[info] Tests: succeeded 4, failed 0, canceled 0, ignored 0, pending 0
```

### 10.4.2 Grouping parameters in a case class

A generator with one knob takes one argument; a generator with eight knobs
taking eight arguments is a mess to thread through submodules. A **`case
class`** packages many parameters into one lightweight, immutable value
(optionally validated):

`src/main/scala/Config.scala`
```scala
case class Config(txDepth: Int, rxDepth: Int, width: Int)
```

A `case class` can also validate its parameters, so a bad configuration fails
at elaboration instead of producing broken hardware:

`src/main/scala/Config.scala`
```scala
case class SaveConf(txDepth: Int, rxDepth: Int, width: Int) {
  assert(txDepth > 0 && rxDepth > 0 && width > 0, "parameters must be larger than 0")
}
```

*Scala note — `case class` → [§B.2](../SCALA-NOTES.md#b2-case-class); Scala's `assert` → [§J.3](../SCALA-NOTES.md#j3-assert-scala).*

An object of the case class is created by calling the constructor; fields are
immutable and read by name:

`src/main/scala/Config.scala`
```scala
// Run with:  sbt "runMain ConfigDemo"
object ConfigDemo extends App {
  val param = Config(4, 2, 16)
  println("The width is " + param.width)
}
```

### 10.4.3 Functions with type parameters

The knob does not have to be a number: a generator can also be parameterized by
a Chisel *type*. `[T <: Data]` accepts any Chisel type, so one mux works for a
`UInt` or a whole `Bundle` (this is how Chisel's own `Mux` is generic):

`src/main/scala/ParamFunc.scala`
```scala
  // A multiplexer parameterized by a Chisel TYPE: [T <: Data] accepts any
  // Chisel type (Data is the root of the type system). Same function works for
  // a UInt or a whole Bundle.
  def myMux[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
    val ret = WireDefault(fPath)
    when (sel) {
      ret := tPath
    }
    ret
  }
```

*Scala note — a type parameter with an upper bound → [§D.1](../SCALA-NOTES.md#d1-type-parameters-t-with-an-upper-bound-t--x).*

Calling it with a plain `UInt` needs nothing special:

`src/main/scala/ParamFunc.scala`
```scala
  // Use with a simple UInt type.
  val resA = myMux(io.selA, 5.U, 10.U)
```

> **Caveat:** both mux paths must be of the *same* type `T`. Mixing types
> compiles (Scala can't always tell `T` apart at the call site) but fails at
> **runtime**, e.g. mixing a `UInt` true-path with an `SInt` false-path:
> ```scala
> val resErr = myMux(selA, 5.U, 10.S)   // runtime error: types don't match
> ```

For the "complex" case, `ComplexIO` is a two-field `Bundle`, and a `Bundle`
*constant* is built by wiring up each field of a `Wire`:

`src/main/scala/ParamFunc.scala`
```scala
class ComplexIO extends Bundle {
  val d = UInt(10.W)
  val b = Bool()
}
```

`src/main/scala/ParamFunc.scala`
```scala
  // Use with a complex Bundle type (build Bundle constants with a Wire).
  val tVal = Wire(new ComplexIO)
  tVal.b := true.B
  tVal.d := 42.U
  val fVal = Wire(new ComplexIO)
  fVal.b := false.B
  fVal.d := 13.U

  val resB = myMux(io.selB, tVal, fVal)  // Muxing a Bundle type
```

The first version of `myMux` used `WireDefault` to build a wire of type `T`
*with* a default value. If a plain wire of the type is wanted without an
initial value, use `fPath.cloneType` to get the Chisel type instead:

`src/main/scala/ParamFunc.scala`
```scala
  // Alternative using fPath.cloneType when no default value is wanted.
  def myMuxAlt[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
    val ret = Wire(fPath.cloneType)
    ret := fPath
    when (sel) {
      ret := tPath
    }
    ret
  }
```

### 10.4.4 Modules with type parameters

Whole **modules**, not just functions, can be parameterized by a Chisel type.
A network-on-chip router that should not hard-code its payload format adds a
type parameter `T` to the module constructor (and takes one constructor
argument of that type); the number of ports is a second, ordinary `Int`
parameter:

`src/main/scala/ParamModule.scala`
```scala
class NocRouter[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val address = Input(Vec(n, UInt(8.W)))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address; a plain swap of the two ports
  // stands in for real routing here, just enough to elaborate (n = 2).
  io.outPort(0) := io.inPort(1)
  io.outPort(1) := io.inPort(0)
}
```

Define the payload type as an ordinary `Bundle`, then instantiate the router
with an instance of that type and the port count:

`src/main/scala/ParamModule.scala`
```scala
class Payload extends Bundle {
  val data = UInt(16.W)
  val flag = Bool()
}
```

`src/main/scala/ParamModule.scala`
```scala
  val router = Module(new NocRouter(new Payload, 2))
```

The wrapper `UseParamRouter` in that file connects it up so there is
something to generate Verilog for.

### 10.4.5 Parameterized bundles

The router above needs two separate parallel vectors — one for the addresses,
one for the data — and nothing but convention keeps entry `i` of the one lined
up with entry `i` of the other. A cleaner design gives a port its own `Bundle`;
since the payload type is a parameter, that `Bundle` is parameterized too:

`src/main/scala/ParamBundle.scala`
```scala
class Port[T <: Data](dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}
```

Two details in that declaration are worth pausing on:

- **`dt.cloneType`** gives `data` a fresh, unbound copy of the payload *type*
  instead of reusing the caller's object. A Chisel `Data` object is both a type
  and (potentially) a piece of hardware; `cloneType` asks for just the type. Skip
  it and `data` and `dt` are literally the same object, which Chisel rejects with
  an `AliasedAggregateFieldException`.
- **The parameter has no `val`.** Chisel keeps no record of which `val`s in a
  `Bundle` you meant as ports — it finds them by reflecting over the bundle's
  **public** `Data`-typed members. Writing `val dt: T` would therefore make the
  parameter a *third* field: no error, no warning, just a `Port` that is silently
  a whole extra `Payload` wider, on every port and through every `:=`. Without
  the `val`, Scala keeps `dt` as a `private[this]` field and it never shows up.
  Marking it `private val` (as the book does) is equivalent as far as Chisel is
  concerned, and says the same thing more explicitly.

> **Watch out in a `case class`.** Case-class parameters are public `val`s by
> definition, so `case class Port[T <: Data](dt: T) extends Bundle` *does* get the
> phantom field even though no `val` was typed. There `private val` has to be
> spelled out.

`PortDemo` in the same file carries all three spellings so the difference can be
seen rather than taken on faith. It prints the field map (`elements`) Chisel
builds for each, and then the router's port list with and without the phantom
field (it writes no `.sv` files):

```
$ sbt "runMain PortDemo"
...
Port         (dt, no val)      fields: data, address
PortPrivate  (private val dt)  fields: data, address
PortPublic   (val dt)          fields: data, address, dt
```

With `val dt`, every port of the router carries the payload *twice* — eight
phantom `_dt_` ports across the two inputs and two outputs, and no warning
anywhere:

```
--- val dt (public: extra dt field) ---
module NocRouter2(
  input         clock,
                reset,
  input  [15:0] io_inPort_0_dt_data,
  input         io_inPort_0_dt_flag,
  input  [7:0]  io_inPort_0_address,
  input  [15:0] io_inPort_0_data_data,
  input         io_inPort_0_data_flag,
  ...
```

The demo's last case is `PortAliased`, the public `val` *without* `cloneType`,
where the two fields are the same object and Chisel refuses outright instead of
silently widening:

```
chisel3.AliasedAggregateFieldException: PortAliased contains aliased fields named (data,dt)
```

`src/test/scala/ParamBundleTest.scala` locks all of this in: two fields for
`Port` and `PortPrivate`, exactly eight `_dt_` ports for `PortPublic`, and the
aliasing exception for `PortAliased`.

With that `Bundle` in hand, the router's ports become a single parameterized
type, and it is instantiated by wrapping the payload type in a `Port`:

`src/main/scala/ParamBundle.scala`
```scala
class NocRouter2[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address; again a swap stands in for the
  // real routing logic.
  io.outPort(0) := io.inPort(1)
  io.outPort(1) := io.inPort(0)
}
```

`src/main/scala/ParamBundle.scala`
```scala
  val router = Module(new NocRouter2(new Port(new Payload), 2))
```

`UseParamRouter2` wraps it the same way as `UseParamRouter`, and
`src/test/scala/ParamBundleTest.scala` checks that both fields of the `Port`
actually survive the trip through the router. (The type-parameterized *module*
of the previous section has its own bench,
`src/test/scala/ParamModuleTest.scala`, one file per source file.)

### 10.4.6 Optional ports

Some IO ports should only exist under a configuration flag. Example: a
register file for a 32-bit RISC core, with an optional debug port that
exposes every register (useful for the tester, wasteful in the final
design). The `debug: Boolean` constructor parameter decides — via Scala's
`Option` (`Some`/`None`) — whether the port exists at all:

`src/main/scala/RegisterFile.scala`
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
  // The port is unwrapped with .get - only ever reached when it exists.
  if (debug) {
    io.dbgPort.get := regfile
  }
}
```

(We built this same register file, without the optional port, in
[Chapter 2](../ch02-basic-components/README.md).)

Note where the decision happens: `if (debug)` is **Scala**, evaluated while the
hardware is being constructed, so it chooses *what to build* — it is not a
multiplexer. `io.dbgPort` is a Scala `Option[Vec[UInt]]`, not a hardware
signal, and `.get` unwraps it. Guarding the assignment with `if (debug)` is
what keeps `.get` from ever running on a `None`.

On the tester side, the optional port is unwrapped the same way, with `.get`:

`src/test/scala/RegisterFileTest.scala`
```scala
      dut.io.dbgPort.get(4).expect(123.U)
```

The whole register file is visible at once through that one port, which is what
makes it worth having in a tester:

`src/test/scala/RegisterFileTest.scala`
```scala
      // Every register is visible at once through the debug port.
      dut.io.dbgPort.get(4).expect(123.U)
      dut.io.dbgPort.get(2).expect(456.U)
      dut.io.dbgPort.get(7).expect(0.U)
```

Build the same module with `debug = false` and the port is simply not there.
Two things follow, both asserted in the test:

`src/test/scala/RegisterFileTest.scala`
```scala
  it should "raise an exception when the missing port is unwrapped" in {
    test(new RegisterFile(false)) { dut =>
      assert(dut.io.dbgPort.isEmpty)
      intercept[NoSuchElementException] {
        dut.io.dbgPort.get(4).expect(123.U)
      }
    }
  }
```

First, reaching for the port anyway is a plain Scala `NoSuchElementException`
(`None.get`) raised while the test elaborates — not a hardware fault, and not
something the Chisel compiler can catch for you. Second, the `None` port costs
*nothing*: it leaves no trace in the generated Verilog, which is the entire
point of making it optional.

---

## 10.5 Inheritance

`Module` is a Scala class, so use inheritance to share an interface. An abstract
`Ticker` fixes the `io`; three subclasses implement tick generation differently
(up, down, nerd) — and a single generic test drives all of them:

`src/main/scala/Ticker.scala`
```scala
abstract class Ticker(n: Int) extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
  })
}
```

Each subclass generates the tick its own way — counting up, counting down, or
counting down to `-1` to avoid a comparator:

`src/main/scala/Ticker.scala`
```scala
// Tick generation by counting up.
class UpTicker(n: Int) extends Ticker(n) {
  val N = (n - 1).U
  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  val tick = cntReg === N
  when(tick) {
    cntReg := 0.U
  }
  io.tick := tick
}

// Tick generation by counting down to 0.
class DownTicker(n: Int) extends Ticker(n) {
  val N = (n - 1).U
  val cntReg = RegInit(N)
  cntReg := cntReg - 1.U
  when(cntReg === 0.U) {
    cntReg := N
  }
  io.tick := cntReg === N
}

// The "nerd" version: count down to -1 to avoid a comparator.
class NerdTicker(n: Int) extends Ticker(n) {
  val N = n
  val MAX = (N - 2).S(8.W)
  val cntReg = RegInit(MAX)
  io.tick := false.B
  cntReg := cntReg - 1.S
  when(cntReg(7)) {
    cntReg := MAX
    io.tick := true.B
  }
}
```

*Scala note — an `abstract class` with constructor parameters → [§A.5](../SCALA-NOTES.md#a5-abstract-class-with-constructor-parameters).*

The tester takes `[T <: Ticker]`, so it accepts any implementation:

`src/test/scala/TickerTest.scala`
```scala
trait TickerTestFunc {
  def testFn[T <: Ticker](dut: T, n: Int) = {
    var count = -1   // -1 means no tick seen yet
    for (_ <- 0 to n * 3) {
      if (count > 0)
        dut.io.tick.expect(false.B)
      else if (count == 0)
        dut.io.tick.expect(true.B)

      if (dut.io.tick.peekBoolean())
        count = n - 1
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

Every generator so far was driven by a loop or an `if`. But a Chisel `Vec` is
also a Scala collection, which means Scala's **higher-order functions** — methods
that take another *function* as an argument — apply to hardware: `map`, `zip`,
`reduce` and friends. That flips how a regular structure is described. Instead of
writing the wiring loop, you hand over a function that combines *two* signals and
let the collection method build the structure that applies it across all of them.

The rest of this intro is the machinery — `reduce`, function literals, and
`reduceTree` — all of it in one small module, `FunctionalAdd`. Two worked examples
then follow: a **minimum search** over a `Vec`
([§10.6.1](#1061-minimum-search)) and an **arbitration tree** built from nothing
but 2:1 arbiters ([§10.6.2](#1062-an-arbitration-tree)).

Start with the simplest case, summing a `Vec`: define an `add` function and fold
the vector with Scala's `reduce`, which combines the first two elements, then
combines that result with the next, and so on until one value remains:

`src/main/scala/FunctionalAdd.scala`
```scala
  def add(a: UInt, b: UInt) = a + b
  val sumNamed = vec.reduce(add)
```

Naming that function is optional. The combining function can be written straight
into the call as an anonymous **function literal** — parameters in parentheses,
then `=>`, then the body:

*illustrative — the shape of a function literal*
```scala
// (param) => function body
val sumLiteral = vec.reduce((a: UInt, b: UInt) => a + b)
```

Filled in for the adder above, the parameters are the two operands and the body
is their sum, so the literal reads `(a: UInt, b: UInt) => a + b` and the whole
`add` definition disappears into the `reduce` call:

`src/main/scala/FunctionalAdd.scala`
```scala
  val sumLiteral = vec.reduce((a: UInt, b: UInt) => a + b)
```

When the parameters are used once each, in order, Scala's `_` wildcard can stand
in for them and the types are inferred from the collection — which collapses the
literal to just the operator:

`src/main/scala/FunctionalAdd.scala`
```scala
  val sum = vec.reduceTree(_ + _)
```

*Scala note — higher-order functions → [§E.4](../SCALA-NOTES.md#e4-higher-order-functions) and the `_` placeholder (point-free) → [§E.3](../SCALA-NOTES.md#e3-the-_-placeholder-point-free-style).*

That last line also swaps `reduce` for **`reduceTree`**, which is the other half
of the story. `reduce` folds left, producing a *chain* of adders —
`((((in0+in1)+in2)+in3)+in4)` — whose combinational delay grows as `O(n)`.
`reduceTree` builds a balanced *tree* instead, `O(log n)` deep, which is what you
want for a wide sum. Same combining function, different structure generated from
it.

All three spellings above are in the same module, wired to their own outputs, so
the test can confirm they really do describe one adder network:

```
$ sbt "testOnly FunctionalAddTest"
[info] FunctionalAddTest:
[info] FunctionalAdd
[info] - should sum the vector
[info] - should give the same sum for all three spellings
[info] Tests: succeeded 2, failed 0, canceled 0, ignored 0, pending 0
```

One caveat to carry forward: `reduceTree` is Chisel's own method on `Vec` (part
of `chisel3._`, no extra import needed), *not* a Scala collection method — Scala
has `reduce` but no `reduceTree`. That distinction bites as soon as a reduction
leaves Chisel's collections, which [§10.6.1](#1061-minimum-search) runs into.

### 10.6.1 Minimum search

Nothing about `reduceTree` is specific to addition: it takes any function of two
elements. Passing a `Mux` instead of an adder turns the same call into a
minimum-search tree, the first of four variants in `FunctionalMin`:

`src/main/scala/FunctionalMin.scala`
```scala
  // (a) minimum value only: reduceTree with a Mux.
  val min = vec.reduceTree((x, y) => Mux(x < y, x, y))
```

Each tree node keeps the smaller of its two inputs, so the root ends up with the
smallest of all `n` — an `n`-input minimum built from one line and `n-1` muxes.

Often the **index** of the minimum is wanted as well, and that is where the
reduction needs to carry more than one value per element. Two ways to do it. The
first packs value and index into a `Bundle` and reduces over that, comparing on
the `.v` field while both fields travel together:

`src/main/scala/FunctionalMin.scala`
```scala
  // (b) value AND index, using a Bundle to carry both.
  class Two extends Bundle {
    val v = UInt(w.W)
    val idx = UInt(8.W)
  }
  val vecTwo = Wire(Vec(n, new Two()))
  for (i <- 0 until n) {
    vecTwo(i).v := vec(i)
    vecTwo(i).idx := i.U
  }
  val res = vecTwo.reduceTree((x, y) => Mux(x.v < y.v, x, y))
```

Note the indices are *constants* baked in during generation (`i.U`), not hardware
counters — the `for` loop runs at elaboration time.

The second way uses Scala **tuples** + `zipWithIndex` and avoids declaring a
`Bundle`, at the price of writing the comparison twice:

`src/main/scala/FunctionalMin.scala`
```scala
  // (c) value AND index, using Scala tuples + zipWithIndex + reduce.
  val resFun = vec.zipWithIndex
    .map((x) => (x._1, x._2.U))
    .reduce((x, y) => (Mux(x._1 < y._1, x._1, y._1),
      Mux(x._1 < y._1, x._2, y._2)))
```

Read it as a **chain of functions** — `zipWithIndex`, then `map`, then
`reduce` — each one feeding the next. Chaining functions this way is a typical
functional-programming pattern, and can equally be read as a pipeline of
operations on the collection.

Here `zipWithIndex` turns the `Vec[UInt]` into a Scala `Vector` of tuples
`(UInt, Int)`. (In general, `zip` merges two sequences into a single one whose
elements are pairs; `zipWithIndex` is the special case that pairs each element
with its own position.) The result is *still* a Scala `Vector`, not a Chisel
`Vec` — so it must use `reduce`, **not** `reduceTree`, which only exists on
Chisel's `Vec`.

**Why the comparison appears twice.** That `reduce` looks redundant — the same
`x._1 < y._1` written in both muxes — but it has to be. A Scala tuple is not
Chisel `Data`, so there is no single `Mux` that can select *a pair*; the value and
the index must each be muxed on their own. What makes the result correct is that
both muxes are driven by the **same** condition, so the surviving value and the
surviving index always come from the same element. Writing two *different*
conditions there would be the bug.

> **Ties.** All four variants compare with a strict `<`, so on a tie the *later*
> element wins and the index returned is that of the **last** minimum. The
> pure-Scala model below uses `<=` and keeps the **first**. With
> `Seq(1, 0, 3, 2, 0, 5)` the hardware reports index 4 while
> `ScalaFunctionalMin.findMin` reports index 1 — the two agree only when the
> minimum is unique, which is why the bench pokes a vector with a unique minimum.

To keep using `reduceTree`, swap the Scala tuple for a Chisel **`MixedVec`**
(a fixed-size, indexable collection whose elements can have different
types — like a tuple, but usable as an actual Chisel collection):

`src/main/scala/FunctionalMin.scala`
```scala
  // (d) a Chisel MixedVec carries value and index like a tuple would, but IS a
  //     Chisel collection - so reduceTree is available again.
  val scalaVector = vec.zipWithIndex
    .map((x) => MixedVecInit(x._1, x._2.U(8.W)))
  val resFun2 = VecInit(scalaVector)
    .reduceTree((x, y) => Mux(x(0) < y(0), x, y))
```

`MixedVecInit` comes from `chisel3.util._`, so that import joins `chisel3._` at
the top of the file. `map` still yields a Scala `Vector`, but of `MixedVec`s;
wrapping it in `VecInit` converts it to a Chisel `Vec` and makes `reduceTree`
available again, at the cost of that extra conversion step. `resFun2` ends up a
two-element `MixedVec`, indexed like an ordinary `Vec` — `(0)` is the value and
`(1)` the index:

`src/main/scala/FunctionalMin.scala`
```scala
  io.resC := resFun2(0)
  io.idxC := resFun2(1)
```

There is a second benefit, and it is the reason (d) is more than (c) with
different syntax: a `MixedVec` **is** Chisel `Data`, so a single `Mux` selects
the whole pair — where the Scala tuple of (c) needed one `Mux` per field. (d)
recovers the property (b) had and (c) lost. The measured mux counts
[below](#comparing-the-four-in-generated-verilog) show it: at `n = 4`, (b) and
(d) use 4 muxes where (c) uses 5; at `n = 8`, 10 against 13.

The value-and-index variants (b), (c) and (d) each drive their own pair of
outputs, so one test can confirm all of them agree. And since the whole thing is a *search*, it has
an obvious pure-Scala counterpart — the same `reduce` over a `Seq[Int]`, in the
same file:

`src/main/scala/FunctionalMin.scala`
```scala
object ScalaFunctionalMin {
  def findMin(v: Seq[Int]) = {
    v.zip((0 until v.length).toList).reduce((x, y) => if (x._1 <= y._1) x else y)
  }
}
```

`src/test/scala/FunctionalMinTest.scala` checks the reference model first, then
the hardware against the same expected answer — a **pure-Scala reference model**
is a powerful testing pattern, and it is especially cheap here because the
hardware reduction and the Scala one are written the same way:

```
$ sbt "testOnly FunctionalMinTest"
[info] FunctionalMinTest:
[info] ScalaFunctionalMin (reference model)
[info] - should find the min and index
[info] a tie
[info] - should give the last index in hardware and the first in the model
[info] FunctionalMin
[info] - should find the min value and its index
[info] MinValueOnly
[info] - should find the minimum value
[info] MinBundle
[info] - should find the minimum and its index
[info] MinTuple
[info] - should find the minimum and its index
[info] MinMixedVec
[info] - should find the minimum and its index
[info] all three index variants
[info] - should report the last index on a tie
[info] Tests: succeeded 8, failed 0, canceled 0, ignored 0, pending 0
```

The second case is the tie caveat above, pinned as a test: it asserts index 4 from
all three hardware variants and index 1 from the model, so if a future edit
changes either comparison the bench says so. The last five cases belong to the
split-out variants of the next section.

#### Comparing the four in generated Verilog

Four spellings that compute the same thing raise the obvious question: do they
build the same *circuit*? `FunctionalMin` can't answer it — it elaborates all four
at once, so its Verilog is one tangle. The rest of
`src/main/scala/FunctionalMin.scala` therefore carries each variant again as its
own small module (`MinValueOnly`, `MinBundle`, `MinTuple`, `MinMixedVec`), and
the `FunctionalMinDemo` app at the end of that file emits and measures each:

```
$ sbt "runMain FunctionalMinDemo"        # 4 inputs, the default
$ sbt "runMain FunctionalMinDemo 8"      # 8 inputs
```

For four 8-bit inputs, variant **(a)** is the baseline — no index to carry, so it
is nothing but three compare-and-select stages:

```
--- (a) MinValueOnly  reduceTree + Mux ---
comparators:  3   muxes:  3   wires:  2
  wire [7:0] _io_min_T_1 = io_in_0 < io_in_1 ? io_in_0 : io_in_1;
  wire [7:0] _io_min_T_3 = io_in_2 < io_in_3 ? io_in_2 : io_in_3;
  assign io_min = _io_min_T_1 < _io_min_T_3 ? _io_min_T_1 : _io_min_T_3;
```

Read the dependencies: `_T_1` and `_T_3` are independent, so they settle in
parallel, and only the last line waits on them. That is the *tree* — two levels
deep for four inputs.

Variant **(b)** adds the index. Each comparison is now a named wire feeding two
muxes (one for the value, one for the index), and firtool reduces the index
arithmetic to a concatenation of the comparison bits:

```
--- (b) MinBundle     Bundle + reduceTree ---
comparators:  3   muxes:  4   wires:  5
  wire       _res_T = io_in_0 < io_in_1;
  wire [7:0] _res_T_1_v = _res_T ? io_in_0 : io_in_1;
  wire       _res_T_2 = io_in_2 < io_in_3;
  wire [7:0] _res_T_3_v = _res_T_2 ? io_in_2 : io_in_3;
  wire       _res_T_4 = _res_T_1_v < _res_T_3_v;
  assign io_min = _res_T_4 ? _res_T_1_v : _res_T_3_v;
  assign io_idx = _res_T_4 ? {7'h0, ~_res_T} : {7'h1, ~_res_T_2};
```

Variant **(d)**, the `MixedVec` one, comes out *identical* — same wire count, same
mux count, same two-level shape, same `{7'h0, ~…}` index expression, only the
generated names differ:

```
--- (d) MinMixedVec   MixedVec + reduceTree ---
comparators:  3   muxes:  4   wires:  5
  wire       _resFun2_T = io_in_0 < io_in_1;
  wire [7:0] _resFun2_T_1_0 = _resFun2_T ? io_in_0 : io_in_1;
  wire       _resFun2_T_2 = io_in_2 < io_in_3;
  wire [7:0] _resFun2_T_3_0 = _resFun2_T_2 ? io_in_2 : io_in_3;
  wire       _resFun2_T_4 = _resFun2_T_1_0 < _resFun2_T_3_0;
  assign io_min = _resFun2_T_4 ? _resFun2_T_1_0 : _resFun2_T_3_0;
  assign io_idx = _resFun2_T_4 ? {7'h0, ~_resFun2_T} : {7'h1, ~_resFun2_T_2};
```

So the `VecInit` conversion that bought back `reduceTree` costs **nothing** in
hardware — `MixedVec` and `Bundle` are two ways to write one netlist.

Variant **(c)** is the one that differs. Follow the chain: each stage consumes the
previous stage's output, so the comparisons are strictly sequential, and the index
needs *nested* muxes:

```
--- (c) MinTuple      Scala tuple + reduce ---
comparators:  3   muxes:  5   wires:  5
  wire       _resFun_T_2 = io_in_0 < io_in_1;
  wire [7:0] _resFun_T_1 = _resFun_T_2 ? io_in_0 : io_in_1;
  wire       _resFun_T_6 = _resFun_T_1 < io_in_2;
  wire [7:0] _resFun_T_5 = _resFun_T_6 ? _resFun_T_1 : io_in_2;
  wire       _resFun_T_9 = _resFun_T_5 < io_in_3;
  assign io_min = _resFun_T_9 ? _resFun_T_5 : io_in_3;
  assign io_idx =
    {6'h0, _resFun_T_9 ? (_resFun_T_6 ? {1'h0, ~_resFun_T_2} : 2'h2) : 2'h3};
```

**Why (c) differs and (b) ≡ (d): it is the fold, not the container.** The three
index-carrying variants differ in two independent ways, and only one of them
reaches the hardware:

| | how the pair is carried | how the sequence is folded |
|---|---|---|
| (b) | `Bundle` (`Two`) | `reduceTree` |
| (c) | Scala tuple `(UInt, UInt)` | `reduce` |
| (d) | `MixedVec` | `reduceTree` |

The **container** is erased at elaboration. A `Bundle` with an 8-bit `v` and an
8-bit `idx`, and a 2-element `MixedVec` of two 8-bit values, are the same 16 bits
of aggregate as far as FIRRTL is concerned; named fields versus positional indices
is a Scala-side distinction only. That is why (b) and (d) come out
character-for-character equivalent apart from generated names.

The **fold** is what builds structure. `reduce` is a left fold, so
`op(op(op(e0,e1),e2),e3)` — every step depends on the previous one, giving a chain.
`reduceTree` pairs elements up and recurses, giving `op(op(e0,e1),op(e2,e3))` — the
two inner ops are independent and settle in parallel.

To be sure the tuple is not the culprit, take variant (b)'s `Bundle` unchanged and
swap only the fold:

*illustrative — a control experiment, not part of the project*
```scala
  val res = vecTwo.reduce((x, y) => Mux(x.v < y.v, x, y))   // reduce, not reduceTree
```

That emits (c)'s chain exactly — sequential `_res_T` → `_res_T_2` → `_res_T_4`, and
the same nested index mux — from a `Bundle`:

```
  wire       _res_T = io_in_0 < io_in_1;
  wire [7:0] _res_T_1_v = _res_T ? io_in_0 : io_in_1;
  wire       _res_T_2 = _res_T_1_v < io_in_2;
  wire [7:0] _res_T_3_v = _res_T_2 ? _res_T_1_v : io_in_2;
  wire       _res_T_4 = _res_T_3_v < io_in_3;
  assign io_min = _res_T_4 ? _res_T_3_v : io_in_3;
  assign io_idx = _res_T_4 ? (_res_T_2 ? {7'h0, ~_res_T} : 8'h2) : 8'h3;
```

So variant (c)'s shape is not a consequence of choosing a tuple *directly*. It is a
consequence of what the tuple forces: a tuple is not `Data`, so it cannot be a
`Vec` element, so after `zipWithIndex.map` you are holding a plain Scala
`IndexedSeq` — and `reduceTree` does not exist there. Asking for it is a compile
error, not a fallback:

```
[error] value reduceTree is not a member of IndexedSeq[(chisel3.UInt, chisel3.UInt)]
```

The container choice therefore decides the fold available to you, and the fold
decides the circuit. `MixedVec` exists precisely to break that link: it *is* `Data`,
so a `Vec` of them is a Chisel collection again and `reduceTree` comes back — which
is why (d) lands on (b)'s netlist rather than (c)'s.

The **comparator count is the same in all four** — finding a minimum of `n`
values takes `n-1` comparisons however you associate them. What changes is
**depth**, and that only shows up as `n` grows:

| Variant | Shape | n = 4 | n = 8 |
|---------|-------|-------|-------|
| (a) `MinValueOnly` | tree, value only | 3 cmp / 3 mux / 2 wires | 7 cmp / 7 mux / 6 wires |
| (b) `MinBundle` | tree, value + index | 3 cmp / 4 mux / 5 wires | 7 cmp / 10 mux / 13 wires |
| (c) `MinTuple` | **chain**, value + index | 3 cmp / 5 mux / 5 wires | 7 cmp / **13** mux / 13 wires |
| (d) `MinMixedVec` | tree, value + index | 3 cmp / 4 mux / 5 wires | 7 cmp / 10 mux / 13 wires |

At `n = 8` the chain is seven compare-and-select stages back to back — one
critical path through all of them — where the trees are three levels. `sbt
"runMain FunctionalMinDemo 8"` prints it plainly: `_resFun_T_2` → `_T_6` → `_T_10` → `_T_14`
→ `_T_18` → `_T_22` → `_T_25` → `io_min`, each waiting on the last.

That is the practical takeaway of [§10.6](#106-functional-programming): the
combining function decides *what* is computed, the fold decides the *shape* it is
computed in, and the container decides which folds you are allowed to use. Wherever
`reduceTree` is available, prefer it — and `MixedVec` is the way to keep it
available when an index has to ride along.

The second half of `src/test/scala/FunctionalMinTest.scala` keeps the four
honest: each finds minimum `1` at index `2` in `Seq(3, 5, 1, 7)`, and all three
index variants agree on the tie rule (last index) despite the different
associativity.

### 10.6.2 An arbitration tree

`reduceTree` also builds an arbitration tree out of nothing but 2:1 arbiters.
One class holds the whole generator: the interface — a `Vec` of ready/valid
(`DecoupledIO`) inputs reduced to a single ready/valid output — and the one line
that turns a 2:1 arbitration function into an `n`-input tree.

`src/main/scala/ArbiterTree.scala`
```scala
class Arbiter[T <: Data: Manifest](
    n: Int,
    gen: T,
    arbitrate: (DecoupledIO[T], DecoupledIO[T]) => DecoupledIO[T]
) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new DecoupledIO(gen)))
    val out = new DecoupledIO(gen)
  })

  io.out <> io.in.reduceTree((a, b) => arbitrate(a, b))
}
```

That last line *is* the tree. Everything specific to *how* two requests are
arbitrated arrives as `arbitrate` — an ordinary function value — so one class
covers both arbiters:

*illustrative*
```scala
new Arbiter(4, UInt(8.W), arbitrateSimp)   // priority
new Arbiter(4, UInt(8.W), arbitrateFair)   // fair
```

and the two named trees are nothing but the base class with one function
plugged in:

`src/main/scala/ArbiterTree.scala`
```scala
class ArbiterSimpleTree[T <: Data: Manifest](n: Int, gen: T)
  extends Arbiter(n, gen, arbitrateSimp[T])

class ArbiterTree[T <: Data: Manifest](n: Int, gen: T)
  extends Arbiter(n, gen, arbitrateFair[T])
```

> **`: Manifest` — inherited, and not needed here.** The context bound comes
> straight from the book's `Arbiter`, where it supports a hand-written tree
> helper built on Scala arrays. Nothing in this chapter's version uses it:
> deleting it from all three classes still compiles and passes the tests.
> It is kept so the class matches the book's listing.

> **A note on the arrangement.** The book defines each arbitration function
> *inside* its own subclass of `Arbiter` and repeats the `reduceTree` line in
> both. This chapter instead keeps the two functions together in an
> `Arbitration` object and passes one in, which says the same thing more
> directly — the combining function is a value, and it is the only difference
> between the two arbiters — and keeps the tree line in exactly one place. The
> generated hardware is unaffected: both arrangements elaborate to byte-identical
> FIRRTL and SystemVerilog (checked by diffing both trees before and after).
> Because the functions no longer live inside a class, each one derives its data
> type from its own argument with `chiselTypeOf(a.bits)` instead of closing over
> `gen`.

The two functions are wrapped in an `object Arbitration` for a reason that is
easy to trip over: Scala 2 has **no top-level `def`** — a bare function at the
top level of a file is a compile error, so a free function must live in an
`object` (or be a method of a class, as in the book's version).

*Scala note — an `object` as a namespace, and why a free `def` needs one → [§A.7](../SCALA-NOTES.md#a7-object-as-a-namespace--companion-object); passing a function as a parameter → [§E.4](../SCALA-NOTES.md#e4-higher-order-functions).*

All that is left is to write the two 2:1 arbitration functions themselves. They
live side by side in the `Arbitration` object, priority first:

> **About the waveforms below.** Each diagram below shows **one 2:1 node** (an
> `Arbiter(2, UInt(8.W))`, i.e. a tree of a single node), with `in(0)` playing
> the role of `a` and `in(1)` of `b`. Every value in them was **recorded from a
> real simulation** — a temporary chiseltest bench peeked every port each cycle;
> the internal registers are shown too, since each one is observable from the
> ports (`in(0).ready` *is* `regReadyA`, `out.bits` *is* `regData`, `out.valid`
> is `!regEmpty`, and the fair arbiter's `regState` follows uniquely from
> `ready`/`valid`/`bits`). To get the real trace rather than this rendering, run
> `sbt "testOnly ArbiterWaveTest"` — see
> [Recording your own waveforms](#recording-your-own-waveforms) at the end of
> this section.

#### Simple Arbitration

The arbiter meant by "the arbiter of the earlier chapter" is the one from
[§5.4 Arbiter](../ch05-combinational-building-blocks/README.md#54-arbiter) —
`Arbiter3` / `Arbiter3Loop`, which take a `request` bit vector straight to a
`grant` bit vector, lower index wins. That circuit is **purely combinational**:
no registers, the grant is a function of the request in the same cycle.

It cannot be reused here, because the ports of this arbiter are not raw
request/grant bits but **ready/valid handshakes**
([§9.3](../ch09-communicating-state-machines/README.md#93-the-readyvalid-interface)),
and the handshake forbids a combinational path from `ready` back to `valid`. A
combinational grant would be exactly that path — `out.ready` would feed
`in.ready`, which the producer may use to compute `valid` — and stacking such
nodes into a tree would build one long combinational chain, or a loop. So the
decision has to be **registered**, which means this arbiter needs state.

That state comes with two assumptions about the protocol, which the function
below relies on:

1. a requester that has asserted `valid` holds it until the receiver takes the
   word (signals `ready`) — it may not withdraw a request;
2. `ready` may be asserted in the clock cycle *after* the one in which `valid`
   was seen.

This is one specific interpretation of the ready/valid protocol — the same one
used by **AXI**. Other interpretations exist, which is why the book spells this
one out before the code:

`src/main/scala/ArbiterTree.scala`
```scala
  def arbitrateSimp[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {

    val regData = Reg(chiselTypeOf(a.bits))
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))

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

**The shape of it.** Ignore the arbitration for a second and this is the
one-word buffer of [§9.3](../ch09-communicating-state-machines/README.md#93-the-readyvalid-interface):
one data register plus an "empty" flag, `DecoupledIO` on each side. The
arbitration is a *front door* bolted onto that buffer — two inputs competing
for one slot, and a registered grant deciding who gets it.

Four registers, two jobs:

| register | job |
|---|---|
| `regData` | the one storage slot — the word being passed along |
| `regEmpty` | is the slot free? (`out.valid` is just `!regEmpty`) |
| `regReadyA` | input `a` has been *promised* the slot; it is `a.ready` |
| `regReadyB` | the same for input `b` |

Note that `regReadyA`/`regReadyB` **are** the `ready` outputs — `a.ready :=
regReadyA` — which is what "registering the decision" means concretely. The
grant is decided one cycle, then presented the next.

**Each word travels through three phases.** Follow one word from input `a`
(the snippets below are condensed from `src/main/scala/ArbiterTree.scala` — the
statements are the file's, the layout is squeezed onto one line where it fits):

1. **Decide.** The slot is free and an input is asking, so promise the slot to
   one of them — a single `when`/`.elsewhen` chain that tests `a` first:
   ```scala
   when (a.valid & regEmpty & !regReadyB) {
     regReadyA := true.B
   } .elsewhen (b.valid & regEmpty & !regReadyA) {
     regReadyB := true.B
   }
   ```
   The `!regReadyB` / `!regReadyA` guards keep the promise exclusive — there is
   only one slot, so at most one input may hold a grant at a time.

   **This chain is the whole reason it is a *priority* arbiter.** Whenever the
   slot is free and `a` is asking, the first branch is taken and the `.elsewhen`
   is never evaluated, so `a` always wins and a busy `a` starves `b` completely
   — exactly what the waveform below shows, and what the fair version in the
   next subsection fixes by adding state that remembers whose turn it is.

2. **Capture.** Next cycle `a.ready` is high. Assumption 1 above guarantees `a`
   is still holding `valid` with the same `bits`, so the word can simply be
   taken: store it, mark the slot full, and drop the grant.
   ```scala
   when (regReadyA) { regData := a.bits; regEmpty := false.B; regReadyA := false.B }
   ```

3. **Hand over.** With the slot full, `out.valid` is high and `out.bits` is the
   stored word. When the consumer answers with `out.ready`, the slot frees up
   and the cycle can start again:
   ```scala
   out.valid := !regEmpty
   when (out.ready) { regEmpty := true.B }
   ```

**One subtlety worth pausing on** — it is **cycle 1** of the diagram below (and
cycles 4, 7, 10: every cycle in which `in(0).ready` is high).

Start from the rule that makes it happen: in Chisel, `someReg := value` does
**not** change the register during the current cycle. It describes the value the
register will take *at the next clock edge*. Everything a cycle reads is the
register's **old** value.

So in the capture cycle, phase 2 runs `regEmpty := false.B` — but `regEmpty`
still *reads* as `1` for the whole of that cycle. And the phase-1 decide chain
reads `regEmpty`. Its condition `a.valid & regEmpty & !regReadyB` is therefore
**still true**, and it fires a second time, scheduling `regReadyA := true.B`.

Now two statements in the same cycle target the same register:

| order in the code | statement | phase |
|---|---|---|
| earlier | `regReadyA := true.B` | 1, decide — fires again because `regEmpty` still reads `1` |
| later | `regReadyA := false.B` | 2, capture |

Chisel resolves this by **last connection wins**: the later statement is the one
that takes effect, so `regReadyA` goes to `0` at the next edge. That is exactly
what you see in the figure — `in(0).ready` (which *is* `regReadyA`) is high for
**one cycle only**, cycle 1, and is back low in cycle 2. Had the earlier
assignment won instead, `ready` would stay high and the arbiter would keep
re-accepting `a` forever.

The same rule is what lets you read these `when` chains straight down the page:
a later statement overrides an earlier one, so the last assignment to a signal is
the one that counts.

**So does the order of the `when` blocks matter? Yes — for this signal.** It is
worth proving to yourself rather than taking on faith, because "the register
just goes to `0` at the edge" feels like it should be true regardless.
[Appendix A](#appendix-a--statement-order-in-arbitratesimp) builds the arbiter
twice from the same statements in the two orders and compares them, down to the
emitted SystemVerilog: swapped, the arbiter grants input `a` **8 times while
forwarding only 4 words**.

#### Writing it so the order cannot matter

Depending on statement order for correctness is a fragile way to write this.
Nothing in `arbitrateSimp` announces that two blocks collide; a reader has to
discover it, and a later edit that reorders the blocks silently changes the
hardware. The safer construction is to make the collision impossible — then the
order is free, and the reader does not need the rule at all.

One line does it. The decide chain should fire only when **no grant is
outstanding**:

`src/main/scala/ArbiterVariants.scala`
```scala
    val noGrant = !regReadyA & !regReadyB          // <- the whole fix

    when (a.valid & regEmpty & noGrant) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & noGrant) {
      regReadyB := true.B
    }
```

`noGrant` is false in exactly the cycle the capture block fires, so decide and
capture can never both assign `regReadyA`. Nothing is left for last-connect-wins
to resolve, and the statements may be written in either order. The same test
file checks both halves of that claim against 200 cycles of stimulus in which
the inputs idle and the consumer stalls:

```
$ sbt "testOnly ArbiterOrderTest"
order-free vs arbitrateSimp: 200 cycles, 0 mismatching
order-free, decide-first vs capture-first: 200 cycles, 0 mismatching
```

Identical behaviour to the book's version, and immune to the swap that breaks
it. This is the same principle the **fair** arbiter uses in the next subsection,
where `switch`/`is` makes the cases mutually exclusive *by construction* — which
is why reordering its branches changes nothing (also verified, 200 cycles).

> **One caveat about "same functionality".** The rewrite fixes the *ordering*
> fragility, not the second defect described below: `regEmpty` is still cleared
> by `when (out.ready)` rather than by `when (out.valid & out.ready)`, so a
> consumer that parks `ready` high still gets nothing. Fixing that one changes
> behaviour on purpose — measured, the parked-consumer case goes from **0 words
> delivered to 8** — which is why it is a separate change rather than part of
> the order-free rewrite.

The chapter keeps the book's `arbitrateSimp` as the code under discussion, so
`ArbiterTree.scala` holds only the two arbiters the book prints. The rewrite and
the deliberately-worse variant live apart in
`src/main/scala/ArbiterVariants.scala`, and the measurements in
`src/test/scala/ArbiterOrderTest.scala`.

**Cycle by cycle.** Both inputs request forever (`a` sends `1`, `b` sends `2`,
both hold `valid` high), and the consumer plays a correct handshake: it asserts
`out.ready` only in a cycle in which it sees `out.valid`.

<p align="center">
  <img src="figures/arbiter-priority-wave.png" alt="Priority 2:1 arbiter waveform: input b never gets a ready" width="820">
</p>

***Timing diagram — the priority arbiter (`arbitrateSimp`), both inputs
requesting.*** It is the three phases above, one per cycle, repeating with a
**three-cycle period** — so each phase is a lane you can pick out by eye:

| cycles | phase | what to look for in the trace |
|---|---|---|
| 0, 3, 6, 9 | decide | `regEmpty` high, both `ready` low — the grant is being registered *for the next* cycle |
| 1, 4, 7, 10 | capture | `in(0).ready` high while `regEmpty` is **still** high — the cycle the subtlety above is about |
| 2, 5, 8, 11 | hand over | `regEmpty` low, `out.valid` high with `out.bits = 1`, and the consumer answering with `out.ready` |

Two things stand out. First, the throughput of one node is **one word every
three cycles** with this consumer — the decide, the capture and the handover
each cost a cycle. (The wave test bears this out: 8 words accepted in 24
cycles.) Second, and the section's real point:
**`in(1).ready` never rises at all.** Every time `regEmpty` is high, `a.valid`
is high and `regReadyB` is low, so the `when`/`.elsewhen` chain always takes
the first branch. Input `b` is locked out forever — that is the starvation the
test measures.

#### Fair Arbitration

A priority arbiter lets a high-priority requester dominate. A **fair** 2:1
arbiter instead remembers who won last time, using a small state machine
with two idle states (so each input gets a turn) and two "has data" states:

`src/main/scala/ArbiterTree.scala`
```scala
  def arbitrateFair[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {
    object State extends ChiselEnum {
      val idleA, idleB, hasA, hasB = Value
    }
    import State._
    val regData = Reg(chiselTypeOf(a.bits))
    val regState = RegInit(idleA)
    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))
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

One data register plus one state register are enough. In `idleA`, only input
`a` is accepted (`ready` for `a` only); if `a` isn't valid, the state moves on
to `idleB` so `b` gets a chance next. Once a request is accepted the state
moves to `hasA`/`hasB`; when the consumer takes the output (`out.ready`), the
state returns to the *other* input's idle state — guaranteeing the next
winner alternates rather than letting one input starve the other. (With just
one data register, the arbiter can only be ready for one input at a time; a
second data register would be needed to accept both inputs in the same
cycle.) Building an `Arbiter` out of these functions and `reduceTree` gives a
whole arbitration tree essentially "for free".

**Cycle by cycle.** Exactly the same stimulus as before — both inputs
requesting forever, `a` sending `1` and `b` sending `2`, consumer asserting
`out.ready` only when it sees `out.valid`:

<p align="center">
  <img src="figures/arbiter-fair-wave.png" alt="Fair 2:1 arbiter waveform: the state machine alternates between the two inputs" width="820">
</p>

***Timing diagram — the fair arbiter (`arbitrateFair`), both inputs
requesting.*** The `regState` lane is the whole story; the period here is four
cycles — one per state — but unlike the priority arbiter's three-cycle period it
delivers **two** words in that time:

| cycles | `regState` | what to look for in the trace |
|---|---|---|
| 0, 4, 8 | `idleA` | `in(0).ready` high, `in(1).ready` low — `a.bits` is captured *and* decided in this one cycle, then → `hasA` |
| 1, 5, 9 | `hasA` | `out.valid` high with `out.bits = 1`; the consumer takes it, and the state moves to `idleB` — the *other* input's turn, which is what makes it fair |
| 2, 6, 10 | `idleB` | now `in(1).ready` is the asserted one and `b.bits = 2` is captured, then → `hasB` |
| 3, 7, 11 | `hasB` | `out.bits = 2` handed over, state returns to `idleA` |

Compare the two `ready` lanes with the priority diagram: here they take turns
(and are never both high — there is only one data register), so `out.bits`
alternates `1, 2, 1, 2, …` and neither input starves. Throughput is **one word
every two cycles** (6 words in 12 cycles), against the priority arbiter's one
every three — half again as fast — because the idle state does the deciding
*and* the capturing in one cycle, instead of spending a separate cycle on a
registered `ready`.

The waveform doesn't show the "input not valid" case, because both inputs
request in every cycle here. If, say, `a` were idle in `idleA`, the `otherwise`
branch would move straight to `idleB` on the next edge, so an idle input costs
one cycle and never blocks the other one. `regData` is undefined (`x`) until the
first capture — it is a plain `Reg`, with no reset value.

#### Fair vs. priority, measured

`src/test/scala/ArbiterTreeTest.scala` drives both trees through the base class
`Arbiter[_ <: UInt]` — one tester, two implementations, the same trick as
`TickerTest`. Each of the four inputs requests forever with a distinct value
(input *i* sends *i+1*), and the test records what reaches the output.

The **fair** tree round-robins, so nothing starves:

```
fair served: List(3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4)
```

The **priority** tree serves only values `1` and `3` — inputs 1 and 3 are the
`b` side of their 2:1 node, and `a` always wins, so they never get a turn:

```
priority served: List(1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3)
```

Both lines are printed by `sbt test`. That is the section's point made
concrete, and the tests assert exactly it:
`ArbiterTree` must serve all four inputs, `ArbiterSimpleTree` must starve two.

> **A second caveat on the simple arbiter.** Its last statement is
> `when (out.ready) { regEmpty := true.B }` — it empties the data register
> whenever `ready` is high, without checking `valid`. Because that assignment
> comes *after* the capture, last-connect-wins makes it override the capture. A
> consumer that simply parks `ready` high therefore receives **nothing at all**,
> which is why the test asserts an empty output for that case. A correct
> handshake would empty the register only on `out.valid && out.ready`. The fair
> arbiter has no such problem: it moves state only on `out.ready` while in a
> `has` state.
>
> The waveform makes the deadlock obvious — same priority node as above, but with
> `out.ready` parked high from cycle 0:
>
> <p align="center">
>   <img src="figures/arbiter-priority-parked-wave.png" alt="Priority arbiter with out.ready held high: regEmpty never clears, so out.valid never rises" width="760">
> </p>
>
> ***Timing diagram — the priority arbiter with `out.ready` held high.***
> `regEmpty` is stuck at `1` for every cycle, so `out.valid` never rises and the
> consumer receives nothing. Data *is* accepted — `in(0).ready` pulses every
> other cycle and `regData` becomes `1` — it is just thrown away each time,
> because the later `when (out.ready) { regEmpty := true.B }` overrides the
> `regEmpty := false.B` from the capture. Note also that `in(0).ready` now
> pulses every second cycle rather than every third: the arbiter thinks it is
> empty and keeps re-accepting `a`.

#### Recording your own waveforms

The diagrams above are drawn in the README, but you can produce the real thing —
a `.vcd` you can scrub through in a waveform viewer — straight from sbt.
`src/test/scala/ArbiterWaveTest.scala` attaches chiseltest's
`WriteVcdAnnotation` (the idiom from
[Chapter 3](../ch03-build-and-testing/README.md)) to eight scenarios, pairing the
**same stimulus** across the priority and the fair arbiter so the two can be put
side by side:

```
$ sbt "testOnly ArbiterWaveTest"
[wave] priority, both requesting  -> List(1, 1, 1, 1, 1, 1, 1, 1)
[wave] fair, both requesting      -> List(1, 2, 1, 2, 1, 2, 1, 2, 1, 2, 1, 2)
[wave] priority, only b requesting-> List(2, 2, 2, 2, 2, 2, 2, 2)
[wave] fair, only b requesting    -> List(2, 2, 2, 2, 2, 2, 2, 2)
[wave] priority, slow consumer    -> List(1, 1, 1, 1, 1, 1)
[wave] fair, slow consumer        -> List(1, 2, 1, 2, 1, 2, 1, 2)
[wave] priority, 4-input tree     -> List(1, 3, 1, 3, 1, 3, 1, 3, 1, 3, 1, 3)
[wave] fair, 4-input tree         -> List(3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4, 2, 3, 1, 4)
[info] Tests: succeeded 8, failed 0, canceled 0, ignored 0, pending 0
```

Those eight lines are the whole section in miniature. Input *i* sends the value
*i+1*, and the consumer is correct by construction — it asserts `ready` only in
a cycle in which it sees `valid`:

| scenario | priority (`ArbiterSimpleTree`) | fair (`ArbiterTree`) |
|---|---|---|
| both inputs requesting | `1,1,1,…` — `b` never wins | `1,2,1,2,…` — strict alternation |
| only `b` requesting | `2,2,2,…` — served fine | `2,2,2,…` |
| slow consumer (takes every 4th word) | `1,1,1,…` | `1,2,1,2,…` |
| 4-input tree, all requesting | `1,3,1,3,…` — inputs 1 and 3 starve | `3,1,4,2,…` — all four |

The second row is worth dwelling on: **priority is not the same as starvation.**
With `a` idle, the low-priority input is served at full rate; `b` only loses when
it is actually competing.

Each test writes its own file under `test_run_dir/`:

```
test_run_dir/priority_2to1_both_requesting_should_record_a_waveform_showing_b_starved/ArbiterSimpleTree.vcd
test_run_dir/fair_2to1_both_requesting_should_record_a_waveform_showing_alternation/ArbiterTree.vcd
test_run_dir/priority_2to1_only_b_requesting_should_record_a_waveform_showing_b_served/ArbiterSimpleTree.vcd
test_run_dir/fair_2to1_only_b_requesting_should_record_a_waveform_showing_b_served/ArbiterTree.vcd
test_run_dir/priority_2to1_slow_consumer_should_record_a_waveform_with_backpressure/ArbiterSimpleTree.vcd
test_run_dir/fair_2to1_slow_consumer_should_record_a_waveform_with_backpressure/ArbiterTree.vcd
test_run_dir/priority_4input_tree_should_record_a_waveform_of_the_whole_tree/ArbiterSimpleTree.vcd
test_run_dir/fair_4input_tree_should_record_a_waveform_of_the_whole_tree/ArbiterTree.vcd
```

Open one with GTKWave (or Surfer):

```
$ gtkwave test_run_dir/fair_2to1_both_requesting_should_record_a_waveform_showing_alternation/ArbiterTree.vcd
```

The dump includes the **internal registers**, not just the ports — the fair
arbiter's `io_out_regState` and the priority arbiter's `regEmpty` /
`regReadyA` / `regReadyB` are all there, so the three phases described above can
be followed signal by signal. (`test_run_dir/` is in `.gitignore`, so the traces
stay local.)

To add a scenario of your own, copy one of the eight tests and change the
`requesting` set or `acceptEvery`; the helper drives the rest.

---

## 10.7 Build, run, and check

```
$ sbt test
```

Expected tail (51 tests across 15 suites — `BcdTableTest.scala` holds two
suites, the `should` form and the `behavior of` form):

```
[info] Total number of tests run: 51
[info] Suites: completed 15, aborted 0
[info] Tests: succeeded 51, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Note that `sbt test` also runs `ArbiterWaveTest`, which leaves eight `.vcd`
traces under `test_run_dir/` — see
[Recording your own waveforms](#recording-your-own-waveforms).

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits fourteen files **into `generated/`**: `FunctionalComp.sv`,
`FunctionalAdd.sv`, `BcdTable.sv`, `GenHardware.sv`, `UseAdder.sv`,
`ParamFunc.sv`, `FunctionalMin.sv`, **all three** `Ticker` implementations of
[§10.5](#105-inheritance) (`UpTicker.sv`, `DownTicker.sv`, `NerdTicker.sv`),
`ArbiterTree.sv` (the generated 4:1 arbitration tree), `UseParamRouter.sv` /
`UseParamRouter2.sv` (the two type-parameterized routers), and `RegisterFile.sv`
(built with `debug = false`, so with no debug port).

`BcdTable.sv` is the one to open first: the Scala `for` loop that builds the
table is nowhere in it, replaced by a fully expanded constant array. That is
this chapter's thesis in a single file — see
[`SYSTEMVERILOG-NOTES.md` §J](../SYSTEMVERILOG-NOTES.md#j-what-elaboration-erases).

**Where the output goes.** `emitVerilog` would drop every file in the project
root; `Generate` passes `--target-dir` so they are collected in one folder
instead:

`src/main/scala/Generate.scala`
```scala
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
```

The second argument of `emitVerilog` is handed straight to the Chisel/CIRCT
command line, so this is the same `--target-dir` the standalone `ChiselMain`
entry point takes (below). The directory is created on demand and is listed in
`.gitignore`, together with the two directories sbt and chiseltest create:

| directory | who writes it | what is in it |
|---|---|---|
| `generated/` | `Generate` | the emitted `.sv` files — the output you actually want |
| `target/` | sbt | compiled classes (`target/scala-2.13/classes`), test classes, the packaged jar, incremental-compile state (`zinc`), and JUnit XML test reports |
| `project/target/` | sbt | the same, for the *build definition* itself (`project/build.properties`) |
| `test_run_dir/` | chiseltest | one subdirectory per test case, holding the FIRRTL the simulator ran (`<Module>.lo.fir`) and any `.vcd` written with `WriteVcdAnnotation` |

All four are disposable: delete them and the next `sbt` run rebuilds what it
needs. `make clean` at the repo root removes all of them for every chapter.

**Emitting just one design.** All ten at once is rarely what you want. Pass a
name and only that design is elaborated:

```
$ sbt "runMain Generate UseAdder"
...
emitting generated/UseAdder.sv
```

Several names work as well, and `list` prints the available ones:

```
$ sbt "runMain Generate UseAdder ParamFunc"
...
emitting generated/UseAdder.sv
emitting generated/ParamFunc.sv
```

```
$ sbt "runMain Generate list"
...
Available designs: BcdTable, GenHardware, UseAdder, ParamFunc, FunctionalMin, UpTicker, ArbiterTree, UseParamRouter, UseParamRouter2, RegisterFile
```

A name that is not on the list generates nothing and exits non-zero, rather than
silently falling back to all of them:

```
$ sbt "runMain Generate Nope"
...
Unknown design(s): Nope
Available designs: BcdTable, GenHardware, UseAdder, ParamFunc, FunctionalMin, UpTicker, ArbiterTree, UseParamRouter, UseParamRouter2, RegisterFile
```

The selection is nothing fancier than a `Seq` of name → *function value* pairs;
wrapping each `emitVerilog` in `() => …` is what keeps an unselected design from
being elaborated when the `Seq` is built:

`src/main/scala/Generate.scala`
```scala
  val designs: Seq[(String, () => Unit)] = Seq(
    ...
    "BcdTable" -> (() => emitVerilog(new BcdTable(), opts)),
    "GenHardware" -> (() => emitVerilog(new GenHardware(), opts)),
    "UseAdder" -> (() => emitVerilog(new UseAdder(), opts)),   // ParamAdder(8) and (16)
    ...
  )
```

**One module, without touching Scala.** Chisel also ships a command-line entry
point that looks a module up by name and instantiates it reflectively:

```
$ sbt "runMain circt.stage.ChiselMain --module UseAdder --target systemverilog --target-dir generated"
```

That writes `generated/UseAdder.sv` and nothing else — handy for a module
`Generate` does not list. The catch is the reflection: it can only build a class
with a **no-argument constructor**, so it cannot be pointed at a generator that
takes parameters:

```
$ sbt "runMain circt.stage.ChiselMain --module ParamAdder --target systemverilog --target-dir generated"
Error: Unable to create instance of module 'ParamAdder'! (Does this class take parameters?)
```

There is no place on that command line to say `n = 8`. Supplying parameters
requires Scala code — which is exactly what `Generate` (or a small
`object … extends App`) is for, and why the parameterized designs in this chapter
are reached through no-argument wrappers such as `UseAdder`, `UseParamRouter`,
and `UseParamRouter2`.

And the case-class demo:

```
$ sbt "runMain ConfigDemo"
...
The width is 16
```

And the parameterized-`Bundle` demo from
[§10.4.5](#1045-parameterized-bundles), which prints the field list for the three
parameter spellings and the router's port list with and without the phantom field
(it writes no `.sv` files):

```
$ sbt "runMain PortDemo"
...
Port         (dt, no val)      fields: data, address
PortPrivate  (private val dt)  fields: data, address
PortPublic   (val dt)          fields: data, address, dt
```

And the minimum-search comparison from
[§10.6.1](#comparing-the-four-in-generated-verilog), which emits the four variants
one module at a time and reports the size of each (also no `.sv` files — it prints
to stdout). It takes the vector length as an optional argument:

```
$ sbt "runMain FunctionalMinDemo 8"
...
=== summary ===
(a) MinValueOnly  re   comparators:  7   muxes:  7   wires:  6
(b) MinBundle     Bu   comparators:  7   muxes: 10   wires: 13
(c) MinTuple      Sc   comparators:  7   muxes: 13   wires: 13
(d) MinMixedVec   Mi   comparators:  7   muxes: 10   wires: 13
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

---

## Appendix A — statement order in `arbitrateSimp`

[§10.6.1's simple arbiter](#simple-arbitration) depends on the order of two
`when` blocks. This appendix is the experiment behind that claim: the same
statements, in the two orders, compared in behaviour and in emitted Verilog.
The functions live in `src/main/scala/ArbiterVariants.scala`, the measurements
in `src/test/scala/ArbiterOrderTest.scala`.

### The two versions

`arbitrateSimp` (in `ArbiterTree.scala`) writes the **decide chain first** and
the **capture blocks second**. `arbitrateSimpSwapped` (in
`ArbiterVariants.scala`) is a copy whose only difference is that the capture
blocks come first:

`src/main/scala/ArbiterVariants.scala`
```scala
  def arbitrateSimpSwapped[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {
    ...
    // ---- capture FIRST (in arbitrateSimp this block comes second) ----------
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

    // ---- decide SECOND (in arbitrateSimp this chain comes first) ----------
    when (a.valid & regEmpty & !regReadyB) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & !regReadyA) {
      regReadyB := true.B
    }
```

Both are built from the same class — this is what taking the arbitration
function as a parameter buys — and driven with identical stimulus:

*illustrative — the two devices under test*
```scala
new Arbiter(2, UInt(8.W), arbitrateSimp[UInt])          // decide, then capture
new Arbiter(2, UInt(8.W), arbitrateSimpSwapped[UInt])   // capture, then decide
```

### The difference in behaviour

```
$ sbt "testOnly ArbiterOrderTest"
cycle              :  0  1  2  3  4  5  6  7  8  9 10 11
in(0).ready  decide-then-capture:  0  1  0  0  1  0  0  1  0  0  1  0
in(0).ready  capture-then-decide:  0  1  1  0  1  1  0  1  1  0  1  1
grant cycles: as written = 4, swapped = 8   (words delivered: 4 vs 4)
```

As written, the grant is one cycle wide and every grant yields a word. Swapped,
the grant is two cycles wide: the arbiter asserts `ready` to input `a` **8 times
but forwards only 4 words**, so a compliant producer counts eight accepted
transfers and four words are silently lost. The test asserts all three
properties, so the claim cannot rot.

### The difference in emitted SystemVerilog

`ArbiterVariants.scala` also carries a small emitter, so the comparison is
reproducible:

```
$ sbt "runMain ArbiterOrderEmit"
emitting generated/order_asWritten.sv
emitting generated/order_swapped.sv
emitting generated/order_free.sv
emitting generated/order_freeSwapped.sv

$ diff generated/order_asWritten.sv generated/order_swapped.sv
86c86
<       io_out_regReadyA <= ~io_out_regReadyA & (_io_out_T_2 | io_out_regReadyA);
---
>       io_out_regReadyA <= _io_out_T_2;
88,90c88
<         ~io_out_regReadyB
<         & (~_io_out_T_2 & io_in_1_valid & io_out_regEmpty & ~io_out_regReadyA
<            | io_out_regReadyB);
---
>         ~_io_out_T_2 & io_in_1_valid & io_out_regEmpty & ~io_out_regReadyA;
```

`_io_out_T_2` is the decide condition. Read the two right-hand sides:

| order | next value of `regReadyA` | meaning |
|---|---|---|
| decide, then capture | `~regReadyA & (T_2 \| regReadyA)` | the capture's `0` **masks** the decide — a grant always drops after one cycle |
| capture, then decide | `T_2` | the capture has vanished; the decide alone drives the register |

In the swapped build the capture's `regReadyA := false.B` contributes *nothing*
to the emitted logic — it was overridden at elaboration, so firtool never sees
it. That is last-connect-wins made visible in the final Verilog.

The FIRRTL one level up shows the same thing as nested multiplexers, later
statement outermost:

```
node _GEN_1 = mux(_io_out_T_2,      UInt<1>("h1"), io_out_regReadyA)  // decide  -> 1  (earlier)
node _GEN_5 = mux(io_out_regReadyA, UInt<1>("h0"), _GEN_1)            // capture -> 0  (later)
io_out_regReadyA <= mux(reset, UInt<1>("h0"), _GEN_5)
```

### Why the fix works: one line of algebra

Run the same comparison on the order-free rewrite of
[§10.6.1](#writing-it-so-the-order-cannot-matter) and the result is, at first
sight, disappointing — the two orders still emit *different text*:

```
$ diff generated/order_free.sv generated/order_freeSwapped.sv
88,92c88,89
<       io_out_regReadyA <= ~io_out_regReadyA & (_io_out_T_1 | io_out_regReadyA);
<       io_out_regReadyB <=
<         ~io_out_regReadyB
<         & (~_io_out_T_1 & io_in_1_valid & io_out_regEmpty & io_out_noGrant
<            | io_out_regReadyB);
---
>       io_out_regReadyA <= _io_out_T_1;
>       io_out_regReadyB <= ~_io_out_T_1 & io_in_1_valid & io_out_regEmpty & io_out_noGrant;
```

Elaboration still applies last-connect-wins, so the decide-first version still
wraps the decide result in the capture's mask. What changed is what that mask is
worth. Both conditions are in the emitted code:

```
$ grep -E "noGrant =|_io_out_T_1 =" generated/order_free.sv
      io_out_noGrant = ~io_out_regReadyA & ~io_out_regReadyB;
      _io_out_T_1 = io_in_0_valid & io_out_regEmpty & io_out_noGrant;
```

`T_1` already contains `~A`. Writing `A` for `regReadyA`:

```
decide-first :  ~A & (T_1 | A)  =  ~A & T_1  =  T_1     because T_1 implies ~A
capture-first:  T_1
```

The mask is **redundant**, so the two orders compute the same function — which
is why the 200-cycle comparison finds no difference. Now do the same for the
book's version, where the guard is `!regReadyB` only:

```
$ grep "_io_out_T_2 =" generated/order_asWritten.sv
      _io_out_T_2 = io_in_0_valid & io_out_regEmpty & ~io_out_regReadyB;
```

```
decide-first :  ~A & (T_2 | A)  =  ~A & T_2
capture-first:  T_2
```

`T_2` says nothing about `A`, so the mask is **load-bearing**: the two differ
exactly when `A ∧ T_2` — the capture cycle — which is the 8-grants-for-4-words
divergence measured above.

| version | decide-first | capture-first | equal? |
|---|---|---|---|
| `arbitrateSimp` (`!regReadyB`) | `~A & T_2` | `T_2` | **no** — differ when `A ∧ T_2` |
| order-free (`noGrant`) | `~A & T_1` | `T_1` | **yes** — `T_1` implies `~A` |

That is the real justification for adding `!regReadyA` to the guard. It is not
"safer by convention": it makes the override *provably redundant*, so statement
order stops mattering as a matter of algebra rather than of care. The redundant
`~A &` that firtool leaves in the decide-first form costs nothing — logic
synthesis folds it — but it is worth knowing that the tool does not prove it
away, so "same behaviour" does not imply "same netlist text".

### What is *not* order-sensitive

> Moving `a.ready := regReadyA` to a different point in the same function
> changes **nothing** — verified by diffing the emitted SystemVerilog, which
> comes out byte-identical. Two reasons, and the second is the one that matters:
> `a.ready` is assigned exactly **once**, so there is no later statement to
> override it; and `:=` is a *connection*, not a value copy — it wires `a.ready`
> to the register's **output**, so it tracks that register wherever the line
> sits. Statement order decides *which connection survives*, not the order in
> which values are computed.
>
> Note also what the ordering tests do **not** catch: with the blocks swapped,
> `ArbiterTreeTest` still passes 5/5, because it asserts which values arrive and
> that `b` starves, not the shape of the handshake. Only the cycle-level trace
> shows it — an argument for the waveform tests of
> [§10.6.1](#recording-your-own-waveforms).

The order-free rewrite that removes the whole issue is in
[§10.6.1](#writing-it-so-the-order-cannot-matter).

---

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 9 — Communicating State Machines](../ch09-communicating-state-machines/README.md)**.
Next: **[Chapter 11 — Example Designs](../ch11-example-designs/README.md)**.
