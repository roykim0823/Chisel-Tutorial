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

### Which artifact are your tests actually running?

There is a more fundamental difference between the backends than speed, and it
is easy to miss: **Treadle and Verilator do not simulate the same thing.**

| backend | what it executes | is your SystemVerilog involved? |
|---|---|---|
| **Treadle** (default) | the **FIRRTL** intermediate representation, interpreted on the JVM | **no** |
| **Verilator** / **VCS** | the **generated SystemVerilog**, compiled to a binary | **yes** |

Treadle never produces Verilog at all. You can see this directly — run this
chapter's tests and look at what is left behind in `test_run_dir/`:

```
$ sbt test
$ find test_run_dir -type f
test_run_dir/Assert_should_hold_even_across_an_overflowing_add/Assert.lo.fir
test_run_dir/Boring_should_expose_the_internal_counter/TickGenTestTop.lo.fir
```

Only `.lo.fir` files — *lowered FIRRTL*. Not one `.sv`. (Chapter 5 gives the
same picture at larger scale: eleven `.lo.fir`, zero `.sv`.)

**Why this matters.** FIRRTL is the stage *before* firtool does its work, and
firtool changes the design substantially: it eliminates registers nothing reads,
narrows arithmetic when the extra bits cannot be observed, and turns `when`
chains and `switch` statements into lookup tables. Chapter 6's `Registers`
module declares four registers and emits one; Chapter 5's `Arbiter3Direct`
becomes a packed array rather than a gate chain. See
[`SYSTEMVERILOG-NOTES.md`](../SYSTEMVERILOG-NOTES.md) for the measured examples.

So "the tests passed" means **the FIRRTL passed**. In practice the lowering is
semantics-preserving and heavily tested, so this rarely bites — but the two are
different artifacts, and if you want the shipped RTL exercised, you must ask for
it by switching the backend. That is a one-line change and the *same test code*
runs either way:

```scala
test(new Dut()).withAnnotations(Seq(VerilatorBackendAnnotation)) { c => testFun(c) }
```

This is the point worth internalizing: **you do not write a SystemVerilog
testbench to test the generated SystemVerilog.** Your existing `poke`/`step`/
`expect` test drives the real emitted RTL; only the backend changes.

Chisel 6 also ships a second, newer path — `chisel3.simulator` (svsim) — which
always goes through generated SystemVerilog, with no FIRRTL-interpreter option:

```scala
import chisel3.simulator.EphemeralSimulator._

simulate(new Comparator()) { dut =>
  dut.io.a.poke(3.U); dut.io.b.poke(3.U); dut.clock.step()
  dut.io.equ.expect(true.B)
}
```
*illustrative — the svsim path, which always compiles the SystemVerilog*

> **Version warning, verified on this toolchain.** Neither Verilog-level path
> runs against **Verilator 5.050**, which is much newer than the pinned Chisel
> 6.5.0 / chiseltest 6.0.0. Two independent failures:
>
> - `VerilatorBackendAnnotation` → `error: unknown type name 'WData'` while
>   compiling chiseltest's C++ harness (Verilator changed that API).
> - `EphemeralSimulator` → `java.lang.Exception: Unexpected message: Ready`
>   (the simulator binary builds and starts, then the handshake protocol
>   mismatches).
>
> Both are version skew, not design problems — the Verilog itself is fine. If
> you need the Verilog-level path, pair the pinned Chisel with a Verilator from
> the same era, or move to a newer Chisel/chiseltest. The chapters here all pass
> on Treadle, which is why the tutorial does not require Verilator.

### How this scales up: verifying a real design

Unit tests against a reference model — Chapter 14 checks `AluAccu` against a
plain-Scala `alu` function — are the bottom of a ladder that real projects
climb. It is worth knowing the rest of it, because **from the second rung up,
everything runs on the generated Verilog, not on Chisel**:

1. **Unit tests vs. a reference model** — Chapter 14's approach. The only rung
   that lives in Chisel-land.
2. **ISA test suites** — for a RISC-V core, `riscv-tests` (`rv32ui-p-add` and
   friends) compiled to ELF, loaded into the core's memory, run on the RTL
   simulation.
3. **Architectural compliance** — RISCOF / `riscv-arch-test` run the same
   program on the design and on a golden model (Sail, Spike) and compare
   signature dumps.
4. **Co-simulation** — run the core and an ISA simulator in lockstep and compare
   every committed instruction. Chapter 15's Wildcat does exactly this against a
   Scala ISA model in its [own repository](https://github.com/schoeberl/wildcat);
   Chipyard uses Dromajo for Rocket and BOOM.
5. **Formal** — `riscv-formal` defines an interface (RVFI) that cores expose so
   SystemVerilog properties can be model-checked (§13.4 covers the Chisel side).
6. **UVM** — the industry bench style: constrained-random stimulus, scoreboards,
   coverage closure. OpenHW's `core-v-verif` is the well-known open example.
7. **FPGA emulation and booting Linux** — the ultimate smoke test.
8. **Post-synthesis** — gate-level simulation and logical equivalence checking.

The reason this works is that from step 2 onward the tests consume **RTL plus a
binary** — nothing about them knows the core was written in Chisel. That is why
Rocket Chip (Chisel) and CVA6 (SystemVerilog) can share `riscv-tests`, and it is
the strongest practical evidence that the Chisel → SystemVerilog lowering is
trustworthy: your generated Verilog is checked by the same suite that checks
hand-written cores.

**When you do still write SystemVerilog test code.** Chisel test code covers
module-level verification well, but not everything:

- running software on a core (loading an ELF and simulating for millions of
  cycles) is normally driven by a C++ or SV harness for speed;
- UVM has no Chisel equivalent;
- multi-cycle **SVA** properties (`|->`, `##1`, `throughout`) are not
  expressible as Chisel `assert`s, which are immediate assertions;
- after synthesis there is no Chisel and no FIRRTL — gate-level simulation runs
  on a netlist, so the testbench must be SV or C++;
- if your organization's verification environment is SV/UVM, your block is a DUT
  inside it.

Levels [B3](../system_verilog/level-b3-printf-assert-pipeline/README.md) and
[D](../system_verilog/level-d-advanced/README.md) of the
[SystemVerilog appendix](../system_verilog/README.md) cover that territory.

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

emits `Assert.sv` and `TickGenTestTop.sv` into `generated/`.

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
