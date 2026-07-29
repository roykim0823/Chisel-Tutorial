# Anatomy of `CounterTest.scala`

Six counter modules, six one-line tests, and **one** shared bench that checks
all of them. `CounterTest.scala` is short, but almost every line of it uses a
Scala or ChiselTest idiom that does not mean what it looks like: a pair of
braces that is really a function argument, a module that is not built where it
is written, a loop counter that starts at `-1` on purpose. This page walks the
file top to bottom and explains each of those.

*Conventions: every file path is relative to
`tutorial/ch06-sequential-building-blocks/`, and every command is run from that
folder. A block labelled with a project path is verbatim from that file; an
italic note underneath flags anything excerpted or annotated. Blocks labelled
`chiseltest 6.0.0 · chiseltest/…` are verbatim **library** source at the stated
lines (`…` marks an omission).*

> **Where the library source comes from.** ChiselTest is this chapter's
> dependency — `libraryDependencies += "edu.berkeley.cs" %% "chiseltest" % "6.0.0"`
> in `build.sbt` — so `chiseltest/ChiselScalatestTester.scala` is a path *inside
> that library's sources jar*, not a file in this repository; searching the repo
> for it finds nothing. sbt compiles against the **binary** jar, and the sources
> jar is for reading only, so the build works with or without it. To read along:
>
> ```
> $ find ~/.cache/coursier -name 'chiseltest_2.13-*-sources.jar'
> $ unzip -o -q <that path> -d /tmp/chiseltest-src
> $ ls /tmp/chiseltest-src/chiseltest/
> ```

---

## 1. The shape of the file

`src/test/scala/CounterTest.scala` has two halves that do completely different
jobs:

```
import chisel3._ / chiseltest._ / AnyFlatSpec     the three vocabularies

trait CountTest                                   ① the checking algorithm
  └ def testFn[T <: Counter](c: T, n: Int)           (reusable, DUT-agnostic)

class CounterTest extends AnyFlatSpec             ② the list of what to check
    with ChiselScalatestTester with CountTest        (no checking logic at all)
  └ "…" should "count" in { test(dut) { c => testFn(c, n) } }   × 6
```

| Half | Contains | Does **not** contain |
|---|---|---|
| `trait CountTest` | the whole bench: the loop, `expect`, `peek`, `step` | any mention of a specific counter |
| `class CounterTest` | six lines naming a module and its `n` | any checking logic |

That split is the point of the file. `Counter.scala` defines an abstract
`Counter` base, five concrete variants implement it differently, and because
they all expose the same `io`, a single `testFn` can drive every one of them.
The test never looks at `cntReg` or at any internal signal — only at the
`io.tick` output — so it stays valid no matter how a counter is implemented.

---

## 2. The three imports

`src/test/scala/CounterTest.scala`
```scala
import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
```

