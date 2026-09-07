# Scala Notes for Chisel

Chisel is not a new language — it is a **library written in Scala**. Every
Chisel description you write (`Module`, `IO`, `when`, `:=`, `RegNext`, …) is
just Scala code that, when it runs, *builds* a hardware graph. So to read the
tutorial fluently you need a working feel for a handful of Scala constructs.

**Start with [Chapter 1 §1.2, "A crash course in Scala"](ch01-introduction/README.md#12-a-crash-course-in-scala).**
That section is the runnable introduction to the language: values and variables,
type inference, literals, expressions, methods, classes and objects, operators
as method calls, `apply`, collections, loops, tuples, string interpolation,
packages — and the elaboration-vs-hardware line that everything else depends on.
It is where the basics are *taught*, with a program you can run
(`sbt "runMain ScalaIntro"`) and real output for every construct.

**This file picks up where that leaves off.** It is the reference for the Scala
that the *later* chapters add: traits and abstract classes, `case class` and
enumerations, type parameters and generics, function literals and the functional
collection operators, pattern matching and `Option`, elaboration-time contracts,
and the ScalaTest DSL. Every entry is deliberately about *Scala itself* — not the
Chisel API — and carries a real example copied from the tutorial. Read Chapter 1
§1.2 once up front, then come back here whenever a chapter uses syntax you don't
recognize.

> *Conventions:* file paths are relative to the tutorial root. `chNN` refers to
> the chapter folder (e.g. `ch06` = `ch06-sequential-building-blocks/`). Within
> each section, examples are ordered by **ascending tutorial chapter number**,
> so the first time you meet a construct is the first bullet. Code blocks are
> verbatim from the tutorial unless tagged `*illustrative*`. The explanations
> draw on *Programming in Scala* (5th ed., Odersky, Spoon, Venners & Sommers),
> the definitive language reference, adapted to the hardware setting.

**The one idea to internalize first** is taught in Chapter 1 and worth repeating:
most Scala code here runs **once, at *elaboration* time**, to construct hardware —
it is not itself hardware. A Scala `if`, `for`, `val`, or `map` is a *build-time*
instruction ("emit this wire, repeat this connection 32 times"). The hardware
equivalents are the Chisel constructs (`when`, `Mux`, `Vec`, `Reg`). Keeping this
distinction straight is the single biggest hurdle for newcomers — see
[ch01 §1.2.18](ch01-introduction/README.md#1218-scala-vs-chisel-the-elaboration-vs-hardware-line).

Where a chapter README already has a "Scala note" callout, this file points to
it rather than repeating it.

---

## Contents

**The basics live in Chapter 1** — [§1.2 A crash course in Scala](ch01-introduction/README.md#12-a-crash-course-in-scala):
[`val`/`var`](ch01-introduction/README.md#121-values-and-variables-val-vs-var) ·
[type inference](ch01-introduction/README.md#122-types-are-inferred-but-you-can-state-them) ·
[literals](ch01-introduction/README.md#123-literals) ·
[expressions](ch01-introduction/README.md#124-everything-is-an-expression) ·
[`def`](ch01-introduction/README.md#125-methods-def) ·
[named & default arguments](ch01-introduction/README.md#126-named-and-default-arguments) ·
[classes](ch01-introduction/README.md#127-classes-new-and-extends) ·
[`object`](ch01-introduction/README.md#128-singletons-object-and-the-program-entry-point) ·
[operators](ch01-introduction/README.md#129-operators-are-method-calls) ·
[`apply`](ch01-introduction/README.md#1210-apply-the-one-method-name-you-may-omit) ·
[`Seq`/`List`/`Array`](ch01-introduction/README.md#1211-collections-seq-list-array) ·
[ranges & `for`](ch01-introduction/README.md#1212-ranges-and-the-for-loop) ·
[`while`](ch01-introduction/README.md#1213-while) ·
[tuples](ch01-introduction/README.md#1214-tuples) ·
[string interpolation](ch01-introduction/README.md#1215-string-interpolation) ·
[`println`](ch01-introduction/README.md#1216-println-and-the-standard-library) ·
[packages & imports](ch01-introduction/README.md#1217-packages-and-imports) ·
[elaboration vs. hardware](ch01-introduction/README.md#1218-scala-vs-chisel-the-elaboration-vs-hardware-line)

**Everything beyond the basics is here:**

- [A. Structure & reuse: traits, abstract classes, encapsulation, namespaces](#a-structure--reuse-traits-abstract-classes-encapsulation-namespaces)
- [B. Data & enumeration types](#b-data--enumeration-types)
- [C. Types & generics](#c-types--generics)
- [D. Functions & functional programming](#d-functions--functional-programming)
- [E. Collections: the functional toolkit](#e-collections-the-functional-toolkit)
- [F. Pattern matching & `Option`](#f-pattern-matching--option)
- [G. Control flow beyond the basics](#g-control-flow-beyond-the-basics)
- [H. Elaboration-time contracts: `assert` & `require`](#h-elaboration-time-contracts-assert--require)
- [I. ScalaTest DSL (reads like English, is really Scala)](#i-scalatest-dsl-reads-like-english-is-really-scala)
- [J. What the tutorial does *not* use](#j-what-the-tutorial-does-not-use)
- [Where the chapters already explain Scala](#where-the-chapters-already-explain-scala)

---

## A. Structure & reuse: traits, abstract classes, encapsulation, namespaces

Chapter 1 introduced the two containers you meet on day one — the
[`class`](ch01-introduction/README.md#127-classes-new-and-extends) and the
[`object`](ch01-introduction/README.md#128-singletons-object-and-the-program-entry-point).
This section covers the rest of Scala's structuring vocabulary, which the
tutorial reaches for once designs grow past a single module.

### A.1 `trait` … mixed in with `with`


*(ch06)* — a trait is Scala's fundamental
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

### A.2 `abstract class` with constructor parameters


*(ch10)* — a class that has an
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

### A.3 `private` members


*(ch11)* — placing `private` in front of a field, method,
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

### A.4 `object` as a namespace / companion object


*(ch14)* — because an `object` is
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

**There is no top-level `def` in Scala 2.** Convenience is not the only reason a
free function ends up inside an `object` — it is the only place it may go. At the
top level of a file Scala 2 accepts a `class`, a `trait`, an `object`, or a
`package object`, and nothing else; a bare `def` (or `val`) there is a
compile-time error, not a style warning:

*illustrative — does not compile under Scala 2.13*
```scala
import chisel3._

def arbitrateSimp[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]) = { ... }
```
```
ArbiterTree.scala:26:3: expected class or object definition
  def arbitrateSimp[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]) = {
  ^
```

*(ch10)* — this is why the two 2:1 arbitration functions of §10.6.2, which are
deliberately kept *outside* the modules so that the arbiter class can take one as
a parameter, are wrapped in an `object`:

`ch10-hardware-generators/src/main/scala/ArbiterTree.scala`

```scala
object Arbitration {
  def arbitrateSimp[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = { ... }
  def arbitrateFair[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = { ... }
}

import Arbitration._
```

The wrapper can equally well be the **companion** of the class that uses the
functions (`object Arbiter` next to `class Arbiter`) — the choice is only about
naming, since either way `import <object>._` brings the names into bare scope.
Note also that the restriction applies to the *top level* only: a `def` **inside**
a class, object, or even another method is fine, which is why the book's version
of these arbiters — where each function is a method of its own Module subclass —
needs no wrapper at all.

Scala 3 lifted the restriction and does allow top-level `def`s and `val`s; this
tutorial pins Scala 2.13, so the `object` is required here.

---

---

## B. Data & enumeration types


Where [§A](#a-structure--reuse-traits-abstract-classes-encapsulation-namespaces) covers the
*containers* that organize a program, this section covers declarations whose job
is to model **values** — a bundle of typed fields, or a finite set of named
constants.

### B.1 Enumerations via a nested `object` (`ChiselEnum`)


*(ch08)* — an enumeration
is a type restricted to a **finite set of named values**. You first meet one in
the finite-state-machine chapter, where each FSM declares its states as a nested
`object State extends ChiselEnum` **inside** the Module. Two Scala ideas combine
here:

- *Nesting / scoping* — a declaration may be nested inside a class, object, or
  even a method, scoping it to its encloser. Putting `object State` inside the
  Module keeps the state names local to that module; they don't leak out.
- *`object` = singleton* — an enumeration is conceptually **one** type with
  **one** fixed set of constants, so it wants exactly one instance, which is
  precisely what an `object` is (no `new`, no duplicates). `import State._` then
  pulls `green`/`orange`/`red` into bare scope.

(`ChiselEnum` itself is a *Chisel library* trait, not a Scala keyword: it turns
the nested `object` into symbolic, binary-encoded hardware state constants — the
Chisel counterpart to Scala 2's own library `Enumeration`, covered at the end of
this section.)

`ch08-finite-state-machines/src/main/scala/SimpleFsm.scala`

```scala
object State extends ChiselEnum {   // declared *inside* the Module
  val green, orange, red = Value
}
import State._                      // then refer to `green`, `orange`, `red` bare
```

> **Why `object`, not `class`?** `class State extends ChiselEnum` *does* compile —
> but then you must instantiate it (`val s = new State; import s._`), and nothing
> stops you creating several independent instances whose values have formally
> different, path-dependent types (`s1.Type` ≠ `s2.Type`). An enumeration is a
> **single shared type** — inherently a singleton — so `object`, Scala's built-in
> singleton with no `new`, is the exact fit. It's the same "Scala classes have no
> statics; an `object` replaces them" point from
> [§A](#a-structure--reuse-traits-abstract-classes-encapsulation-namespaces): the state
> constants are the shared, static-like values that belong on one singleton, and
> being a singleton is what lets `import State._` bring them into scope bare.

### B.2 `case class`


*(ch10)* — a lightweight, immutable data holder. The `case`
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

### B.3 Scala's `Enumeration` + `type` alias


*(ch15)* — Scala's *standard library*
offers its own enumeration, distinct from Chisel's `ChiselEnum` above: you extend
the library class `Enumeration` (again as an `object`, for the same singleton
reason) and assign `Value`. Each value carries an ordinal `.id` starting at 0 in
declaration order, which you can use as an `Int`. A `type` member, declared with
the `type` keyword, names a type: a concrete one (`type InstrType = Value`) is an
alias that clarifies a verbose type, while an abstract one is left for subclasses
to define. The RISC-V chapter uses this for instruction-format tags used purely
at generation time.

`ch15-a-risc-v-pipeline/src/main/scala/wildcat/defines.scala`

```scala
object InstrType extends Enumeration {
  type InstrType = Value
  val R, I, S, SBT, U, UJ = Value
}
```

---

---

## C. Types & generics


### C.1 Type parameters `[T]` with an upper bound `[T <: X]`


*(ch06)* — a *generic* (type parameter) is a placeholder for a **type**, written
in square brackets `[ ]` just as value parameters are written in parentheses
`( )` — and any value arguments then follow in parentheses, because the type
argument is part of the type while the value isn't. It is **filled in per call**,
and the compiler *infers* it from the arguments, so you never write it out:
calling `testFn(new WhenCounter(4), …)` makes `T = WhenCounter`, and passing a
`MuxCounter` makes `T = MuxCounter`.

`<:` is an **upper bound** — "is a subtype of". `[T <: Counter]` reads *"`T` may
be any type, as long as it extends `Counter`."* So `WhenCounter`, `MuxCounter`,
`DownCounter`, `FunctionCounter`, and `NerdCounter` (which all `extends Counter`)
are valid choices for `T`, while something unrelated like `String` is rejected at
**compile time**. The bound is also exactly what lets the body call `T`'s
members: because `T` is guaranteed to be a `Counter`, the compiler knows
`c.io.tick` / `c.io.cnt` / `c.clock` exist — without the bound `T` could be
anything and `c.io` wouldn't compile (requiring `[T <: Ordered[T]]`, say, would
instead let you compare elements). One method definition, type-checked once,
drives all five counter variants — this is the mechanism behind *type-generic*
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

### C.2 Wildcard type argument `[_ <: Data]`


*(ch11)* — when you don't care *which*
subtype fills a type parameter, `_` is an anonymous placeholder: `Fifo[_ <: Data]`
means "a `Fifo` of *some* `Data` subtype." Handy for a test that accepts any
FIFO regardless of payload.

`ch11-example-designs/src/test/scala/fifo/FifoTest.scala`

```scala
def testFn[T <: Fifo[_ <: Data]](dut: T) = { ... }
```

### C.3 `type` alias


*(ch15)* — `type Name = Existing` introduces a synonym for an
existing type, to abbreviate a verbose type or clarify intent. (See the
`type InstrType = Value` example in [§B.3](#b3-scalas-enumeration--type-alias).)

---

---

## D. Functions & functional programming


### D.1 Function literals (lambdas) and the `=>` arrow


*(ch06)* — Scala has *first-class functions*: you write a function as an unnamed
literal (`(x: Int) => x + 1`) and pass it around as a value. The **`=>`** is the
anonymous-function arrow — it separates the parameter(s) on the left from the
body on the right (`param => body`, read *"given `param`, do `body`"*). Worth
distinguishing: the literal is source-code *text*, while the function *value* it
produces at run time is an object you can store and invoke — the same
class-vs-object distinction, one level up.

This is *the* most common Scala shape in the tutorial, because every ChiselTest
bench is `test(new Dut) { dut => ... }`: `test` elaborates the module into a
simulator, then calls *your* function, **loaning** you the ready-to-poke running
instance as `dut`; when the block returns, `test` tears the simulation down.
(This is why you can't just write `testFn(new WhenCounter(4), 4)` directly — a
raw `new` isn't a live simulation until `test` wraps it.) Short forms let you
drop inferable parameter types and the parentheses around a single parameter (the
expected type "targets" the inference).

Don't confuse `=>` with the neighbouring arrows: **`<-`** in
`for (_ <- 0 until n)` is the for-comprehension *generator* ("drawn from"), and
**`<:`** is the [subtype bound](#c1-type-parameters-t-with-an-upper-bound-t--x)
from the entry above. The `=>` arrow also shows up in `match` cases
(`case 0 => ...`) — same idea: left of the arrow is the input, right of it is the
result.

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`

```scala
test(new WhenCounter(4)) { c => testFn(c, 4) }
```

Two-parameter literal (used to fold a Chisel `Vec`):

`ch10-hardware-generators/src/main/scala/functional.scala`

```scala
vec.reduceTree((x, y) => Mux(x < y, x, y))
```

### D.2 Nested (local) functions & closures

*(ch06 / ch12)* — a `def` defined inside another
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

**Closures over test state.** The function you hand to `test` closes
over `val`s and `var`s declared just outside it, so the body can read the DUT and
update expected-value bookkeeping (the `var count` from [§1.2.1](ch01-introduction/README.md#121-values-and-variables-val-vs-var))
across cycles. Because closures capture the variable, not a copy, each iteration
sees the updated count.

### D.3 The `_` placeholder (point-free style)


*(ch10)* — inside a function literal,
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

### D.4 Higher-order functions


*(ch10)* — a function that takes (or returns) another
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

---

## E. Collections: the functional toolkit

Chapter 1 covered the collection *types* themselves —
[`Seq` / `List` / `Array`](ch01-introduction/README.md#1211-collections-seq-list-array)
and [ranges](ch01-introduction/README.md#1212-ranges-and-the-for-loop). This
section is about the operations you apply to them. Scala collections drive
*generation*: you build a Scala `Seq`/`List` at elaboration time and turn it into
hardware with `VecInit`, `for`, `map`, etc.

### E.1 `map` / `foreach` / `reduce` / `zip` / `zipWithIndex`


*(ch10)* — the
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

### E.2 String as a `Seq[Char]`


*(ch10)* — a `String` isn't literally a sequence, but
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

### E.3 Conversions (`.toList`, `.toIndexedSeq`, `.toInt`, `.toLong`)


*(ch14)* —
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

---

## F. Pattern matching & `Option`

[Tuples](ch01-introduction/README.md#1214-tuples) — the third member of this
family — are in Chapter 1, because a generator that returns two signals needs
them immediately.

### F.1 `Option` / `Some` / `None` / `.get`


*(ch02)* — Scala's null-free "maybe a
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

### F.2 Pattern-matching in a lambda


*(ch10)* — patterns are allowed well beyond a
standalone `match`. A sequence of `case` clauses in braces can be used anywhere a
function literal is expected — it's essentially a literal with multiple entry
points (formally a *partial function*, which throws if applied to an unmatched
value). So `{ case (v, i) => … }` destructures each element as it arrives,
binding the tuple's parts to names. Common right after `zipWithIndex`.

`ch10-hardware-generators/src/test/scala/FunctionalTest.scala`

```scala
Seq(3, 2, 0, 9, 1).zipWithIndex.foreach { case (v, i) => dut.io.in(i).poke(v.U) }
```

### F.3 `match` / `case` / wildcard `case _`


*(ch14)* — a `match` selects among
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

---

## G. Control flow beyond the basics

The [`for` loop](ch01-introduction/README.md#1212-ranges-and-the-for-loop) and
the [`while` loop](ch01-introduction/README.md#1213-while) are in Chapter 1. One
variation shows up only in the later chapters.

### G.1 Multi-generator `for`


*(ch14)* — several `<-` generators in one `for` header,
separated by `;`, iterate as nested loops — here sweeping every ALU operation ×
every operand pair to exhaustively test the ALU.

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`

```scala
for (fun <- 0 to 7; a <- values; b <- values) testOne(a, b, fun)
```

---

---

## H. Elaboration-time contracts: `assert` & `require`

Both of these run on the JVM while your generator is building the circuit, not on
the chip afterwards — they check *your parameters and your Scala model*, and they
are distinct from Chisel's hardware `assert`, which checks during simulation.

### H.1 `assert` (Scala)


*(ch10)* — the predefined `assert` method (from `Predef`)
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

### H.2 `require`


*(ch11)* — a *precondition* is a constraint on the values passed into
a method or constructor: something the caller must satisfy. You enforce one with
the `require` method (from `Predef`), which throws an `IllegalArgumentException`
if its condition is false, **preventing construction** with invalid data. Placing
`require(depth > 0, …)` at the top of a module rejects bad parameters *before* any
hardware is built, so the object is valid from the moment it exists.

`ch11-example-designs/src/main/scala/fifo/fifo.scala`

```scala
require(depth > 0, "Number of buffer elements needs to be larger than 0")
```

---

## I. ScalaTest DSL (reads like English, is really Scala)


The test files read like sentences, but every word is an ordinary Scala method
call in infix position (see [§H](#h-elaboration-time-contracts-assert--require)). ScalaTest
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

---

## J. What the tutorial does *not* use


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
  [§H](#h-elaboration-time-contracts-assert--require)); the tutorial never *defines* one.
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

---

## Where the chapters already explain Scala

Several chapter READMEs have inline **"Scala note"** callouts. This file
consolidates and extends them; consult the originals for the long-form version:

| Chapter README (line) | Topic |
|---|---|
| `ch01-introduction/README.md` §1.2 | **The whole Scala crash course** — the basics this file builds on |
| `ch02-basic-components/README.md` (~96) | Type inference |
| `ch02-basic-components/README.md` (~181) | Operator precedence |
| `ch05-combinational-building-blocks/README.md` (~121) | Scala `if` vs Chisel `when` |
| `ch06-sequential-building-blocks/README.md` (~276) | Type parameters & upper bounds `[T <: Counter]` |
| `ch06-sequential-building-blocks/README.md` (~281) | The `=>` arrow, lambdas, and `=>` vs `<-` vs `<:` |
| `ch10-hardware-generators/README.md` (~584–607) | Function literals, higher-order functions, `_` wildcard |

---

*Back to the [tutorial index](README.md).*
