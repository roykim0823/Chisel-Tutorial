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
> that grows on you." You do not need to learn Scala first — but a little goes a
> long way, so [§1.2](#12-a-crash-course-in-scala) below is a runnable tour of
> exactly the Scala this tutorial uses, and Chapter 10 revisits it from the
> generator angle. This tutorial is neither a Scala textbook nor a Chisel
> language reference; [`SCALA-NOTES.md`](../SCALA-NOTES.md) is the reference for
> the Scala that later chapters add.

Every code example in this tutorial (as in the book) is compiled and tested, so
snippets should be free of syntax errors, and the examples aim to show not just
working Chisel but **good hardware-description style**.

> **Toolchain setup** (Java JDK 8–21, `sbt`, an optional IDE, and the `$`-prompt
> convention used in the command blocks) is a one-time step covered in the
> [tutorial index / README](../README.md#prerequisites). For building on a real
> FPGA you also need a vendor synthesis tool — see the exercise in §1.6.

By Scala/sbt convention, application source lives under `src/main/scala/`.
sbt finds and compiles everything there automatically — you never list files
manually, so every file path in this chapter starts from there.

---

## 1.1 Hello World (this is Scala, *not* hardware)

Every language book starts with "Hello World". Here is the first attempt.

`src/main/scala/HelloScala.scala`
```scala
object HelloScala extends App {
  println("Hello Chisel World!")
}
```

*Scala note — `object … extends App` → [§1.2.8](#128-singletons-object-and-the-program-entry-point); `println` & the standard library → [§1.2.16](#1216-println-and-the-standard-library).*

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
[info] compiling 3 Scala sources to .../target/scala-2.13/classes ...
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
"Hello World"? A blinking LED — but before we build one, the next section takes
twenty minutes to make sure the Scala underneath it reads as ordinary code
rather than as magic.

---

## 1.2 A crash course in Scala

`HelloScala` was three lines long and every one of them was Scala. Before the
next section asks you to read a hardware module, it is worth spending twenty
minutes on the language itself — because **Chisel is not a new language.** It is
a *library written in Scala*. Every Chisel description you write (`Module`,
`IO`, `when`, `:=`, `RegInit`, …) is ordinary Scala code that, when it runs,
*builds* a hardware graph. Anything legal in Scala is legal in a Chisel design,
and that is exactly where Chisel's power comes from.

Scala itself is a statically typed language on the JVM that blends
object-oriented and functional programming. You do not need to master it — the
subset below is genuinely all you need to read every chapter of this tutorial,
and each item reappears in context when a chapter first uses it.

Everything in this section is **plain Scala with no hardware in it**, and all of
it is in one runnable file. Run it now and keep the output beside you:

```
$ sbt "runMain ScalaIntro"
```

`src/main/scala/ScalaIntro.scala` opens with a small helper that prints the
banner separating each block below, and the file ends with a namespace object
holding a constant:

`src/main/scala/ScalaIntro.scala`
```scala
object ScalaIntro extends App {

  def section(n: String, title: String): Unit =
    println(s"\n--- $n $title " + "-" * (46 - title.length))

  // ... the numbered blocks below ...
}

object Constants {
  val NOP = 0x00
}
```

The captured output of the whole file is at the end of this section, in
[§1.2.19](#1219-the-full-run).

> *Conventions:* the explanations draw on *Programming in Scala* (5th ed.,
> Odersky, Spoon, Venners & Sommers), the definitive language reference, adapted
> to the hardware setting. Advanced Scala that only shows up in later chapters —
> traits, generics, `case class`, pattern matching, the functional collection
> operators — lives in [`SCALA-NOTES.md`](../SCALA-NOTES.md), the reference
> companion to this section. Come back here for the basics; go there for the
> rest.

---

### 1.2.1 Values and variables: `val` vs `var`

Scala has two kinds of variable. A `val`, once initialized, can **never** be
reassigned (like a Java `final`); a `var` can be reassigned throughout its life
and supports `+=` / `-=`. Scala style prefers `val`.

`src/main/scala/ScalaIntro.scala`
```scala
val fixed = 42
var running = 0
running += 1
running += 1
```
```
fixed = 42 (can never be reassigned)
running = 2 (reassigned twice)
```

(Reassignment is really a get/set pair: every non-private `var` member of an
object implicitly gets a matching getter and setter.)

**This distinction matters more in hardware than in software.** In
*hardware-description* code everything is a `val` — a `val` *names a piece of
the circuit*, it doesn't "vary." You will see `val cntReg = RegInit(0.U(32.W))`
in the next section: `cntReg` is the *name of a register*, and the register's
contents changing every clock cycle has nothing to do with Scala reassignment.
Mutable `var` shows up in this tutorial **only in test benches**, where it
accumulates an expected value across simulated clock cycles — software
bookkeeping, not hardware:

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`
```scala
var count = -1
// ...later, inside the cycle loop:
count -= 1
```

---

### 1.2.2 Types are inferred (but you can state them)

Scala is statically typed, yet you rarely write types down: the compiler
*infers* the type of most `val`s from the right-hand side. Inference is
**flow-based** — for a call `m(args)`, if the method `m`'s type is already
known, that expected type flows *into* inferring the arguments (for instance,
the parameter types of a function literal you pass in). So a very terse literal
can be inferred where the surrounding type is known, but not when the type would
have to flow the other way.

`src/main/scala/ScalaIntro.scala`
```scala
val inferred = 42          // Int, inferred
val stated: Int = 42       // the same thing, spelled out
val text = "forty-two"     // String, inferred
```
```
inferred=42 stated=42 text=forty-two
```

State the type when it aids the reader, or when inference needs the help.
Chapter 2 calls type inference out again where it first matters for hardware
types, and Chapter 10 shows the explicit form `val number: Int = 42`.

---

### 1.2.3 Literals

A literal writes a constant value directly in code; all of Scala's basic types
have literal forms. A character literal is a Unicode character in single quotes
(`'A'`), or `\u` followed by four hex digits, or an escape sequence.

**One gotcha: Scala has no octal literals.** A C or Java programmer writes
`0755` meaning 493; Scala 2.13 has no octal notation at all, so the leading zero
is simply ignored and you silently get **decimal 755**. (Verified on this
project's pinned Scala 2.13.14: `val a = 0755` compiles and prints `755`.) There
is no error and no warning, so this is a bug that hides — never lead an integer
literal with `0`. Hexadecimal `0x00`, a `Long` via the `L` suffix
(`0x00ffffffffL`), `Double` like `100.0`, and underscores as digit separators
are all fine.

`src/main/scala/ScalaIntro.scala`
```scala
val hex = 0x2a
val big = 0x00ffffffffL
val ch  = 'A'
val sep = 1_000_000
val dbl = 100.0
```
```
hex=42 big=4294967295 char=A separated=1000000 double=100.0
```

In Chisel the suffixes `.U` / `.S` / `.B` / `.W` then lift a Scala literal into a
Chisel value or width — which is what `50000000 / 2 - 1` followed by `.U` does
in the next section, and what turns a character into a hardware constant here:

`ch02-basic-components/src/main/scala/Logic.scala`
```scala
val aChar = 'A'.U    // char literal, then made a Chisel UInt
```

---

### 1.2.4 Everything is an expression

In Scala `if`/`else` *is an expression*: it tests a condition and evaluates to
the value of whichever branch runs, so it can sit on the right-hand side of `=`.
(There is no ternary `?:` because `if` already does that job.) Initializing a
`val` directly from an `if` — rather than declaring a `var` and mutating it — is
the functional idiom and signals to readers that the value never changes.

A `{ … }` block is an expression too: it evaluates to its **last expression**.
Idiomatic Scala has no `return` keyword — a method's result is simply the value
its body ends with.

`src/main/scala/ScalaIntro.scala`
```scala
val parity = if (fixed % 2 == 0) "even" else "odd"
val blockValue = {
  val a = 3
  val b = 4
  a * a + b * b        // the block's value is its last expression
}
```
```
parity=even blockValue=25
```

Chapter 2 uses exactly this shape to decide, **at elaboration time**, whether an
optional debug port exists at all:

`ch02-basic-components/src/main/scala/RegisterFile.scala`
```scala
val dbgPort = if (debug) Some(Output(Vec(32, UInt(32.W)))) else None
```

Contrast Chisel's `when`, which builds a runtime multiplexer rather than
choosing at build time — that is [§1.2.18](#1218-scala-vs-chisel-the-elaboration-vs-hardware-line)
below, and the single most important idea in this chapter.

---

### 1.2.5 Methods: `def`

A `def` starts with the name, a parenthesized parameter list where **every
parameter must carry an explicit type** (the compiler does *not* infer parameter
types), an optional result type after a colon, then `=` and the body. The `=`
reflects the functional view that a method defines an expression yielding a
value; `Unit` is the "nothing useful" result type (like `void`). A method is
simply a function defined as a member of some object.

`src/main/scala/ScalaIntro.scala`
```scala
def square(x: Int): Int = x * x
def shout(msg: String): Unit = println(msg.toUpperCase)
```
```
square(7) = 49
METHODS END WITH THE VALUE OF THEIR BODY
```

In Chisel a `def` that returns hardware (a `UInt`, a `Bundle`, a tuple of
signals) is a **hardware generator** — calling it stamps out that sub-circuit.
That is the whole subject of Chapter 10, and it looks like this:

`ch11-example-designs/src/main/scala/fifo/fifo.scala`
```scala
def counter(depth: Int, incr: Bool): (UInt, UInt) = { ... }
```

`ch14-design-of-a-processor/src/test/scala/leros/AluAccuTest.scala`
```scala
def testOne(a: Int, b: Int, fun: Int): Unit = { ... }
```

Because a block is an expression, a generator ends by simply *naming* the
hardware it wants to hand back:

`ch10-hardware-generators/src/main/scala/ParamFunc.scala`
```scala
def myMux[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
  val ret = WireDefault(fPath)
  when(sel) { ret := tPath }
  ret                        // <- this value is the method's result
}
```

---

### 1.2.6 Named and default arguments

A definition can give a parameter a fallback value, so callers who omit that
argument get the default. At a call site you may also pass arguments by writing
each parameter's name and `=` before its value, which lets you supply them in a
different order than declared (any positional arguments must come first).

Together they make calls with several same-typed parameters self-documenting —
`frequency = 1000, baudRate = 10` cannot be swapped by mistake.

`src/main/scala/ScalaIntro.scala`
```scala
def uart(frequency: Int, baudRate: Int = 115200): String =
  s"frequency=$frequency baudRate=$baudRate"

uart(50000000)                          // default baud rate
uart(baudRate = 10, frequency = 1000)   // named, reordered
```
```
frequency=50000000 baudRate=115200
frequency=1000 baudRate=10
```

Later chapters lean on both — defaults for "usually 1" step counts and "off by
default" feature flags, names for readable test setup:

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`
```scala
def step(n: Int = 1) = dut.clock.step(n)   // step() means step(1)
```

`ch12-interconnect/src/main/scala/interconnect.scala`
```scala
class MemMappedRV[T <: Data](gen: T, block: Boolean = false) extends Module
```

`ch11-example-designs/src/test/scala/uart/UartTest.scala`
```scala
test(new UartLoopback(frequency = 1000, baudRate = 10))
```

---

### 1.2.7 Classes, `new`, and `extends`

A class is a blueprint for objects; you instantiate it with `new`, and inside it
you place *members*: fields (declared with `val`/`var`) that hold each
instance's state, and methods (declared with `def`) that operate on that state.
Every instance gets its own copy of the fields. Constructor parameters go in
parentheses after the class name.

An `extends` clause makes one class a subclass of another: it inherits all the
superclass's non-private members **and** becomes a subtype of it (omitting
`extends` implicitly extends `AnyRef`).

`src/main/scala/ScalaIntro.scala`
```scala
class Greeter(name: String) {
  def greet(): String = s"Hello, $name!"
}
class LoudGreeter(name: String) extends Greeter(name) {
  override def greet(): String = super.greet().toUpperCase
}
```
```
Hello, Scala!
HELLO, CHISEL!
```

**This is the single most important shape in the tutorial**, because *a Chisel
component is just a class that `extends Module`*. Its constructor body runs at
elaboration to build the circuit — which is why the next section's design is
written as a class and nothing else:

`src/main/scala/Hello.scala`
```scala
class Hello extends Module {
  val io = IO(new Bundle { ... })
}
```

`case class`es and factory objects let you skip the `new`; where an explicit
`new` is needed, the type is still inferred:

`ch03-build-and-testing/src/main/scala/usepack.scala`
```scala
val x = new mypack.Abc()   // type Abc inferred; no `: Abc` needed
```

One related idiom is worth naming now because it is *everywhere*: **anonymous
class instantiation** — writing `new` before a trait or abstract-class name
followed by a `{ … }` body yields an instance of an *anonymous* class that
implements it inline. This is how nearly every Chisel IO is declared:

`src/main/scala/Hello.scala`
```scala
val io = IO(new Bundle {
  val led = Output(UInt(1.W))
})
```

---

### 1.2.8 Singletons: `object`, and the program entry point

Scala classes have **no** static members. An `object` is Scala's replacement for
Java-style statics: a lazily-created singleton — exactly one instance, no `new`.
Because it is a singleton with no statics elsewhere in the language, an `object`
is the natural home for constants and stateless helper `def`s. When an `object`
shares its name with a class in the same file it is that class's *companion*,
and the two may freely access each other's private members.

**There is no top-level `def` or `val` in Scala 2.** At the top level of a file
Scala 2 accepts a `class`, a `trait`, an `object`, or a `package object`, and
nothing else — so a helper function has nowhere else to live. (Scala 3 lifted
this; the tutorial pins Scala 2.13, so the wrapper object is required.)

`src/main/scala/ScalaIntro.scala`
```scala
object Constants {
  val NOP = 0x00
}
```
```
Constants.NOP = 0x00 (one shared instance, no `new`)
and ScalaIntro itself is an object too - that is why it can be run
```

An application's entry point is a standalone object with a
`main(args: Array[String]): Unit` method; mixing in the library trait `App`
generates that `main` for you, so the object body simply *is* the program. That
is what `HelloScala` in [§1.1](#11-hello-world-this-is-scala-not-hardware) was:

`src/main/scala/HelloScala.scala`
```scala
object HelloScala extends App {
  println("Hello Chisel World!")
}
```

In this tutorial that same pattern names the generators (`Generate`, `Hello`,
`ScalaIntro`) that elaborate a module and emit its Verilog — the software
wrapper *around* your hardware, not hardware itself. Chapter 14 uses a namespace
object for processor constants, and importing its members lets you write `NOP`
instead of `Constants.NOP`:

`ch14-design-of-a-processor/src/main/scala/leros/shared/shared.scala`
```scala
object Constants {
  val NOP = 0x00
  // ...
}
```

---

### 1.2.9 Operators are method calls

Operators aren't a special language feature in Scala; they're ordinary **method
calls** in nicer syntax. `1 + 2` literally means `1.+(2)`, and *any* method
taking a single argument can be written infix without a dot.

`src/main/scala/ScalaIntro.scala`
```scala
1 + 2
1.+(2)
6 & 3
"ab".concat("cd")   //  ==  "ab" concat "cd"
```
```
1 + 2      = 3
1.+(2)     = 3
6 & 3      = 2   (6.&(3))
'x' concat = abcd == abcd
```

Since there are no built-in operators, precedence is decided by the operator's
**first character** — one starting with `*` binds tighter than one starting with
`+` — and most are left-associative. This is similar to but *not identical to*
Java/C, so parenthesize when unsure.

You can even define your own operators simply by naming methods with operator
characters, which is how a Chisel type gets `+`, `&`, `##`, `===`, … So `a & b`
is `a.&(b)` on a Chisel `UInt` — a method that *builds an AND gate* — and `##`
(bit concatenation) is just another method used infix:

`ch02-basic-components/src/main/scala/Logic.scala`
```scala
val logic = (a & b) | c
val word  = highByte ## lowByte
```

Chapter 2 revisits precedence where it can actually bite you.

---

### 1.2.10 `apply`: the one method name you may omit

`apply` is a piece of syntactic sugar that lets an object be *called like a
function*. When you follow a *value* (as opposed to a method name) with
parentheses, `obj(args)`, the compiler translates it into `obj.apply(args)`
behind the scenes: the parentheses are not built-in call syntax, they are a
silent method invocation. `apply` is the single method name Scala lets you drop
this way — every other method must still be named in full (`obj.foo(args)`).

`src/main/scala/ScalaIntro.scala`
```scala
val squares = Seq(1, 4, 9, 16)
squares(2)          // == squares.apply(2)
val double = (x: Int) => x * 2
double(21)          // == double.apply(21)
```
```
squares(2)        = 9
squares.apply(2)  = 9   (identical)
double(21)        = 42 == double.apply(21) = 42
```

**The reason the language has this rule is unification.** In Scala a function is
itself an object: a value of type `A => B` is really an instance of the trait
`Function1[A, B]`, whose one member is a method called `apply`. Without the
sugar, calling a function would mean writing `f.apply(x)`. By rewriting `f(x)`
into `f.apply(x)`, Scala lets a plain function value be invoked with ordinary
call syntax — and because the same rewrite applies to *any* value, not just
functions, an arbitrary object can opt into that syntax simply by defining an
`apply` method. That is how libraries make their own constructs read like
built-in language features. C++ programmers will recognize both the mechanism
and the motive: this is exactly `operator()`, and an object that defines `apply`
is a callable "functor".

So this is the lesson of [§1.2.9](#129-operators-are-method-calls) from the other
direction: there, operators turned out to be ordinary method calls; here,
**function-call syntax is one too**. That single rewrite is the language's whole
contribution — the method itself is ordinary code someone wrote.

Two consequences follow, and together they explain a lot of Chisel that
otherwise looks like built-in syntax:

- **Dropping the *name* is not the same as dropping the *dot*.** The infix rule
  of §1.2.9 lets any single-argument method lose its dot (`a add b`); the
  `apply` rule lets one particular method lose its name. The two are
  independent: `apply` loses its name, an infix operator loses its dot.
- **`apply` is not a keyword and implements no interface.** Any class or object
  may define one, with any signature, overloaded or curried; the compiler simply
  looks the name up. So `Mux.apply` and a `UInt`'s `apply` are unrelated methods
  that happen to share a name — which is why the same syntax means "build a
  multiplexer" in one place and "extract a bit" in another.

| Written | What it really is | Where `apply` is defined |
|---|---|---|
| `IO(new Bundle { … })` | `IO.apply(…)` | Chisel's `object IO` |
| `Mux(cond, a, b)` / `RegInit(0.U)` | `Mux.apply(…)` / `RegInit.apply(…)` | Chisel companion objects |
| `cntReg(7)` | `cntReg.apply(7)` | `Bits` — **extracts bit 7**, not an array index |
| `Seq(1, 2, 3)` / `Config(4, 2, 16)` | companion `apply` | stdlib / `case class` |
| `test(new Dut) { c => … }` | `.apply(lambda)` on the object `test` returned | chiseltest's `TestBuilder` |

The bit-extraction case is the one most often misread, because it looks exactly
like indexing a collection:

`ch06-sequential-building-blocks/src/main/scala/Counter.scala`
```scala
  when(cntReg(7)) {   // sign bit set => reached -1
```

And the ChiselTest bench is the case where the omission makes a library call
look like syntax — the braces are an argument to a second, separate call:

`ch06-sequential-building-blocks/src/test/scala/CounterTest.scala`
```scala
test(new WhenCounter(4)) { c => testFn(c, 4) }
```

> The ch06 review page [*Anatomy of `CounterTest.scala`*](../ch06-sequential-building-blocks/reviews/CounterTest.md)
> walks that last line through both calls in detail.

---

### 1.2.11 Collections: `Seq`, `List`, `Array`

*Sequence* types hold data lined up in order, so you can ask for elements by
position — with `()`, zero-based, which by [§1.2.10](#1210-apply-the-one-method-name-you-may-omit)
is really a call to `apply`. The default `List` is an immutable linked list:
fast to add or remove at the front and great for pattern matching, but *not*
fast for arbitrary-index access.

You most often create one by passing the initial elements to the companion's
`apply` factory (`List(...)`, `Seq(...)`, `Array(...)`). `Seq.fill(n)(x)` builds
`n` copies of `x`, while `Array.fill(n){ block }` *runs the block* `n` times —
which is how you instantiate `n` fresh sub-modules. `Array` is special: it maps
one-to-one onto a Java array yet is still generic and `Seq`-compatible.

`src/main/scala/ScalaIntro.scala`
```scala
val numbers = Seq(1, 15, -2, 0)
val zeros   = Seq.fill(4)(0)
val boxes   = Array.fill(3) { new Greeter("box") }
```
```
numbers = List(1, 15, -2, 0), numbers(1) = 15
Seq.fill(4)(0) = List(0, 0, 0, 0)
Array.fill(3){...} made 3 distinct objects
```

Scala collections drive *generation*: you build a `Seq` at elaboration time and
turn it into hardware. `Seq` is the preferred general-purpose collection for
Chisel hardware generators — you use these to describe *how many* of something
to generate:

`ch02-basic-components/src/main/scala/RegisterFile.scala`
```scala
val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
```

`ch11-example-designs/src/main/scala/BubbleFifo.scala`
```scala
val buffers = Array.fill(depth) { Module(new FifoRegister(size)) }
```

The functional operators over these collections (`map`, `reduce`, `zip`,
`zipWithIndex`) are what Chapter 10 builds generators out of; they are covered in
[`SCALA-NOTES.md` §E](../SCALA-NOTES.md#e-collections-the-functional-toolkit).

---

### 1.2.12 Ranges and the `for` loop

A `Range` is a collection of evenly-spaced integers, written `1 to 5`
(inclusive) or `1 until 5` (excluding the upper bound). It is what almost every
`for` header iterates over. **The exclusive/inclusive choice is a frequent
off-by-one source** — and, where you can, iterating a collection directly beats
indexing through a range, because it's shorter and sidesteps the off-by-one
entirely.

`for` is the workhorse loop. A generator `x <- coll` binds a fresh `val` to each
element of *any* collection in turn and runs the body.

`src/main/scala/ScalaIntro.scala`
```scala
(0 until 4)      // exclusive
(0 to 4)         // inclusive
for (i <- 1 until 4) println(s"  iteration i = $i")
```
```
0 until 4 = List(0, 1, 2, 3)   (exclusive)
0 to 4    = List(0, 1, 2, 3, 4)   (inclusive)
  iteration i = 1
  iteration i = 2
  iteration i = 3
```

In a Chisel design this loop does not run on the chip. **At elaboration it
*unrolls*, emitting the body's hardware once per iteration** (or running the
test action once per iteration in a test bench). Nested loops can be written
with two generators in one header separated by `;`, which reads as an outer loop
over an inner loop.

`ch05-combinational-building-blocks/src/main/scala/arbiter.scala`
```scala
for (i <- 1 until n) { ... }
```

`ch03-build-and-testing/src/test/scala/testing.scala` (nested form)
```scala
for (a <- 0 until 4) {
  for (b <- 0 until 4) { ... }
}
```

A `for` loop is the classic way to drive a circuit generator — Chapter 10 uses
one to wire the stages of a shift register together, connecting each register to
the previous one.

---

### 1.2.13 `while`

The classic pre-test loop: it re-runs its body as long as the condition holds.
It is called a "loop" rather than an expression because it yields no useful
value (its result type is `Unit`), and it is really only needed where mutable
state (`var`s) is involved — look for a way to avoid it when there is no strong
reason.

`src/main/scala/ScalaIntro.scala`
```scala
var countdown = 3
while (countdown > 0) {
  println(s"  countdown = $countdown")
  countdown -= 1
}
```
```
  countdown = 3
  countdown = 2
  countdown = 1
```

In test benches it runs the simulation until a hardware condition holds — e.g.
step the clock until an `ack` appears (with a guard counter to avoid hanging).
`.peekBoolean()` reads the DUT's current simulated value:

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`
```scala
while (!dut.io.ack.peekBoolean()) step()
```

---

### 1.2.14 Tuples

A tuple combines a **fixed number** of items so you can pass them around as a
whole; unlike a list its elements may have **different types**, which makes it
ideal for returning several values from a method without declaring a class.
Build one with comma-separated values in parentheses, read parts with `._1` /
`._2` (starting at `1`), or destructure with a pattern (`val (x, y) = …`). Its
type records both the count and the element types — `(99, "x")` is a
`Tuple2[Int, String]`.

`src/main/scala/ScalaIntro.scala`
```scala
val city = (2000, "Frederiksberg")
city._1                             // 2000
city._2                             // "Frederiksberg"
val (zipCode, name) = city          // destructuring
```
```
city._1 = 2000, city._2 = Frederiksberg
destructured: zipCode=2000 name=Frederiksberg
```

Perfect for a generator `def` that returns, say, a counter value *and* its wrap
flag:

`ch10-hardware-generators/src/main/scala/functional.scala`
```scala
def compare(a: UInt, b: UInt) = { ...; (equ, gt) }   // returns a 2-tuple
val (equ, gt) = compare(io.a, io.b)                  // destructure it
```

---

### 1.2.15 String interpolation

When an identifier sits immediately before a string literal's opening quote,
Scala applies that *interpolator*. The `s` interpolator evaluates each
`$`-prefixed expression (use braces `${…}` for anything beyond a bare
identifier), calls `toString`, and splices in the result; `f` allows
`printf`-style format specifiers, and `raw` skips escape processing. It's a
concise, readable alternative to concatenation, implemented by a compile-time
rewrite — and you can define your own interpolators.

`src/main/scala/ScalaIntro.scala`
```scala
val n = 7
println(s"s: $n squared is ${square(n)}")
println(f"f: pi is about ${math.Pi}%.3f")
println(raw"raw: a backslash-n stays literal: \n")
```
```
s: 7 squared is 49
f: pi is about 3.142
raw: a backslash-n stays literal: \n
```

Used in later chapters to build descriptive assertion messages:

`ch12-interconnect/src/test/scala/CounterDeviceTest.scala`
```scala
assert(read(i * 4) < 10, s"counter $i just started")
```

---

### 1.2.16 `println` and the standard library

Plain Scala output (`println`, as in `HelloScala`) and ordinary standard-library
utilities are all available inside generators and test benches — they run on the
JVM at elaboration or test time, so they cost nothing in hardware. A common one
is `scala.util.Random` for randomized test vectors (Chapter 14 uses it):

`src/main/scala/ScalaIntro.scala`
```scala
val rnd = new scala.util.Random(1)   // fixed seed: reproducible output
rnd.nextInt(100)
```
```
three random ints: 85, 88, 47
```

Seeding the generator with a constant keeps a failing test reproducible.

---

### 1.2.17 Packages and imports

A `package` clause at the top of a file places its code under a namespace
(mirroring the folder tree) and signals to the compiler that code in the same
package is related, so names don't collide across a large design. `import` then
lets you refer to package or object members by their simple names.

Scala's imports are more flexible than Java's: they may appear **anywhere** (not
just at the top), may import from **any object** and not only packages, and may
rename or hide individual members. The wildcard `_` imports everything;
importing an object's members (`import Constants._`) lets you write `NOP`
instead of `Constants.NOP`.

`ch03-build-and-testing/src/main/scala/usepack.scala`
```scala
import mypack.Abc      // single name
import mypack._        // wildcard: everything in the package
// ... or fully-qualified, with no import at all:
val x = new mypack.Abc()
```

You have already met the most important one. The next section starts with:

`src/main/scala/Hello.scala`
```scala
import chisel3._
```

That single wildcard import is what brings `Module`, `IO`, `Bundle`, `UInt`,
`RegInit`, `when`, and the `.U` / `.W` suffixes into scope. **Chisel is a
library, and this is the line that loads it** — there is no compiler magic
beyond it.

---

### 1.2.18 Scala vs. Chisel: the elaboration-vs-hardware line

This is the concept that trips up every newcomer, so it gets the last and
longest word before you write hardware.

**Your Scala program runs once, to *build* a circuit; the circuit then runs
forever in hardware.** Scala control flow shapes the circuit at build time;
Chisel control flow *is* circuitry.

| Scala (build time, runs once) | Chisel (hardware, runs every cycle) |
|---|---|
| `if (debug) ... else ...` — *decides whether to emit* hardware | `when(sel) { ... } .otherwise { ... }` — emits a **mux** that selects at runtime |
| `for (i <- 0 until 32)` — *unrolls*, emitting the body 32× | a counter register that counts at runtime |
| `val x = a + b` (Scala `Int`) — computed by the compiler | `val x = a + b` (Chisel `UInt`) — an **adder** in silicon |
| `var` accumulating a Scala list | a `Reg` accumulating a value each clock |

The short version: **a Scala `if` picks *one* branch to build; a Chisel `when`
builds a multiplexer that chooses at runtime.** Use Scala `if` / `for` to
*parameterize and generate* hardware; use `when` / `Mux` / `Vec` for behaviour
that varies while the chip is running. Chapter 5 returns to this question
directly ("Why `when` and not Scala's `if`?").

Here is the elaboration-time `if` again — it adds a port only in debug builds,
and in a non-debug build the hardware simply does not exist:

`ch02-basic-components/src/main/scala/RegisterFile.scala`
```scala
if (debug) { io.dbgPort.get := regfile }
```

For a striking parallel, *Programming in Scala* itself builds a **digital-circuit
simulator** as an embedded Scala DSL: wires carry boolean signals and *gate
boxes* (inverter, and-gate, or-gate — enough to build any circuit) transform
them. Its gate constructors are named as **nouns** and build gates as a *side
effect* rather than returning them, so the code reads as a *description* of a
circuit rather than a sequence of build actions — exactly the mindset Chisel
asks of you.

Keep that mindset for the next section. When you read
`cntReg := cntReg + 1.U`, do not read "add one to a variable." Read: *"there is
a 32-bit register, and its input is wired to the output of an adder whose other
input is the constant 1."*

---

### 1.2.19 The full run

For reference, the complete captured output of `sbt "runMain ScalaIntro"`:

```
--- 1.2.1 val vs var ------------------------------------
fixed = 42 (can never be reassigned)
running = 2 (reassigned twice)

--- 1.2.2 type inference --------------------------------
inferred=42 stated=42 text=forty-two

--- 1.2.3 literals --------------------------------------
hex=42 big=4294967295 char=A separated=1000000 double=100.0

--- 1.2.4 if and blocks are expressions -----------------
parity=even blockValue=25

--- 1.2.5 def methods -----------------------------------
square(7) = 49
METHODS END WITH THE VALUE OF THEIR BODY

--- 1.2.6 named and default arguments -------------------
frequency=50000000 baudRate=115200
frequency=1000 baudRate=10

--- 1.2.7 classes, new, extends -------------------------
Hello, Scala!
HELLO, CHISEL!

--- 1.2.8 object as singleton ---------------------------
Constants.NOP = 0x00 (one shared instance, no `new`)
and ScalaIntro itself is an object too - that is why it can be run

--- 1.2.9 operators are method calls --------------------
1 + 2      = 3
1.+(2)     = 3
6 & 3      = 2   (6.&(3))
'x' concat = abcd == abcd

--- 1.2.10 apply, the omitted method name ----------------
squares(2)        = 9
squares.apply(2)  = 9   (identical)
double(21)        = 42 == double.apply(21) = 42

--- 1.2.11 Seq, List, Array ------------------------------
numbers = List(1, 15, -2, 0), numbers(1) = 15
Seq.fill(4)(0) = List(0, 0, 0, 0)
Array.fill(3){...} made 3 distinct objects

--- 1.2.12 ranges and for --------------------------------
0 until 4 = List(0, 1, 2, 3)   (exclusive)
0 to 4    = List(0, 1, 2, 3, 4)   (inclusive)
  iteration i = 1
  iteration i = 2
  iteration i = 3

--- 1.2.13 while -----------------------------------------
  countdown = 3
  countdown = 2
  countdown = 1

--- 1.2.14 tuples ----------------------------------------
city._1 = 2000, city._2 = Frederiksberg
destructured: zipCode=2000 name=Frederiksberg

--- 1.2.15 string interpolation --------------------------
s: 7 squared is 49
f: pi is about 3.142
raw: a backslash-n stays literal: \n

--- 1.2.16 the standard library --------------------------
three random ints: 85, 88, 47

Done. Now go build some hardware.
```

That is the whole of the Scala you need to start. What remains — traits,
generics, `case class`, pattern matching, `Option`, and the functional
collection operators — is introduced by the chapter that first needs it, and
collected in [`SCALA-NOTES.md`](../SCALA-NOTES.md).

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

*Scala note — `class` / `extends` → [§1.2.7](#127-classes-new-and-extends); `val` vs `var` → [§1.2.1](#121-values-and-variables-val-vs-var); `import` → [§1.2.17](#1217-packages-and-imports); the `.U` / `.W` suffixes on literals → [§1.2.3](#123-literals).*

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

The first four exercises use only this project:

0. **Break the Scala on purpose.** In `src/main/scala/ScalaIntro.scala`, try
   each of these, then undo it:
   - reassign `fixed` (a `val`) — `sbt compile` says
     `reassignment to val` ([§1.2.1](#121-values-and-variables-val-vs-var));
   - move `def square` out to the top level of the file, outside any `object` —
     `sbt compile` says `expected class or object definition`
     ([§1.2.8](#128-singletons-object-and-the-program-entry-point));
   - add `val bad = 0755` and print it. This one **compiles**, and that is the
     lesson: you get `755`, not the 493 a C programmer expects, with no error
     and no warning ([§1.2.3](#123-literals)).

   Reading the two error messages — and being surprised by the third case —
   makes those three sections stick faster than reading them does.
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
