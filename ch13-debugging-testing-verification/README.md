# Chapter 13 — Debugging, Testing, and Verification

Chapter 3 introduced ChiselTest; this chapter digs deeper into how to **debug**,
**test**, and **verify** hardware. It covers waveform/printf debugging, making
tests readable with helper functions, selecting tests with **tags**, reaching
**internal signals** with `BoringUtils`, **multithreaded** tests, simulator
**backends**, and finally **assertions** and **formal verification**.

**Testing vs. verification — a note on terminology.** In software development,
*testing* means running tests against components, while *verification* is
usually shorthand for *formal* verification (mathematical proofs or exhaustive
model checking). Digital design borrows *testing* in the same sense — writing
test benches that stimulate and check a device under test (DUT) — but the word
is overloaded: it's also used for the physical test of a manufactured chip on a
tester, using built-in self-tests. Because of that overlap, the digital-design
community is slowly shifting toward calling this *verification* instead, and
reserving *formal verification* for the SMT/model-checking flavor. This book
sticks with **testing** throughout, for consistency. Either way, verification
can be **dynamic** (running the design on a simulator — what Chapter 3 and most
of this chapter do) or **formal** (a model checker or SMT solver proves a
property for *all* inputs, up to a bound — §13.4).

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

ChiselTest is built on ScalaTest, so `sbt test` runs everything. ScalaTest also
runs multiple test **classes** in parallel by default (multithreading at the
class level) — that's separate from the fork/join threading *inside* a single
test, covered below. A test is a class extending `AnyFlatSpec` with the
`ChiselScalatestTester` trait; inside it, `poke`, `peek`, `expect`, and `step`
operate on **Chisel types** (`UInt`/`SInt`/`Bool`). Since test code is Scala,
`peekInt()` and `peekBoolean()` are also available to convert a peek to a plain
Scala `BigInt`/`Boolean`. Run everything with `sbt test`, or one suite with
`sbt "testOnly Name"`.

The simplest possible test just wraps a few pokes/expects in `test(...)`, here
checking a BCD lookup table:

```scala
class BcdTableTest extends AnyFlatSpec with ChiselScalatestTester {
  "BCD table" should "output BCD encoded numbers" in {
    test(new BcdTable) { dut =>
      dut.io.address.poke(0.U)
      dut.io.data.expect("h00".U)
      dut.io.address.poke(1.U)
      dut.io.data.expect("h01".U)
      dut.io.address.poke(13.U)
      dut.io.data.expect("h13".U)
      dut.io.address.poke(99.U)
      dut.io.data.expect("h99".U)
    }
  }
}
```
*illustrative — the book's simplest `test(...)` example*

An equivalent `behavior of` / `it should` form reads well once a module has
several tests:

```scala
class BcdTableTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BCD table"

  it should "output BCD encoded numbers" in {
    test(new BcdTable) { dut => /* ... */ }
  }
}
```
*illustrative*

**A worked example: the counter device.** A first pass at testing the counter
device from Chapter 12 pokes and expects every signal by hand — it works, but
covers only a couple of cases and is already tedious to read.

**Make tests readable with functions.** Raw `poke`/`expect` sequences get long
and hard to follow. Wrapping a protocol in helper functions (as the interconnect
tests in Chapter 12 do with `read`/`write`/`step`) hides the "bit-banging" and
covers more cases in fewer lines. That pattern is the single biggest readability
win for non-trivial test benches. The counter device's `read`/`write` look like:

```scala
def step(n: Int = 1) = dut.clock.step(n)

def read(addr: Int) = {
  dut.io.address.poke(addr.U)
  dut.io.rd.poke(true.B)
  step()
  dut.io.rd.poke(false.B)
  while (!dut.io.ack.peekBoolean()) { step() }
  dut.io.rdData.peekInt()
}

def write(addr: Int, data: Int) = {
  dut.io.address.poke(addr.U)
  dut.io.wrData.poke(data.U)
  dut.io.wr.poke(true.B)
  step()
  dut.io.wr.poke(false.B)
  while (!dut.io.ack.peekBoolean()) { step() }
}
```
*illustrative — `read`/`write` helpers from the book's counter-device test*

