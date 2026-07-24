# Scala Notes for Chisel

Chisel is not a new language — it is a **library written in Scala**. Every
Chisel description you write (`Module`, `IO`, `when`, `:=`, `RegNext`, …) is
just Scala code that, when it runs, *builds* a hardware graph. So to read the
tutorial fluently you need a working feel for a handful of Scala constructs.

This file is that feel: a compact reference to every Scala **language** feature
and idiom used across the chapter projects (ch01–ch15), with an explanation and
a real example copied from the tutorial. It is deliberately about *Scala itself*
— not the Chisel API. Read it once up front, then come back whenever a chapter
uses syntax you don't recognize.

> *Conventions:* file paths are relative to the tutorial root. `chNN` refers to
> the chapter folder (e.g. `ch06` = `ch06-sequential-building-blocks/`). Within
> each section, examples are ordered by **ascending tutorial chapter number**,
> so the first time you meet a construct is the first bullet. Code blocks are
> verbatim from the tutorial unless tagged `*illustrative*`. The explanations
> draw on *Programming in Scala* (5th ed., Odersky, Spoon, Venners & Sommers),
> the definitive language reference, adapted to the hardware setting.

**The one idea to internalize first:** most Scala code here runs **once, at
*elaboration* time**, to construct hardware — it is not itself hardware. A Scala
`if`, `for`, `val`, or `map` is a *build-time* instruction ("emit this wire,
repeat this connection 32 times"). The hardware equivalents are the Chisel
constructs (`when`, `Mux`, `Vec`, `Reg`). Keeping this distinction straight is
the single biggest hurdle for newcomers — see [§H](#h-scala-vs-chisel-the-elaboration-vs-hardware-line).

Where a chapter README already has a "Scala note" callout, this file points to
it rather than repeating it.

---

## Contents

- [A. Program structure & declarations](#a-program-structure--declarations)
- [B. Values, variables & methods](#b-values-variables--methods)
- [C. Types & generics](#c-types--generics)
- [D. Functions & functional programming](#d-functions--functional-programming)
- [E. Collections](#e-collections)
- [F. Pattern matching, tuples & Option](#f-pattern-matching-tuples--option)
- [G. Control flow](#g-control-flow)
- [H. Scala vs. Chisel: the elaboration-vs-hardware line](#h-scala-vs-chisel-the-elaboration-vs-hardware-line)
- [I. Operators, literals & runtime idioms](#i-operators-literals--runtime-idioms)
- [J. ScalaTest DSL (reads like English, is really Scala)](#j-scalatest-dsl-reads-like-english-is-really-scala)
- [K. What the tutorial does *not* use](#k-what-the-tutorial-does-not-use)
- [Where the chapters already explain Scala](#where-the-chapters-already-explain-scala)

---

## A. Program structure & declarations

**`object X extends App`** *(ch01)* — a program entry point. Scala classes have
**no** static members; an `object` is Scala's replacement for Java-style
statics — a lazily-created singleton (exactly one instance, no `new`). When an
`object` shares its name with a class in the same file it is that class's
*companion*, and the two may freely access each other's private members. An
application's entry point is a standalone object with a `main(args: Array[String]): Unit`
method; mixing in the library trait `App` generates that `main` for you, so the
object body simply *is* the program. In this tutorial the pattern names the
generators (`Generate`, `HelloScala`) that elaborate a module and emit its
Verilog — the software wrapper *around* your hardware, not hardware itself.

`ch01-introduction/src/main/scala/HelloScala.scala`

```scala
object HelloScala extends App {
  println("Hello Chisel World!")
}
```

**`class` / `extends`** *(ch01)* — a class is a blueprint for objects; you
instantiate it with `new`, and inside it you place *members*: fields (declared
with `val`/`var`) that hold each instance's state, and methods (declared with
`def`) that operate on that state. Every instance gets its own copy of the
fields. An `extends` clause makes one class a subclass of another: it inherits
all the superclass's non-private members **and** becomes a subtype of it
(omitting `extends` implicitly extends `AnyRef`). A Chisel component is *just* a
class that `extends Module`; its constructor body runs at elaboration to build
the circuit.

`ch01-introduction/src/main/scala/Hello.scala`

```scala
class Hello extends Module {
  val io = IO(new Bundle { ... })
}
```

**`package` and `import`** *(ch03)* — a `package` clause at the top of a file
places its code under a namespace (mirroring the folder tree) and signals to the
compiler that code in the same package is related, so names don't collide across
a large design. `import` then lets you refer to package or object members by
their simple names. Scala's imports are more flexible than Java's: they may
appear anywhere (not just at the top), may import from **any object** and not
only packages, and may rename or hide individual members. The wildcard `_`
imports everything; importing an object's members (`import Constants._`) lets you
write `NOP` instead of `Constants.NOP`.

`ch03-build-and-testing/src/main/scala/usepack.scala`

```scala
import mypack.Abc      // single name
import mypack._        // wildcard: everything in the package
// ... or fully-qualified, with no import at all:
val x = new mypack.Abc()
```

**`trait` … mixed in with `with`** *(ch06)* — a trait is Scala's fundamental
unit of code reuse: it encapsulates method and field definitions that you *mix
into* classes with `with`, and it also defines a type. Unlike the single
inheritance of classes, a class may mix in **any number** of traits, which is
how Scala composes behaviour without C++-style multiple inheritance. Beyond
widening a thin interface into a rich one, traits support *stackable
modifications* — layering changes to a class's methods on top of one another.
The tutorial uses a trait to hold a shared test routine reused by several test
classes; ChiselTest's own `ChiselScalatestTester` is itself a trait you mix in.

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`

```scala
trait CountTest {
  def testFn[T <: Counter](c: T, n: Int) = { ... }
}
class CounterTest extends AnyFlatSpec with ChiselScalatestTester with CountTest
```

**`abstract class` with constructor parameters** *(ch10)* — a class that has an
abstract member (e.g. a method with no body) must itself be declared `abstract`
and cannot be instantiated directly; subclasses supply the missing members. Its
constructor parameters go in parentheses after the name. A related shorthand,
*parametric fields*, fuses a constructor parameter with a field of the same
purpose: prefix the parameter with `val` (or `var`) and it *becomes* a field
directly, avoiding a redundant "copy the parameter into a field" declaration. In
hardware terms an abstract class fixes a common port interface a family of
modules must implement (e.g. every "ticker" has the same I/O).

`ch10-hardware-generators/src/main/scala/Ticker.scala`

```scala
abstract class Ticker(n: Int) extends Module { ... }
```

**`case class`** *(ch10)* — a lightweight, immutable data holder. The `case`
modifier tells the compiler to generate a bundle of boilerplate: a companion
object with a factory `apply` (so you construct instances *without* `new`),
accessor methods for each constructor parameter (which become read-only fields),
and structural `toString`/`hashCode`/`equals` derived from the arguments — so two
`Config(4,2,16)` values are equal because their fields are. The compiler never
overrides methods you write yourself, and you may add your own fields and
methods. It's the idiomatic way to bundle a module's **parameters** into one
typed, comparable value.

`ch10-hardware-generators/src/main/scala/Config.scala`

```scala
case class Config(txDepth: Int, rxDepth: Int, width: Int)
// used as:  val param = Config(4, 2, 16)   // no `new`
```

**Nested class / nested object** *(ch11)* — declarations can be *nested* inside a
class, object, or even a method, scoping them to their encloser so helpers stay
private to where they're used. A standalone `object` (one with no companion
class) is also the natural place to group related utilities. Scala even lets you
reuse a name in a nested inner scope, shadowing the outer one. The tutorial's
FSMs declare their state enumeration as a nested `object` inside the Module, so
the states belong to that module and don't leak out.

`ch11-example-designs/src/main/scala/BubbleFifo.scala`

```scala
object State extends ChiselEnum {   // declared *inside* the Module
  val empty, full = Value
}
```

**`private` members** *(ch11)* — placing `private` in front of a field, method,
or nested class makes it accessible only inside the body of the class or object
that defines it, enforcing encapsulation so callers depend only on the intended
interface. Scala's `private` is stricter and more consistent than Java's: it
applies even to inner classes, so an *enclosing* class cannot reach a member
declared private in a class nested inside it.

`ch11-example-designs/src/main/scala/fifo/fifo.scala`

```scala
private class Buffer() extends Module { ... }
class FifoIO[T <: Data](private val gen: T) extends Bundle { ... }
```

**`object` as a namespace / companion object** *(ch14)* — because an `object` is
a singleton with no statics elsewhere in the language, it's the natural home for
constants and stateless helper `def`s. When it shares a name with a class it is
that class's **companion** — a place for factory methods and a `default` value,
with mutual access to each other's private members. Importing its members
(`import Constants._`) then lets you use the names bare.

`ch14-design-of-a-processor/src/main/scala/leros/shared/shared.scala`

```scala
object Constants {
  val NOP = 0x00
  // ...
}
```

**Scala's `Enumeration` + `type` alias** *(ch15)* — an enumeration is a type
restricted to a **finite set of named values**. Scala 2's library `Enumeration`
(distinct from Chisel's `ChiselEnum`) creates these by assigning `Value`; each
one carries an ordinal `.id` starting at 0 in declaration order, which you can
use as an `Int`. A `type` member, declared with the `type` keyword, names a
type: a concrete one (`type InstrType = Value`) is an alias that clarifies a
verbose type, while an abstract one is left for subclasses to define. The RISC-V
chapter uses this for instruction-format tags used purely at generation time.

`ch15-a-risc-v-pipeline/src/main/scala/wildcat/defines.scala`

```scala
object InstrType extends Enumeration {
  type InstrType = Value
  val R, I, S, SBT, U, UJ = Value
}
```

---

## B. Values, variables & methods

**`val` vs `var`** *(ch01 / ch06)* — Scala has two kinds of variable. A `val`,
once initialized, can **never** be reassigned (like a Java `final`); a `var` can
be reassigned throughout its life and supports `+=`/`-=`. Scala style prefers
`val`, and in *hardware-description* code everything is a `val` — a `val` *names
a piece of the circuit*, it doesn't "vary." (Reassignment is really a get/set
pair: every non-private `var` member of an object implicitly gets a matching
getter and setter.) Mutable `var` shows up in this tutorial **only in test
benches**, where it accumulates an expected value across simulated clock cycles —
software bookkeeping, not hardware.

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`

```scala
var count = -1
// ...later, inside the cycle loop:
count -= 1
```

**`if` as an expression** *(ch02)* — in Scala `if`/`else` *is an expression*: it
tests a condition and evaluates to the value of whichever branch runs, so it can
sit on the right-hand side of `=` (there's no ternary `?:` because `if` already
does that job). Initializing a `val` directly from an `if` — rather than
declaring a `var` and mutating it — is the functional idiom and signals to
readers that the value never changes. Here it decides, **at elaboration time**,
whether an optional debug port exists at all. Contrast Chisel's `when`, which
builds a runtime mux — see [§H](#h-scala-vs-chisel-the-elaboration-vs-hardware-line).

`ch02-basic-components/src/main/scala/RegisterFile.scala`

```scala
val dbgPort = if (debug) Some(Output(Vec(32, UInt(32.W)))) else None
```

**`new` and type inference** *(ch02 / ch03)* — `new` instantiates a class
(`new mypack.Abc()`); `case class`es and factory objects let you skip it. Scala
also *infers* the type of most `val`s from the right-hand side. Its inference is
**flow-based**: for a call `m(args)`, if the method `m`'s type is already known,
that expected type flows *into* inferring the arguments (for instance, the
parameter types of a function literal you pass in). So a very terse literal can
be inferred where the surrounding type is known — but not when the type would
have to flow the other way. (The ch02 README calls type inference out too.)

`ch03-build-and-testing/src/main/scala/usepack.scala`

```scala
val x = new mypack.Abc()   // type Abc inferred; no `: Abc` needed
```

**`def` methods** *(ch10)* — a `def` starts with the name, a parenthesized
parameter list where **every parameter must carry an explicit type** (the
compiler does *not* infer parameter types), an optional result type after a
colon, then `=` and the body. The `=` reflects the functional view that a method
defines an expression yielding a value; `Unit` is the "nothing useful" result
type (like `void`). A method is simply a function defined as a member of some
object. In Chisel a `def` that returns hardware (`UInt`, a `Bundle`, a tuple of
signals) is a *hardware generator* — calling it stamps out that sub-circuit.

`ch11-example-designs/src/main/scala/fifo/fifo.scala`

```scala
def counter(depth: Int, incr: Bool): (UInt, UInt) = { ... }
```

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`

```scala
def testOne(a: Int, b: Int, fun: Int): Unit = { ... }
```

**Block-as-expression (implicit return)** *(ch10)* — a `{ … }` block *is* an
expression: it evaluates to its **last expression**. Idiomatic Scala has no
`return` keyword — a method's result is simply the value its body ends with. Here
the generator builds a wire, conditionally drives it, and yields it as the
result.

`ch10-hardware-generators/src/main/scala/ParamFunc.scala`

```scala
def myMux[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
  val ret = WireDefault(fPath)
  when(sel) { ret := tPath }
  ret                        // <- this value is the method's result
}
```

**Named arguments** *(ch11)* — at a call site you may pass arguments by writing
each parameter's name and `=` before its value, which lets you supply them in a
different order than declared (any positional arguments must come first). This
makes calls with several same-typed parameters self-documenting —
`frequency = 1000, baudRate = 10` can't be swapped by mistake.

`ch11-example-designs/src/test/scala/uart/UartTest.scala`

```scala
test(new UartLoopback(frequency = 1000, baudRate = 10))
```

**Default arguments** *(ch12)* — a definition can give a parameter a fallback
value, so callers who omit that argument get the default. Common for "usually 1"
step counts and "off by default" feature flags, and frequently combined with
named arguments to set just the one you care about.

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`

```scala
def step(n: Int = 1) = dut.clock.step(n)   // step() means step(1)
```

`ch12-interconnect/src/main/scala/interconnect.scala`

```scala
class MemMappedRV[T <: Data](gen: T, block: Boolean = false) extends Module
```

**Nested (local) functions & closures** *(ch12)* — a `def` defined inside another
`def` or block is a *local function*, visible only there; it lets you factor code
into small helpers without polluting the namespace or exposing them to clients. A
**closure** is the function value formed from a literal that references *free*
variables (ones not among its own parameters) — it "closes over" their bindings.
Crucially, a Scala closure captures the **variables themselves, not a snapshot**,
so later changes are seen by the closure and vice versa. The tutorial's tests
define `read`/`write` helpers that capture the running `dut`.

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`

```scala
// defined inside the test block; both capture `dut` from the enclosing scope:
def read(addr: Int)            = { ... dut.io ... }
def write(addr: Int, data: Int) = { ... dut.io ... }
```

---

## C. Types & generics

**Type parameters `[T]` with an upper bound `[T <: X]`** *(ch06)* — a *generic*
is a placeholder type filled in per use, written in square brackets just as value
parameters are written in parentheses (and any value arguments then follow in
parentheses — the type argument is part of the type, the value isn't). `<:` is an
**upper bound**: `[T <: Data]` means "any `T`, as long as it is a subtype of
`Data`." The bound is exactly what lets the body call `T`'s members — without it
the compiler knows nothing about `T`, so, e.g., requiring `[T <: Ordered[T]]`
would let you compare elements. This is the mechanism behind *type-generic*
hardware: a FIFO or mux that works for **any** payload type, checked once at
compile time.

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`

```scala
def testFn[T <: Counter](c: T, n: Int) = { ... }
```

`ch11-example-designs/src/main/scala/fifo/fifo.scala`

```scala
class BubbleFifo[T <: Data](gen: T, depth: Int) extends Fifo(gen, depth) { ... }
```

> The **ch06 README (~line 259)** has a full "Scala note" walking through
> `[T <: Counter]`, upper bounds, and inference.

**Wildcard type argument `[_ <: Data]`** *(ch11)* — when you don't care *which*
subtype fills a type parameter, `_` is an anonymous placeholder: `Fifo[_ <: Data]`
means "a `Fifo` of *some* `Data` subtype." Handy for a test that accepts any
FIFO regardless of payload.

`ch11-example-designs/src/test/scala/fifo/FifoTest.scala`

```scala
def testFn[T <: Fifo[_ <: Data]](dut: T) = { ... }
```

**`type` alias** *(ch15)* — `type Name = Existing` introduces a synonym for an
existing type, to abbreviate a verbose type or clarify intent. (See the
`type InstrType = Value` example in [§A](#a-program-structure--declarations).)

---

## D. Functions & functional programming

**Function literals (lambdas) and the `=>` arrow** *(ch06)* — Scala has
*first-class functions*: you write a function as an unnamed literal
(`(x: Int) => x + 1`) and pass it around as a value. Worth distinguishing: the
literal is source-code *text*, while the function *value* it produces at run time
is an object you can store and invoke — the same class-vs-object distinction, one
level up. This is *the* most common Scala shape in the tutorial, because every
ChiselTest bench is `test(new Dut) { dut => ... }`: a function that `test` calls
with the elaborated, running DUT loaned in as `dut`. Short forms let you drop
inferable parameter types and the parentheses around a single parameter (the
expected type "targets" the inference).

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`

```scala
test(new WhenCounter(4)) { c => testFn(c, 4) }
```

Two-parameter literal (used to fold a Chisel `Vec`):

`ch10-hardware-generators/src/main/scala/functional.scala`

```scala
vec.reduceTree((x, y) => Mux(x < y, x, y))
```

> The **ch06 README (~line 287)** has a "Scala note" on `=>`, and carefully
> distinguishes it from `<-` (for-comprehension generator) and `<:` (subtype
> bound) — three arrows that look alike and mean different things.

**Closures over test state** *(ch06)* — the function you hand to `test` closes
over `val`s and `var`s declared just outside it, so the body can read the DUT and
update expected-value bookkeeping (the `var count` from [§B](#b-values-variables--methods))
across cycles. Because closures capture the variable, not a copy, each iteration
sees the updated count.

**The `_` placeholder (point-free style)** *(ch10)* — inside a function literal,
each `_` stands for a successive parameter, filled in at each invocation. So
`_ + _` is shorthand for `(x, y) => x + y`, `_ > 0` for `x => x > 0`, and `_.U`
for `x => x.U`. It works only when each parameter appears **exactly once** in the
literal; reach for the explicit `param => …` form otherwise.

`ch10-hardware-generators/src/main/scala/functional.scala`

```scala
val sum = vec.reduceTree(_ + _)
```

`ch10-hardware-generators/src/main/scala/GenHardware.scala`

```scala
val text = VecInit(msg.map(_.U))   // each Char -> a Chisel UInt literal
```

> The **ch10 README (§10.6, ~lines 584–607)** explains function-literal syntax
> and the `_` wildcard in prose.

**Higher-order functions** *(ch10)* — a function that takes (or returns) another
function. Because the *varying* part of an algorithm can be passed in as a
function value, higher-order functions let you factor out common structure and
eliminate duplicated code — the book's example unifies several nearly identical
file-search methods that differed only in a matching test. `List` (and Chisel's
`Vec`) provide many such methods for recurring patterns, expressed far more
concisely than the equivalent imperative loops; this is how you *fold* a whole
`Vec` into a tree of adders/comparators in one line.

`ch10-hardware-generators/README.md`

```scala
def add(a: UInt, b: UInt) = a + b
val sum = vec.reduce(add)      // pass the function `add` itself
```
*illustrative*

---

## E. Collections

Scala collections drive *generation*: you build a Scala `Seq`/`List` at
elaboration time and turn it into hardware with `VecInit`, `for`, `map`, etc.

**`Seq` / `List` / `Array` / `IndexedSeq` and builders** *(ch02)* — *sequence*
types hold data lined up in order, so you can ask for elements by position. The
default `List` is an immutable linked list: fast to add/remove at the front and
great for pattern matching, but *not* fast for arbitrary-index access. You most
often create one by passing the initial elements to the companion's `apply`
factory (`List(...)`, `Seq(...)`, `Array(...)`); `Seq.fill(n)(x)` builds `n`
copies of `x`, while `Array.fill(n){ block }` *runs the block* `n` times (e.g. to
instantiate `n` fresh sub-modules). `Array` is special — it maps one-to-one onto
a Java array yet is still generic and `Seq`-compatible. You use these to describe
*how many* of something to generate.

`ch02-basic-components/src/main/scala/RegisterFile.scala`

```scala
val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
```

`ch11-example-designs/src/main/scala/BubbleFifo.scala`

```scala
val buffers = Array.fill(depth) { Module(new FifoRegister(size)) }
```

**Ranges: `until` (exclusive) vs `to` (inclusive)** *(ch03 / ch05)* — a `Range`
is a collection of evenly-spaced integers, written `1 to 5` (inclusive) or
`1 until 5` (excluding the upper bound). It's what almost every `for` header
iterates over. The exclusive/inclusive choice is a frequent off-by-one source —
and, where you can, iterating a collection directly beats indexing through a
range, because it's shorter and sidesteps the off-by-one entirely.

`ch05-combinational-building-blocks/src/main/scala/arbiter.scala`

```scala
for (i <- 1 until n) { ... }
```

**`map` / `foreach` / `reduce` / `zip` / `zipWithIndex`** *(ch10)* — the
functional toolkit shared by Scala collections *and* Chisel `Vec`s. Where
imperative code mutates data in place, the functional style *transforms an
immutable collection into a new one*, and `map` is central: it applies a function
to each element and returns a new collection of the results. `foreach` runs a
side effect per element; `reduce` (`reduceTree` in Chisel) folds a sequence into
one value; `zip` pairs two sequences elementwise; `zipWithIndex` pairs each
element with its position. (A `for`-`yield` comprehension compiles down to a
`map` — same operation, different spelling.)

`ch10-hardware-generators/src/test/scala/FunctionalTest.scala`

```scala
Seq(3, 2, 0, 9, 1).zipWithIndex.foreach { case (v, i) => dut.io.in(i).poke(v.U) }
```

**String as a `Seq[Char]`** *(ch10)* — a `String` isn't literally a sequence, but
an implicit conversion wraps it (as a `WrappedString`, a kind of `IndexedSeq`) so
that all sequence operations work on its characters — `reverse`, `map`, `slice`,
and so on. That's why you can `map` over `"Hello"` to turn each `Char` into a
Chisel literal; `.length` gives the count.

`ch10-hardware-generators/src/main/scala/GenHardware.scala`

```scala
val msg  = "Hello World!"
val text = VecInit(msg.map(_.U))
val len  = msg.length.U
```

**Conversions (`.toList`, `.toIndexedSeq`, `.toInt`, `.toLong`)** *(ch14)* —
collections offer a family of `toArray`/`toList`/`toSeq`/`toIndexedSeq`/`toSet`/
`toMap` methods (and number types their own `toInt`/`toLong`) to move between
representations — e.g. widening a signed `Int` into a `Long` and masking it before
making an *unsigned* Chisel literal. Note that converting to a list or array
usually copies every element, so it can be slow on large collections.

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`

```scala
(a.toLong & 0x00ffffffffL).U
```

---

## F. Pattern matching, tuples & Option

**`Option` / `Some` / `None` / `.get`** *(ch02)* — Scala's null-free "maybe a
value." An `Option` is either `Some(x)` (present) or the `None` singleton
(absent); many library operations (like `Map.get`) return one. The idiomatic way
to take it apart is a pattern match (`case Some(s)` / `case None`); `.get`
extracts the contents when you know it's there. This is safer than Java's `null`
because forgetting the empty case is a **compile-time type error**, not a runtime
`NullPointerException`. The tutorial uses it for an *optional* debug port that
exists only when a parameter is set.

`ch02-basic-components/src/main/scala/RegisterFile.scala`

```scala
val dbgPort = if (debug) Some(Output(Vec(32, UInt(32.W)))) else None
// ...
if (debug) { io.dbgPort.get := regfile }
```

**Tuples** *(ch10)* — a tuple combines a **fixed number** of items so you can pass
them around as a whole; unlike a list its elements may have **different types**,
which makes it ideal for returning several values from a method without declaring
a class. Build one with comma-separated values in parentheses, read parts with
`._1` / `._2`, or destructure with a pattern (`val (x, y) = …`). Its type records
both the count and the element types — `(99, "x")` is a `Tuple2[Int, String]`.
Perfect for a generator `def` that returns, say, a counter value *and* its wrap
flag.

`ch10-hardware-generators/src/main/scala/functional.scala`

```scala
def compare(a: UInt, b: UInt) = { ...; (equ, gt) }   // returns a 2-tuple
val (equ, gt) = compare(io.a, io.b)                  // destructure it
```

**Pattern-matching in a lambda** *(ch10)* — patterns are allowed well beyond a
standalone `match`. A sequence of `case` clauses in braces can be used anywhere a
function literal is expected — it's essentially a literal with multiple entry
points (formally a *partial function*, which throws if applied to an unmatched
value). So `{ case (v, i) => … }` destructures each element as it arrives,
binding the tuple's parts to names. Common right after `zipWithIndex`.

`ch10-hardware-generators/src/test/scala/FunctionalTest.scala`

```scala
Seq(3, 2, 0, 9, 1).zipWithIndex.foreach { case (v, i) => dut.io.in(i).poke(v.U) }
```

**`match` / `case` / wildcard `case _`** *(ch14)* — a `match` selects among
alternatives using patterns. It's like a `switch` but far more general: it
**yields a value**, has **no fall-through** and needs no `break`, and matches on
literals, types, and destructured shapes; `case _` is the catch-all. (Case
classes exist precisely to make matching on structured objects boilerplate-free,
which is why they suit tree-like recursive data.) The Leros ALU's *golden model*
— the plain-Scala reference the hardware is tested against — is a `match`.

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`

```scala
op match {
  case 0 => a
  case 1 => a + b
  // ...
  case _ => -123 // shall not happen
}
```

---

## G. Control flow

**`for` over a range** *(ch03 / ch05)* — the workhorse loop. A generator
`x <- coll` binds a fresh `val` to each element of *any* collection in turn and
runs the body; at elaboration it *unrolls*, emitting the body's hardware (or
running the test action) once per iteration. Nested loops can be written with two
generators in one header, separated by `;`, which reads as an outer loop over an
inner loop.

`ch03-build-and-testing/src/test/scala/testing.scala` (nested form)

```scala
for (a <- 0 until 4) {
  for (b <- 0 until 4) { ... }
}
```

`ch05-combinational-building-blocks/src/main/scala/arbiter.scala`

```scala
for (i <- 1 until n) { ... }
```

**`while` loop** *(ch11 / ch12)* — the classic pre-test loop: it re-runs its body
as long as the condition holds. It's called a "loop" rather than an expression
because it yields no useful value (its result type is `Unit`), and it's really
only needed where mutable state (`var`s) is involved — the book suggests looking
for a way to avoid it when there's no strong reason. In test benches it runs the
simulation until a hardware condition holds — e.g. step the clock until an `ack`
appears (with a guard counter to avoid hanging). `.peekBoolean()` reads the DUT's
current simulated value.

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`

```scala
while (!dut.io.ack.peekBoolean()) step()
```

**Multi-generator `for`** *(ch14)* — several `<-` generators in one `for` header,
separated by `;`, iterate as nested loops — here sweeping every ALU operation ×
every operand pair to exhaustively test the ALU.

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`

```scala
for (fun <- 0 to 7; a <- values; b <- values) testOne(a, b, fun)
```

---

## H. Scala vs. Chisel: the elaboration-vs-hardware line

This is the concept that trips up every newcomer, so it gets its own section.
Your Scala program **runs once** to *build* a circuit; the circuit then runs
forever in hardware. Scala control flow shapes the circuit at build time; Chisel
control flow *is* circuitry.

| Scala (build time, runs once) | Chisel (hardware, runs every cycle) |
|---|---|
| `if (debug) ... else ...` — *decides whether to emit* hardware | `when(sel) { ... } .otherwise { ... }` — emits a **mux** that selects at runtime |
| `for (i <- 0 until 32)` — *unrolls*, emitting the body 32× | a counter register that counts at runtime |
| `val x = a + b` (Scala `Int`) — computed by the compiler | `val x = a + b` (Chisel `UInt`) — an **adder** in silicon |
| `var` accumulating a Scala list | a `Reg` accumulating a value each clock |

The **ch05 README (~line 121)** has a "Scala note" on exactly this — *"Why
`when` and not Scala's `if`?"*. The short answer: a Scala `if` picks **one**
branch to build; a `when` builds a multiplexer that chooses **at runtime**. Use
Scala `if`/`for` to *parameterize and generate* hardware; use `when`/`Mux`/`Vec`
for behaviour that varies while the chip is running.

`ch02-basic-components/src/main/scala/RegisterFile.scala` (elaboration-time `if`
that adds a port only in debug builds):

```scala
if (debug) { io.dbgPort.get := regfile }
```

For a striking parallel, *Programming in Scala* itself builds a **digital-circuit
simulator** as an embedded Scala DSL: wires carry boolean signals and *gate
boxes* (inverter, and-gate, or-gate — enough to build any circuit) transform
them. Its gate constructors are named as **nouns** and build gates as a *side
effect* rather than returning them, so the code reads as a *description* of a
circuit rather than a sequence of build actions — exactly the mindset Chisel asks
of you.

---

## I. Operators, literals & runtime idioms

**Infix method / operator notation & precedence** *(ch02)* — operators aren't a
special language feature in Scala; they're ordinary **method calls** in nicer
syntax. `1 + 2` literally means `1.+(2)`, and *any* method taking a single
argument can be written infix without a dot — so `a & b` is `a.&(b)`, and
Chisel's `##` (bit concatenation) is just a method used infix. Since there are no
built-in operators, precedence is decided by the operator's **first character**
(one starting with `*` binds tighter than one starting with `+`), and most are
left-associative — similar to but not identical to Java/C, so parenthesize when
unsure. You can even define your own operators simply by naming methods with
operator characters (which is how a Chisel type gets `+`, `&`, `##`, …).

`ch02-basic-components/src/main/scala/Logic.scala`

```scala
val logic = (a & b) | c
val word  = highByte ## lowByte
```

**Literals** *(ch02)* — a literal writes a constant value directly in code; all of
Scala's basic types have literal forms. A character literal is a Unicode
character in single quotes (`'A'`), or `\u` followed by four hex digits, or an
escape sequence. One gotcha: Scala has **no octal literals**, so an integer that
starts with `0` won't compile. Hexadecimal `0x00`, a `Long` via the `L` suffix
(`0x00ffffffffL`), `Double` like `100.0`, and underscores as digit separators are
all fine. The suffix `.U`/`.S`/`.B`/`.W` then lifts a Scala literal into a Chisel
value/width.

`ch02-basic-components/src/main/scala/Logic.scala`

```scala
val aChar = 'A'.U    // char literal, then made a Chisel UInt
```

**`assert` (Scala)** *(ch10)* — the predefined `assert` method (from `Predef`)
throws an `AssertionError` when its condition is false; a two-argument form
`assert(cond, explanation)` attaches an explanatory value to the error. It's used
here at *elaboration/test* time (distinct from Chisel's hardware `assert`, which
checks during simulation). Assertions can be globally enabled/disabled with the
JVM's `-ea`/`-da` flags, so each acts as a small built-in test against real
runtime data.

`ch10-hardware-generators/src/main/scala/Config.scala`

```scala
assert(txDepth > 0 && rxDepth > 0 && width > 0, "parameters must be larger than 0")
```

**`require`** *(ch11)* — a *precondition* is a constraint on the values passed into
a method or constructor: something the caller must satisfy. You enforce one with
the `require` method (from `Predef`), which throws an `IllegalArgumentException`
if its condition is false, **preventing construction** with invalid data. Placing
`require(depth > 0, …)` at the top of a module rejects bad parameters *before* any
hardware is built, so the object is valid from the moment it exists.

`ch11-example-designs/src/main/scala/fifo/fifo.scala`

```scala
require(depth > 0, "Number of buffer elements needs to be larger than 0")
```

**String interpolation `s"…"`** *(ch12)* — when an identifier sits immediately
before a string literal's opening quote, Scala applies that *interpolator*. The
`s` interpolator evaluates each `$`-prefixed expression (use braces `${…}` for
anything beyond a bare identifier), calls `toString`, and splices in the result;
`f` allows `printf`-style format specifiers, and `raw` skips escape processing.
It's a concise, readable alternative to concatenation, implemented by a
compile-time rewrite (and you can define your own interpolators). Used here to
build a descriptive assertion message.

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`

```scala
assert(read(i * 4) < 10, s"counter $i just started")
```

**`println` and the standard library** *(ch01, ch14)* — plain Scala output
(`println` in `HelloScala`) and ordinary stdlib utilities such as
`scala.util.Random.nextInt()` for randomized test vectors are all available in
generators and tests.

---

## J. ScalaTest DSL (reads like English, is really Scala)

The test files read like sentences, but every word is an ordinary Scala method
call in infix position (see [§I](#i-operators-literals--runtime-idioms)). ScalaTest
is the most flexible of Scala's testing options; its central concept is the
**suite** — a named collection of tests — and you shape *how* tests are written by
mixing in style and matcher traits. The tutorial uses the "tests as
specifications" (BDD) style via `AnyFlatSpec`: you write near-English specifier
clauses so that running the suite prints human-readable, spec-like output.
Decoded: `"DUT" should "pass"` is a method chain on a `String`, `in { … }` takes
the test body as a by-name block, `taggedAs (…)` attaches a tag, and
`should be(42)` is the Matchers DSL.

`ch03-build-and-testing/src/test/scala/testing.scala`

```scala
"DUT" should "pass" in { ... }
```

`ch13-debugging-testing-verification/src/test/scala/TagTest.scala`

```scala
object Unnecessary extends Tag("Unnecessary")
"Integers" should "add" taggedAs (Unnecessary) in { 17 + 25 should be(42) }
```

---

## K. What the tutorial does *not* use

So this reference doesn't over-promise, these Scala features **do not appear** in
the tutorial's own code (you may still meet them elsewhere), with a one-line
sketch of each:

- **`lazy val`** — a `val` whose right-hand side is computed on *first access*
  rather than at definition, then cached.
- **`for`-comprehensions with `yield`** — a `for` that *builds a collection*
  instead of looping for side effects; the compiler rewrites `for (x <- xs) yield f(x)`
  into `xs.map(f)`.
- **user-defined *symbolic* operators / `unary_` methods** — every symbolic
  operator you see is a Chisel library method used infix (see
  [§I](#i-operators-literals--runtime-idioms)); the tutorial never *defines* one.
- **`implicit` / `given` conversions and context parameters** — a function's
  behaviour often depends on contextual data; *context parameters* (Scala 3
  "givens", the older `implicit` in the Scala 2.13 the tutorial uses) let the
  compiler supply such an argument by type, and *implicit conversions* silently
  turn a value of one type into another to heal a mismatch. Powerful but easy to
  abuse, so they're best kept behind libraries — which is exactly where the
  tutorial meets them: pulled in *indirectly* through Chisel and ChiselTest,
  never declared in chapter code.
- **by-name parameters (`=> T`)** — a parameter whose type starts with `=>`
  receives an *unevaluated* expression (no explicit `() =>` needed) that is
  evaluated only when the body actually uses it — the trick libraries use to make
  a method look like a built-in control structure.

One idiom that *is* everywhere and is worth naming: **anonymous class
instantiation** — writing `new` before a trait/abstract-class name followed by a
`{ … }` body yields an instance of an *anonymous* class that implements it inline.
This is how nearly every IO is declared:

`ch01-introduction/src/main/scala/Hello.scala`

```scala
val io = IO(new Bundle {
  val led = Output(UInt(1.W))
})
```

---

## Where the chapters already explain Scala

Several chapter READMEs have inline **"Scala note"** callouts. This file
consolidates and extends them; consult the originals for the long-form version:

| Chapter README (line) | Topic |
|---|---|
| `ch02-basic-components/README.md` (~96) | Type inference |
| `ch02-basic-components/README.md` (~181) | Operator precedence |
| `ch05-combinational-building-blocks/README.md` (~121) | Scala `if` vs Chisel `when` |
| `ch06-sequential-building-blocks/README.md` (~259) | Type parameters & upper bounds `[T <: Counter]` |
| `ch06-sequential-building-blocks/README.md` (~287) | The `=>` arrow, lambdas, and `=>` vs `<-` vs `<:` |
| `ch10-hardware-generators/README.md` (~584–607) | Function literals, higher-order functions, `_` wildcard |

---

*Back to the [tutorial index](README.md).*
