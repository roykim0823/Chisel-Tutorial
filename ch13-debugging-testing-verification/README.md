# Chapter 13 — Debugging, Testing, and Verification

Chapter 3 introduced ChiselTest; this chapter digs deeper into how to **debug**,
**test**, and **verify** hardware. It covers waveform/printf debugging, making
tests readable with helper functions, selecting tests with **tags**, reaching
**internal signals** with `BoringUtils`, **multithreaded** tests, simulator
**backends**, and finally **assertions** and **formal verification**.

*Conventions: every file path is relative to
`tutorial/ch13-debugging-testing-verification/`, and every command is run from
that folder. This chapter has no figures.*

## What's in this project

```
ch13-debugging-testing-verification/
├── build.sbt · project/build.properties
├── src/main/scala/
│   ├── Assert.scala    an adder with a Chisel assert
│   ├── Boring.scala    a tick generator + a BoringUtils test wrapper
│   └── Generate.scala
└── src/test/scala/
    ├── AssertTest.scala   assertions during simulation
    ├── BoringTest.scala   observing an internal counter
    └── TagTest.scala      tagging tests for selection
```

---

## 13.1 Debugging

Two everyday techniques: **waveform debugging** (attach `WriteVcdAnnotation`,
open the `.vcd` in GTKWave — Chapter 3) and **printf debugging** (a `printf`
inside a module prints on each rising clock edge during simulation — Chapter 3).
Because hardware runs in parallel, waveforms are the go-to for seeing many
signals evolve over time.

---

## 13.2 Testing in Chisel

ChiselTest (on ScalaTest) uses `poke`, `peek`, `expect`, and `step` on the DUT's
ports; `peekInt()`/`peekBoolean()` return Scala values. Run everything with
`sbt test`, or one suite with `sbt "testOnly Name"`.

**Make tests readable with functions.** Raw `poke`/`expect` sequences get long
and hard to follow. Wrapping a protocol in helper functions (as the interconnect
tests in Chapter 12 do with `read`/`write`/`step`) hides the "bit-banging" and
covers more cases in fewer lines. That pattern is the single biggest readability
win for non-trivial test benches.

### Selecting tests with tags

Tag tests to include/exclude them from a run:

`src/test/scala/TagTest.scala`
```scala
object Unnecessary extends Tag("Unnecessary")

class TagTest extends AnyFlatSpec with Matchers {
  "Integers" should "add" taggedAs (Unnecessary) in {
    17 + 25 should be(42)
  }
}
```

Exclude the tagged tests with:

```
$ sbt "testOnly * -- -l Unnecessary"
```

which reports them as not run:

```
[info] TagTest:
[info] Tests: succeeded 0, failed 0, canceled 0, ignored 0, pending 0
[info] No tests were executed.
```

### Accessing internal signals with `BoringUtils`

Tests normally see only the ports — good practice. But sometimes you need an
internal signal (e.g. compare a CPU's register file against a reference model).
Rather than clutter the design with debug ports, `BoringUtils.bore` "bores" a
connection out through the hierarchy, adding the needed ports automatically.

Our `TickGen` exposes only `tick`; a **test wrapper** bores out the hidden
`cntReg`:

`src/main/scala/Boring.scala`
```scala
import chisel3.util.experimental.BoringUtils

class TickGenTestTop extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
    val counter = Output(UInt(8.W))
  })
  val tickGen = Module(new TickGen)
  io.tick := tickGen.io.tick
  io.counter := DontCare                       // keep the compiler happy...
  BoringUtils.bore(tickGen.cntReg, Seq(io.counter))  // ...then bore the connection
}
```

`BoringTest` then checks the internal counter directly through `io.counter`.

### Multithreaded testing (fork/join)

Hardware is parallel, and so can the test be: `fork { ... }` spawns a tester
thread, `.join()` waits for it. Threads synchronize on `step`, and no two may
`poke`/`peek` the same signal at once. We used this in Chapter 11's
`BubbleFifoTest` (one thread enqueues while the main thread dequeues).

### Simulator backends

By default ChiselTest uses **Treadle** (fast startup, no extra install). For
large designs or features Treadle lacks, switch to **Verilator** (open-source)
or **VCS** by adding a backend annotation to `.withAnnotations(...)`. Verilator
is a synchronous simulator (no latches, single-clock); VCS is event-based.

---

## 13.3 Assertions

A Chisel `assert` states an assumption. It's checked in **simulation** (the run
stops with a message on failure) and **ignored** in hardware generation:

`src/main/scala/Assert.scala`
```scala
io.sum := io.a + io.b

/* NOT always true — an 8-bit add can overflow:
assert(io.sum >= io.a)
assert(io.sum >= io.b)
 */
assert(io.sum === io.a + io.b)
```

`AssertTest` runs it, including `a=100, b=200` (300 wraps to 44 in 8 bits) — the
kept assertion still holds. The two commented-out assertions look reasonable but
are **false on overflow** — exactly the kind of corner case a hand-written test
usually misses, which motivates formal verification.

---

## 13.4 Formal verification

Testing shows the *presence* of bugs, not their *absence*. **Formal
verification** checks a property for *all* inputs (up to a bound) using an SMT
solver. In ChiselTest you swap `test(...)` for `verify(...)` and reuse the very
same `assert`s; `assume(...)` constrains inputs, and `past(x)` refers to a
previous cycle's value.

*illustrative — requires the Z3 solver installed*
```scala
import chiseltest.formal._

class FormalTest extends AnyFlatSpec with ChiselScalatestTester with Formal {
  "Assert" should "pass" in {
    verify(new Assert(), Seq(BoundedCheck(5)))
  }
}
```

Run formally against the naive overflow assertions and the solver immediately
finds a counterexample (e.g. `0xdb + 0x65` overflows to `0x40`), proving
`sum >= a` is false — a bug a small test suite would miss.

> **Not runnable here:** formal verification needs the
> [Z3](https://github.com/Z3Prover/z3) theorem prover, which isn't installed in
> this environment, so this project ships no formal test. Install Z3 and add a
> `FormalTest` to try `verify` yourself.

---

## 13.5 Build, run, and check

```
$ sbt test
```

Expected (3 tests):

```
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog (the `assert` is dropped in generation):

```
$ sbt "runMain Generate"
```

emits `Assert.sv` and `TickGenTestTop.sv`.

---

## 13.6 Recap

- Debug with **waveforms** (VCD + GTKWave) and **printf**.
- Make test benches readable with **helper functions**; **tag** tests to select
  subsets (`-l Tag`).
- Reach internal signals without debug ports using **`BoringUtils.bore`**.
- Use **fork/join** for parallel test threads; switch **backends** (Treadle →
  Verilator/VCS) for speed or features.
- **`assert`** checks assumptions in simulation; **formal verification**
  (`verify` + Z3) proves them for all inputs and catches corner cases like
  overflow.

## 13.7 Exercise

Practice test-first design: pick a small circuit from Chapter 7 (debouncer or
majority filter), write its test bench *before* implementing it, then build the
design. Afterward, inject a fault into the DUT and confirm your tests catch it.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 12 — Interconnect](../ch12-interconnect/README.md)**.
Next: **[Chapter 14 — Design of a Processor](../ch14-design-of-a-processor/README.md)**.