`read` pokes the address and asserts `rd`, steps the clock once, deasserts
`rd`, then polls `io.ack` with `peekBoolean()` (a Scala `Boolean`) in a loop
until the device acknowledges — generalizing beyond this device's one-cycle
latency to devices that take longer. It finally reads `io.rdData` with
`peekInt()`, which returns a Scala `BigInt` so it can express integers of any
width. `write` is symmetric. **Caveat:** if a device never asserts `ack`, this
polling loop hangs forever; a robust version should add a timeout around it.
Writing the more thorough, function-based test actually caught a real bug: an
off-by-one error (`until 3` instead of `until 4`) in the counter device that
the original hand-written, bit-banging test had missed.

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

If your tests (and tags) live inside a package, remember to give the **full
reference path** to both the test and the tag — a bare class/tag name won't
resolve.

### Accessing internal signals with `BoringUtils`

Tests normally see only the ports — good practice. But sometimes you need an
internal signal — e.g. comparing a CPU's register file against a reference
model, since all data that's computed, loaded, or stored eventually passes
through the register file. Another use case is exploring and testing a state
machine (with or without a datapath) with direct access to its internal state.
Rather than clutter the design with debug ports, `BoringUtils.bore` "bores" a
connection out through the hierarchy, adding the needed ports automatically.
At the time of writing, `BoringUtils` is still considered **experimental**.

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
or **VCS** by adding a backend annotation to `.withAnnotations(...)`:

```scala
test(new Dut()).withAnnotations(Seq(VerilatorBackendAnnotation)) {
  c => testFun(c)
}
```
*illustrative — switching the backend to Verilator*

`VerilatorFlags` and `VerilatorCFlags` annotations pass extra switches straight
through to the Verilator simulation command and to GCC, respectively (consult
the tool's manual for the flag list). These are advanced, seldom-needed
features and are **not guaranteed to remain stable** across releases.
ChiselTest 0.3.4+ also supports code-coverage measurement directly in
simulation, which requires Verilator **4.028 or newer**.

The backends differ in what they simulate: Verilator is a **synchronous**
simulator (updates only on the rising clock edge), so it has no latches and
does **not officially support multiple clocks**. VCS is **event-based** and
supports all synthesizable Verilog constructs, including latches and multiple
clocks, at the cost of being closed-source/commercial. For single-clock
circuits, Verilator is generally the fastest and most widely available choice.

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

A failing assertion stops the simulation with a message like:

```
Assertion failed
    at Assert.scala:20 assert(sum <= a + b)
```

**Style tip:** place all assertions at the end of a module, so they don't
clutter the reading of the module's intended design.

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
    verify(new Assert(), Seq(BoundedCheck(5), WriteVcdAnnotation))
  }
}
```

Run formally against the naive overflow assertions and the solver immediately
finds a counterexample (e.g. `0xdb + 0x65` overflows to `0x40`), proving
`sum >= a` is false — a bug a small test suite would miss. Adding
`WriteVcdAnnotation` to the annotation list (as above) dumps the counterexample
trace to a `.vcd`, so you can open it in GTKWave the same way as a regular
simulation waveform.

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

[Extreme programming](https://en.wikipedia.org/wiki/Extreme_programming) is an
agile software-development style built around quick turnaround times and a
strong reliance on unit tests; in its purest form you write the tests *before*
implementing a feature. It's not used all that often in real life, but
exploring it is a good way to focus on testing as a first-class part of
building something.

Practice test-first design: pick a small circuit from Chapter 7 (debouncer or
majority filter), write its test bench *before* implementing it, then build the
design. Afterward, inject a fault into the DUT and confirm your tests catch it.

Reflect on the experience. Did your tests find errors in your design? If all
tests pass, are you sure they cover a reasonable design space? How do you test
your tests?

You may come away with the uncomfortable feeling that testing is hard and it's
probably impossible to catch every error — echoing Dijkstra's famous line,
"testing shows the presence of bugs, not their absence." Formal verification
(§13.4) is the field's answer to that gap; the topic will be extended further
in a future edition of this book.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 12 — Interconnect](../ch12-interconnect/README.md)**.
Next: **[Chapter 14 — Design of a Processor](../ch14-design-of-a-processor/README.md)**.