`_` is Scala's wildcard, the equivalent of Java's `*`
(*Scala note — [§A.3](../../SCALA-NOTES.md#a3-package-and-import)*). Each
import brings in one vocabulary, and they sit on opposite sides of an important
line:

| Import | Supplies | Which world |
|---|---|---|
| `chisel3._` | `UInt`, `Bool`, `false.B` … | **hardware** — becomes a circuit |
| `chiseltest._` | `test()`, `expect()`, `peekBoolean()`, `clock.step()` | **testbench** — drives a simulator |
| `AnyFlatSpec` | `"…" should "…" in { }` | **testbench** — registers tests |

The `var`, `for`, and `if` inside `testFn` are ordinary Scala running on the
JVM. They are *not* hardware: nothing in this file is synthesized. It is a
program that builds a circuit, starts a simulator, and pokes at it
(*Scala note — [§I](../../SCALA-NOTES.md#i-scala-vs-chisel-the-elaboration-vs-hardware-line)*).

---

## 3. `class CounterTest` — the list of what to check

`src/test/scala/CounterTest.scala`
```scala
class CounterTest extends AnyFlatSpec with ChiselScalatestTester with CountTest {
  "WhenCounter 4" should "count" in { test(new WhenCounter(4)) { c => testFn(c, 4) } }
  "WhenCounter 7" should "count" in { test(new WhenCounter(7)) { c => testFn(c, 7) } }
  "MuxCounter 5" should "count" in { test(new MuxCounter(5)) { c => testFn(c, 5) } }
  "DownCounter 7" should "count" in { test(new DownCounter(7)) { c => testFn(c, 7) } }
  "FunctionCounter 8" should "count" in { test(new FunctionCounter(8)) { c => testFn(c, 8) } }
  "NerdCounter 13" should "count" in { test(new NerdCounter(13)) { c => testFn(c, 13) } }
}
```

Scala allows exactly one `extends` and any number of `with` mix-ins
(*Scala note — [§A.4](../../SCALA-NOTES.md#a4-trait--mixed-in-with-with)*).
Each of the three supplies one piece of syntax used above:

| Mix-in | Supplies |
|---|---|
| `AnyFlatSpec` | `"…" should "…" in { … }` |
| `ChiselScalatestTester` | `test(…)` |
| `CountTest` | `testFn` |

`should` and `in` are not keywords — they are methods ScalaTest attaches to a
`String`, which is why the declaration reads like English
(*Scala note — [§K](../../SCALA-NOTES.md#k-scalatest-dsl-reads-like-english-is-really-scala)*).
The result is a test registered under the name **"WhenCounter 4 should count"**;
that string is what a failure report prints, and it is also what ChiselTest uses
to name the test's output directory under `test_run_dir/`.

The `n` values differ on purpose — 4, 7, 5, 7, 8, 13 — so the variants are
exercised at even and odd, small and large periods.

---

## 4. Anatomy of one line

`src/test/scala/CounterTest.scala:26`
```scala
test(new WhenCounter(4)) { c => testFn(c, 4) }
    └── parentheses ───┘ └───── braces ──────┘
```
*excerpt — the test body of line 26, with the two spans marked*

### 4.1 Why parentheses *and* braces — there are two calls

`test` is not part of Chisel and not a keyword — it is a method that
`ChiselScalatestTester` mixes into the test class, and it has exactly **one**
parameter list:

`chiseltest 6.0.0 · chiseltest/ChiselScalatestTester.scala:140-142`
```scala
  def test[T <: Module](dutGen: => T): TestBuilder[T] = {
    new TestBuilder(() => dutGen, Seq(), Seq())
  }
```

Because there is only one parameter list, the trailing braces **cannot** be an
argument to `test`. They attach instead to the object `test` returns, a
`TestBuilder` — and they can, because that class defines `apply`:

`chiseltest 6.0.0 · chiseltest/ChiselScalatestTester.scala:21-24, 29-31`
```scala
  class TestBuilder[T <: Module](
    val dutGen:              () => T,
    val annotationSeq:       AnnotationSeq,
    val chiselAnnotationSeq: firrtl.AnnotationSeq) {
    …
    def apply(testFn: T => Unit): TestResult = {
      runTest(dutGen, finalAnnos(annotationSeq), chiselAnnotationSeq, testFn)
    }
```

Note the type of `apply`'s parameter: `testFn: T => Unit`, a **function** from
the DUT to nothing. That signature is what the braces have to satisfy.

`obj(arg)` is shorthand for `obj.apply(arg)` — the one method name Scala lets
you omit — and a single-argument list may be written with braces instead of
parentheses (*Scala note —
[§J.7](../../SCALA-NOTES.md#j7-apply-the-one-method-name-you-may-omit)*). So the
line really means `test(new WhenCounter(4)).apply(c => testFn(c, 4))`: the
parentheses are the first call, the braces the second.

The braces look like a block but are a **function literal** — `c` is its
parameter declaration, and the name is free (`dut`, `x`, anything)
(*Scala note — [§E.1](../../SCALA-NOTES.md#e1-function-literals-lambdas-and-the--arrow)*).

That one line carries the rest. `dutGen` and `testFn` travel into `runTest`
**together**, so that is where the recipe is finally called — the module is
built there, not where you wrote `new WhenCounter(4)` — and where your function
is applied to the result. `apply`'s parameter type says what the function
receives: a `T`, which is that very module. So `c` **is** the elaborated
`WhenCounter(4)`, and it reaches the bench unchanged as its own `c`.

One thing the signature does not show but the bench relies on: `runTest` also
**resets the DUT before calling your function**, so `cntReg` already holds its
`RegInit` value `0` on the first iteration — which is why the bench contains no
reset handling of its own (see the implicit-reset note in
[§6.1 of the chapter](../README.md#61-registers)).

The *static* type differs at each hop, though: inside the lambda `c` is the
concrete `WhenCounter`, inside the bench it is narrowed to `T <: Counter`, so
only the members declared on `Counter` are visible there
([§5.1](#51-the-trait-and-its-type-bound)).

### 4.2 Summary of the one line

| Fragment | Runs when | Produces |
|---|---|---|
| `test(...)` — parentheses | immediately | `TestBuilder` + the recipe `() => new WhenCounter(4)` |
| `{ c => … }` — braces | immediately | a function value, passed to `TestBuilder.apply` |
| inside `apply` | on `apply` | elaborated DUT, running simulator, reset applied |
| `testFn(dut)` | last | the checking loop, with `c` bound to the DUT |

---

## 5. `trait CountTest` — the shared bench

### 5.1 The trait and its type bound

`src/test/scala/CounterTest.scala`
```scala
trait CountTest {
  def testFn[T <: Counter](c: T, n: Int) = {
```
*excerpt — the trait header and the bench's signature*

`trait` is Scala's interface-with-implementation; mixing it in with `with` makes
`testFn` available as if it were declared in the test class
(*Scala note — [§A.4](../../SCALA-NOTES.md#a4-trait--mixed-in-with-with)*).
The return type is omitted and inferred as `Unit`
(*Scala note — [§C.5](../../SCALA-NOTES.md#c5-block-as-expression-implicit-return)*).

`[T <: Counter]` is a type parameter with an **upper bound**: `T` may be
`Counter` or any subtype, and nothing else
(*Scala note — [§D.1](../../SCALA-NOTES.md#d1-type-parameters-t-with-an-upper-bound-t--x)*).
The bound is what makes `c.io` legal, because the base class declares it:

`src/main/scala/Counter.scala`
```scala
abstract class Counter(n: Int) extends Module {
  val io = IO(new Bundle {
    val cnt = Output(UInt(8.W))
    val tick = Output(Bool())
  })
}
```

Written as a bare `[T]`, the body would not compile: the compiler would have no
reason to believe an arbitrary `T` has an `io` at all. Conversely, a module that
does not extend `Counter` — `Count100`, which has no `tick` — is rejected **at
compile time**, before any simulation starts.

> `test` itself is declared `[T <: Module]`. `testFn` needs a narrower bound
> because it dereferences `io.tick`, which only `Counter` guarantees.

### 5.2 The loop skeleton

`src/test/scala/CounterTest.scala`
```scala
var count = -1
for (_ <- 0 until n * 3) {
```
*excerpt — the loop header, dedented*

- `var` is a reassignable variable, unlike `val`
  (*Scala note — [§C.1](../../SCALA-NOTES.md#c1-val-vs-var)*). It is **not** a
  register — it is the bench's own prediction of the DUT's state.
- `0 until n * 3` is a `Range` covering `0 … n*3-1`; `until` excludes the end
  bound, `to` would include it
  (*Scala note — [§F.2](../../SCALA-NOTES.md#f2-ranges-until-exclusive-vs-to-inclusive)*).
- `_` in the generator position discards the loop index, which is never used
  (*Scala note — [§H.1](../../SCALA-NOTES.md#h1-for-over-a-range)*).
- `n * 3` runs the DUT for **three full periods**, so a tick pattern has to
  repeat rather than coincide once.

### 5.3 What happens in one cycle

`src/test/scala/CounterTest.scala`
```scala
if (count > 0)
  c.io.tick.expect(false.B)     // ① must not tick yet
if (count == 0)
  c.io.tick.expect(true.B)      // ② must tick now

if (c.io.tick.peekBoolean())    // ③ observe, re-synchronize the prediction
  count = n - 1
else
  count -= 1
c.clock.step()                  // ④ advance one clock cycle
```
*excerpt — the loop body, dedented; the ①–④ markers are added here, not in the
file*

The distinction between ① ② and ③ is the heart of the bench:

| Call | Kind | Effect |
|---|---|---|
| `expect(false.B)` / `expect(true.B)` | **assertion** | fails the test on the spot if the pin differs |
| `peekBoolean()` | **observation** | reads the pin into a Scala `Boolean`; never fails |
| `clock.step()` | **stimulus** | advances the simulation one cycle; registers update here |

`false.B` / `true.B` lift a Scala `Boolean` into a Chisel `Bool` literal, which
is what `expect` compares against a hardware pin
(*Scala note — [§J.2](../../SCALA-NOTES.md#j2-literals)*). ChiselTest offers
both `peek()` (returns a Chisel `Bool`) and `peekBoolean()` (returns a Scala
`Boolean`); the `if` needs the Scala one.

Order matters: everything before `step()` observes **the current cycle**, and
the register contents only change when `step()` is called.

### 5.4 Why `count` starts at `-1`

`count` means *"cycles remaining until the next tick"*. At the start of a test
the bench has no idea where in its period the DUT is — `DownCounter` starts at
`N`, `NerdCounter` at `N-2`, the up-counters at `0`. A negative value satisfies
neither `count > 0` nor `count == 0`, so **both assertions are skipped**: the
opening cycles are a silent observation window.

The moment a tick is actually seen, step ③ sets `count = n - 1` and the
prediction is locked to the DUT's phase. From then on every cycle is checked.
This is why one unmodified bench works for counters that reset to different
starting phases.

### 5.5 Cycle by cycle, for `WhenCounter(4)`

`N = 3`, `cntReg` runs `0→1→2→3→0…`, and `tick = (cntReg === 3)`.

| iter | `cntReg` | `tick` | `count` at check | what happens |
|---|---|---|---|---|
| 0 | 0 | 0 | -1 | both checks skipped → `count = -2` |
| 1 | 1 | 0 | -2 | skipped → -3 |
| 2 | 2 | 0 | -3 | skipped → -4 |
| 3 | 3 | **1** | -4 | skipped; tick observed → **`count = 3`** (locked) |
| 4 | 0 | 0 | 3 | `expect(false)` ✓ → 2 |
| 5 | 1 | 0 | 2 | `expect(false)` ✓ → 1 |
| 6 | 2 | 0 | 1 | `expect(false)` ✓ → 0 |
| 7 | 3 | **1** | **0** | **`expect(true)` ✓** → 3 |
| 8–11 | | | | the same four-cycle pattern twice more |

That is exactly the waveform drawn as **Figure 6.5a** in
[§6.2 of the chapter](../README.md#generating-timing-ticks): one single-cycle
pulse every `n` cycles. To see the real thing, run
`sbt "testOnly CounterTest -- -DwriteVcd=1"` and open the `.vcd` it leaves in
`test_run_dir/` (see
[§3.2.3 Waveforms](../../ch03-build-and-testing/README.md#323-waveforms)).

---

## 6. What `c` actually is

`c.io.tick` does not hold a value. It is a Chisel `Data` object — a *handle*
naming a port of the elaborated design. The simulator holds the values, and
`expect` / `peekBoolean` / `step` are not methods of `Bool` or `Clock` at all:
ChiselTest attaches them through implicit classes that look up the currently
running simulation and address it by port name.

Two practical consequences:

- Only **ports** can be peeked and poked. There is no `c.cntReg.peek()`; the
  name map is built from the module's IO.
- The handle is meaningful **only inside the `test(...) { … }` block**. Saving
  `c` to an outer variable and touching it afterwards throws, because by then
  the simulator has been shut down.

---

## 7. End to end

```
"WhenCounter 4" should "count" in { … }        registered with ScalaTest
        │
        ↓ ScalaTest runs the test body
test(new WhenCounter(4))                       parentheses: package the recipe
        │   ⇒ TestBuilder (no module, no simulator)
        ↓
     .apply { c => testFn(c, 4) }               braces: hand over the lambda
        ├─ dutGen()          → WhenCounter instance = dut
        ├─ compile + start the simulator
        ├─ reset asserted for one cycle         → cntReg = 0
        └─ testFn(dut)        → c bound to dut
                │
                ↓
        testFn(c, 4)                            12 cycles of expect / peek / step
```

Each of the six lines gets its **own elaboration, own simulator, and own
module instance**. They share the checking procedure and nothing else — no
state leaks between them.

---

## 8. Takeaways

- `test(dut) { lambda }` is **two calls**: `test(dut)` returns a `TestBuilder`,
  and the braces are a function literal passed to that builder's `apply`.
- The DUT is elaborated inside `apply`, not at the `test(...)` call site — the
  by-name parameter stores a recipe so construction happens in a valid
  elaboration context.
- The elaborated instance is bound to the lambda's parameter and forwarded
  unchanged into `testFn` — the DUT, the lambda's `c`, and the bench's `c` are
  all the same object.
- `T <: Counter` is what lets one bench drive five implementations while
  rejecting unrelated modules at compile time.
- `expect` asserts, `peek` observes, `step` advances — and starting the
  prediction at `-1` lets the bench synchronize to whatever phase the DUT
  happens to reset into.

> **One gap worth knowing.** If a counter never asserts `tick` at all, `count`
> only ever decreases, no `expect` is ever reached, and the test passes
> vacuously. Asserting that at least one tick was observed within `n * 3`
> cycles would close it — a small, worthwhile exercise on top of Exercise 1 in
> the chapter.

---

Back to **[Chapter 6 — Sequential Building Blocks](../README.md)**.
