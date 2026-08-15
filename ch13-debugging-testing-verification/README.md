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

During the design and coding phase you spend much of your time **debugging**:
the process of finding defects — *bugs* — in your code. It usually runs in
parallel with writing new code, rather than as a separate phase afterwards.

One can debug a program with a debugger, or simply by printing interesting
values to the terminal — **printf debugging**. In hardware, elements execute in
parallel, so a common form of hardware debugging is generating waveforms and
watching how the signals of interest evolve over time: **waveform debugging**.

A Chisel tester can do both. It generates waveforms (attach
`WriteVcdAnnotation`, then open the `.vcd` in GTKWave — Chapter 3), and it can
print signal values during circuit simulation for quick checks: a `printf`
inside a module prints at the **rising edge of the clock** (Chapter 3).

---

## 13.2 Testing in Chisel

ChiselTest is built on ScalaTest, so `sbt test` runs everything. ScalaTest also
supports multithreaded testing out of the box: if your project has multiple test
**classes**, they run in parallel — that is multithreading at the class level,
separate from the fork/join threading *inside* a single test (§13.2.4).
Additionally, the `FlatSpec` syntax lets you write clear test descriptions,
which makes debugging easier.

A test is a class extending `AnyFlatSpec` with the `ChiselScalatestTester`
trait. Inside it, `peek`, `poke`, `expect`, and `step` operate on the DUT's IO
ports, using **Chisel types** (`UInt`/`SInt`/`Bool`). When peeking, though, we
usually want Scala types, since the test itself is written in Scala — so two
extra methods exist: `peekInt()` returns a Scala integer (a `BigInt`, so it can
express any width) and `peekBoolean()` returns a Scala `Boolean`. To advance the
simulation by one clock cycle, call `step()` on the DUT's implicit `clock` port.
Run everything with `sbt test`, or one suite with `sbt "testOnly Name"`.

The simplest possible test just wraps a few pokes/expects in `test(...)`, which
takes the module under test as its parameter. Here it checks the BCD lookup
table from Chapter 10:

`../ch10-hardware-generators/src/test/scala/BcdTableTest.scala`
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

Alternatively you can use the `behavior of "module name"` syntax and then refer
to the module with `it`. That form reads better once a module has several tests.
Both classes live in the same file, so `sbt test` in Chapter 10 runs them side
by side — the second is named `BcdTableTest2` only because two Scala classes in
one file cannot share a name:

`../ch10-hardware-generators/src/test/scala/BcdTableTest.scala`
```scala
class BcdTableTest2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BCD table"

  it should "output BCD encoded numbers" in {
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

> The book writes the second variant as `extends FlatSpec`. Plain `FlatSpec` was
> deprecated when ScalaTest split its styles into `org.scalatest.flatspec`;
> use **`AnyFlatSpec`** in both, as above.

**A worked example: the counter device.** Simple tests start by poking test
vectors into the DUT, advancing the clock, and checking outputs with `expect`.
For debugging you can also `peek` values and print them for manual inspection.
Listing 13.1 tests the counter device introduced in Chapter 12 as an example IO
device — every pin of the pipelined protocol poked and expected by hand:

`../ch12-interconnect/src/test/scala/CounterDeviceTest.scala`
```scala
"CounterDevice" should "work" in {
  test(new CounterDevice()) { dut =>
    dut.io.ack.expect(false.B)
    dut.clock.step()
    dut.io.address.poke(0.U)
    dut.io.rd.poke(true.B)
    dut.io.ack.expect(false.B)
    dut.clock.step()
    dut.io.rd.poke(false.B)
    dut.io.ack.expect(true.B)
    dut.clock.step(100)
    dut.io.rd.poke(true.B)
    dut.io.address.poke(4.U)
    dut.clock.step()
    assert(dut.io.rdData.peekInt() > 100)
    dut.io.wr.poke(true.B)
    dut.io.wrData.poke(0.U)
    dut.clock.step()
    dut.io.wr.poke(false.B)
    dut.io.rd.poke(true.B)
    dut.clock.step()
    dut.io.rdData.expect(1.U)
    dut.io.address.poke(0.U)
    dut.clock.step()
    assert(dut.io.rdData.peekInt() > 100)
  }
}
```
***Listing 13.1** — Testing the counter device.*

### 13.2.1 Use functions

As you can see, that test covers only a few cases yet is already very long to
read; all those pokes and expects are cumbersome. As a first step we introduce
**functions** for a read and a write request. Those functions abstract away the
manual "bit banging" at the interface pins. As a shortcut we also define `step`
to advance the clock. Listing 13.2 is the whole test rewritten with them — it
lives in the *same file* as Listing 13.1, so you can run and compare both:

`../ch12-interconnect/src/test/scala/CounterDeviceTest.scala`
```scala
"CounterDevice" should "read, advance, and load counters" in {
  test(new CounterDevice()) { dut =>
    def step(n: Int = 1) = dut.clock.step(n)

    def read(addr: Int) = {
      dut.io.address.poke(addr.U)
      dut.io.rd.poke(true.B)
      step()
      dut.io.rd.poke(false.B)
      while (!dut.io.ack.peekBoolean()) step()   // wait for the delayed ack
      dut.io.rdData.peekInt()
    }
    def write(addr: Int, data: Int) = {
      dut.io.address.poke(addr.U)
      dut.io.wrData.poke(data.U)
      dut.io.wr.poke(true.B)
      step()
      dut.io.wr.poke(false.B)
      while (!dut.io.ack.peekBoolean()) step()
    }

    for (i <- 0 until 4) assert(read(i * 4) < 10, s"counter $i just started")
    step(100)
    for (i <- 0 until 4) assert(read(i * 4) > 100, s"counter $i advanced")
    write(2 * 4, 0)
    write(3 * 4, 1000)
    assert(read(2 * 4) < 5, "counter reset")
    assert(read(3 * 4) > 1000, "counter loaded")
  }
}
```
***Listing 13.2** — Testing the counter device with functions.*

`read` takes an address and returns the read value: it pokes the address and
asserts `rd`, advances the clock by one cycle, deasserts `rd`, then waits. In
this device the value is ready after one clock cycle, but we generalize to
devices with longer latencies by polling `io.ack` in a loop with `peekBoolean()`
(a Scala `Boolean`) until the device acknowledges. It finally reads `io.rdData`
with `peekInt()`, which returns a Scala `BigInt` so it can express integers of
any size. **Caveat:** if a device never asserts `ack`, this polling loop hangs
forever; a robust `read` should add a timeout around the `ack` polling.

`write` takes an address and the data as Scala `Int`s and is symmetric: poke the
values, advance one cycle, deassert `wr`, then wait in the same loop for `ack`.

With those three functions available we can write more readable tests in fewer
lines — and this version already covers **more** cases than the original
bit-banging tester. That is not a hypothetical benefit — the book's author notes
in a footnote that writing this second, more comprehensive test is what caught a
real off-by-one error (`until 3` instead of `until 4`) in the counter device,
which the hand-written test of Listing 13.1 had missed. The version shipped here
has the loop bound right, so both tests pass. You can reproduce the anecdote:
change `for (i <- 0 until 4)` to `until 3` in
`../ch12-interconnect/src/main/scala/interconnect.scala`, so counter 3 stops
counting, and re-run — Listing 13.1 never looks at counter 3 and still passes,
while Listing 13.2 fails:

```
[info] CounterDevice
[info] - should work
[info] CounterDevice
[info] - should read, advance, and load counters *** FAILED ***
[info]   0 was not greater than 100 counter 3 advanced (CounterDeviceTest.scala:61)
```

### 13.2.2 Selecting tests with tags

With a large test suite you may want to run only a subset — for example as part
of a continuous-integration run. The easiest way to do that while still running
a single sbt command is to **tag** your tests:

`src/test/scala/TagTest.scala`
```scala
object Unnecessary extends Tag("Unnecessary")

class TagTest extends AnyFlatSpec with Matchers {
  "Integers" should "add" taggedAs (Unnecessary) in {
    17 + 25 should be(42)
  }
}
```

By default all tests are run, with `sbt test` or `sbt "testOnly *"`. To leave out
the tests tagged `Unnecessary`, run:

```
$ sbt "testOnly * -- -l Unnecessary"
```

The excluded test then shows up as not run in the terminal. Narrowing to just
this suite makes that easy to see:

```
$ sbt "testOnly TagTest -- -l Unnecessary"
[info] TagTest:
[info] Integers
[info] Run completed in 193 milliseconds.
[info] Total number of tests run: 0
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 0, failed 0, canceled 0, ignored 0, pending 0
[info] No tests were executed.
```

Note what `-l Unnecessary` does and does not do: the suite is still *loaded* and
its subject line (`Integers`) still printed — only the tagged test itself is
skipped. Across the whole chapter the other two suites keep running, so
`testOnly * -- -l Unnecessary` reports `Suites: completed 3` and
`Tests: succeeded 2` here, one fewer than a plain `sbt test`.

If your tests (and tags) live inside a package, remember to give the **full
reference path** to both the test and the tag — a bare class/tag name won't
resolve.

### 13.2.3 Accessing internal signals with `BoringUtils`

When testing a circuit, the test code usually has access only to the **ports**
of the DUT. That abstraction is generally good practice — reaching into internal
signals and state is considered bad practice in hardware and software testing
alike.

Sometimes it is still worth doing. Testing a microprocessor with small assembler
programs is the classic case: you compare the hardware implementation against a
software simulator of the same processor, and for a RISC-style processor
comparing the **register file** of the two is enough, because every value that
is computed, loaded, or stored passes through it at some point. Another use case
is exploring and testing a state machine (with or without a datapath) with direct
access to its internal state.

To show internal-signal access in action we use a minimal example: a tick
generator with an internal counter. Only the necessary signal `tick` is
connected to an output port; the counter is not exposed, which is good design
practice:

`src/main/scala/Boring.scala`
```scala
class TickGen extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
  })

  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  io.tick := cntReg === 9.U
  when(io.tick) {
    cntReg := 0.U
  }
}
```
***Listing 13.3** — The tick generator as DUT.*

Say we want the internal counter in our test code. We could add a port to expose
it. We could even use a Scala `Boolean` flag to add that port conditionally while
debugging and drop it when generating hardware. But mixing debugging code into
the hardware description is not good practice.

Instead, use **`BoringUtils`**. It lets us *bore* a connection through a module
hierarchy; behind the scenes it adds the extra ports throughout the hierarchy —
exactly what we would do by hand, without cluttering the original code. At the
time of writing, `BoringUtils` is still considered **experimental**, so it must
be imported from:

```scala
import chisel3.util.experimental.BoringUtils
```
*illustrative — the import needed for `BoringUtils`*

To carry the additional port, we wrap the DUT in another top-level module used
only for testing:

`src/main/scala/Boring.scala`
```scala
class TickGenTestTop extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
    val counter = Output(UInt(8.W))
  })

  val tickGen = Module(new TickGen)
  io.tick := tickGen.io.tick
  io.counter := DontCare
  BoringUtils.bore(tickGen.cntReg, Seq(io.counter))
}
```
***Listing 13.4** — A top-level wrapper for our DUT.*

Inside `TickGenTestTop` we instantiate the original DUT and connect the `tick`
port. For the `counter` output we must first assign *something* to keep the
Chisel compiler happy — since the inner module drives it later, we connect it to
`DontCare`. The next line connects `io.counter` to the count register inside
`tickGen`. We wrap `io.counter` in a Scala `Seq`, because `bore` supports
connecting to several signals at once.

Finally, the test itself. Note that it instantiates the **top-level wrapper**,
so the extra output port is there to observe:

`src/test/scala/BoringTest.scala`
```scala
test(new TickGenTestTop()) { dut =>
  dut.io.tick.expect(false.B)
  dut.io.counter.expect(0.U)

  dut.clock.step()
  dut.io.tick.expect(false.B)
  dut.io.counter.expect(1.U)

  dut.clock.step(8)
  dut.io.tick.expect(true.B)
  dut.io.counter.expect(9.U)

  dut.clock.step()
  dut.io.tick.expect(false.B)
  dut.io.counter.expect(0.U)
}
```
***Listing 13.5** — Testing the DUT with access to internal signals.*

### 13.2.4 Multithreaded testing (fork/join)

Digital hardware is inherently parallel, and it helps to represent that
parallelism in the testing code as well: one thread fills data into a circuit
while another checks the outputs coming out of it. We *could* do this in a single
thread, but then code for two different tasks has to be interleaved into one
function that shares the advancement of the clock. With multithreaded tests each
thread advances the clock independently; the threads are synchronized internally
at the call of `step()`.

ChiselTest supports this with **`fork`** and **`join`**. `fork` spawns a new
tester thread with a block of test code as its parameter; `join` may be called on
the tester-thread value a `fork` returns, to wait for that thread to join the
main thread.

Running multiple threads adds limitations on peeks and pokes: no two threads may
`peek` (respectively `poke`) the *same* signal at the same time. The threads are
synchronized on calls to `step` to guarantee correct operation.

Here is a small test of the Chapter 11 FIFO that enqueues an element in one
thread and dequeues it in the main thread:

`../ch11-example-designs/src/test/scala/BubbleFifoTest.scala`
```scala
it should "work with multiple threads" in {
  test(new BubbleFifo(8, 4)) { dut =>
    val enq = fork {
      while (dut.io.enq.full.peekBoolean()) dut.clock.step()
      dut.io.enq.din.poke(42.U)
      dut.io.enq.write.poke(true.B)
      dut.clock.step()
      dut.io.enq.write.poke(false.B)
    }
    while (dut.io.deq.empty.peekBoolean()) dut.clock.step()
    dut.io.deq.dout.expect(42.U)
    dut.io.deq.read.poke(true.B)
    dut.clock.step()
    dut.io.deq.empty.expect(true.B)
    enq.join()
  }
}
```

The forked thread blocks (stepping the clock) until the FIFO has space, writes
`42`, and finishes. Meanwhile the main thread blocks until the FIFO is
non-empty, checks the word came through, reads it, and confirms the FIFO is
empty again — then `enq.join()` waits for the producer before the test ends.

More threads are spawned with **stacked** calls to `fork`
(`fork { ... }.fork { ... }`). The spawned threads form a hierarchy in which the
first thread should not finish before any of the subsequent ones.

Run it on its own with:

```
$ cd ../ch11-example-designs && sbt "testOnly BubbleFifoTest"
```

```
[info] BubbleFifoTest:
[info] Bubble FIFO
[info] - should bubble a word through and flow-control
[info] - should work with multiple threads
[info] Run completed in 1 second, 182 milliseconds.
[info] Total number of tests run: 2
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 2, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Note this fork/join threading *inside* one test is a different thing from
ScalaTest running whole test **classes** in parallel, mentioned at the top of
§13.2.

### 13.2.5 Simulator backends

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

### 13.2.6 Which artifact are your tests actually running?

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

### 13.2.7 How this scales up: verifying a real design

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
  expressible as a Chisel `assert`, which is an *immediate* assertion — though
  they **are** expressible with `chisel3.ltl`, see below;
- after synthesis there is no Chisel and no FIRRTL — gate-level simulation runs
  on a netlist, so the testbench must be SV or C++;
- if your organization's verification environment is SV/UVM, your block is a DUT
  inside it.

**What this chapter's `assert` actually emits** — the `$error`/`$fatal` form, the
concurrent-SVA form that formal tools consume, and why `Assert`'s assertion is
deleted while `AssertOverflow`'s survives — is worked through with real captured
output in [`SYSTEMVERILOG-NOTES.md` §N](../SYSTEMVERILOG-NOTES.md#n-simulation-only-constructs).

---

## 13.3 Assertions

An assertion statement in a programming language lets you state assumptions
about a program; if the condition evaluates to false, the program usually stops
with an exception. Chisel supports assertions to state assumptions about the
**hardware**. They are checked when simulating the design — the simulation stops
with an error message if the condition is false — and are **ignored when
generating hardware**, because there is no easy way for hardware to communicate
a failing assertion.

Listing 13.6 is the full example module. These are trivial assertions, meant
only to show the mechanism:

`src/main/scala/Assert.scala`
```scala
import chisel3._

class Assert extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  /* the following will not be true when
  the addition overflows
  assert(io.sum >= io.a)
  assert(io.sum >= io.b)
   */
  assert(io.sum === io.a + io.b)
}
```
***Listing 13.6** — Using assertions in Chisel.*

If we made an error — using subtraction instead of addition, or reassigning
`sum` to a different value later — we would catch it during testing.

As the comment says, the first two assertions are **not always true**, so we
cannot use them. That was discovered with formal verification (§13.4): an 8-bit
add can overflow, and then the sum is *smaller* than an input. This is exactly
the kind of corner case a hand-written test usually misses.

This project also carries a second module, `AssertOverflow`, which is the same
adder with the overflow-prone assertion left **enabled**:

`src/main/scala/Assert.scala`
```scala
class AssertOverflow extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  assert(io.sum >= io.a, "8-bit add must not overflow")
}
```

It exists for the SystemVerilog side of the story: `Assert`'s surviving
assertion is a tautology and is optimized away entirely, so nothing shows up in
the generated code — while `AssertOverflow`'s is not provably true, so it
survives and you can see what an `assert` actually emits (see the end of §13.2.7).

`src/test/scala/AssertTest.scala` runs the first module, including
`a = 100, b = 200` (300 wraps to 44 in 8 bits) — the kept assertion still holds,
since it just restates the assignment.

A failing assertion stops the simulation with a message like:

```
Assertion failed
    at Assert.scala:20 assert(sum <= a + b)
```

**Style tip:** place all assertions at the end of a module, so they don't
clutter the reading of the module's intended design.

---

## 13.4 Formal verification

Assertions are executed during simulation, so we still have to write the test
cases that trigger them — and writing tests that trigger *all* possibilities is
hard, in general impossible. To catch errors in overlooked corner cases we can
use **formal verification**, which checks a property for *all* inputs (up to a
bound) using an SMT solver. Kevin Laeufer added formal verification to
ChiselTest, and the **very same assertions** are reused for it.

To explore it, install the open [Z3](https://github.com/Z3Prover/z3) theorem
prover. Then substitute `test(..)` by `verify(..)`; `assume(...)` constrains
inputs, and `past(x)` refers to a previous cycle's value. Listing 13.7 runs
formal verification on our simple adder circuit with the (naive) assertions:

*illustrative — requires the Z3 solver installed*
```scala
import chiseltest.formal._

class FormalTest extends AnyFlatSpec with ChiselScalatestTester with Formal {
  "AssertTest" should "pass" in {
    verify(new Assert(), Seq(BoundedCheck(5), WriteVcdAnnotation))
  }
}
```
***Listing 13.7** — Formally verifying the circuit.*

It surprised the book's author that Chisel formal **immediately found an error**
in the circuit — or rather in the assertions. To investigate, look into the
waveform for the input data that leads to the violation: it uses `0xdb` and
`0x65`, which give a sum of `0x40`. Those inputs overflow the 8-bit addition,
and the simple test case never tried overflowing values. So the verification
showed that the assertions claiming the sum is larger than or equal to the
inputs are wrong. Adding `WriteVcdAnnotation` to the annotation list (as above)
is what dumps that counterexample trace to a `.vcd`, which you open in GTKWave
the same way as a regular simulation waveform.

> **Not runnable here:** formal verification needs the
> [Z3](https://github.com/Z3Prover/z3) theorem prover, which isn't installed in
> this environment, so this project ships no formal test. Install Z3 and add a
> `FormalTest` to try `verify` yourself.

---

## 13.5 Build, run, and check

```
$ sbt test
```

Expected tail (3 tests across 3 suites — `AssertTest`, `BoringTest`, `TagTest`):

```
[info] Run completed in 1 second, 29 milliseconds.
[info] Total number of tests run: 3
[info] Suites: completed 3, aborted 0
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

The listings this chapter borrows from other chapters run in *their* projects:

```
$ cd ../ch10-hardware-generators && sbt "testOnly BcdTableTest BcdTableTest2"
$ cd ../ch12-interconnect      && sbt "testOnly CounterDeviceTest"
$ cd ../ch11-example-designs   && sbt "testOnly BubbleFifoTest"
```

Generate SystemVerilog (a provable `assert` is dropped in generation):

```
$ sbt "runMain Generate"
```

emits `Assert.sv`, `AssertOverflow.sv`, and `TickGenTestTop.sv` into
`generated/`. `src/main/scala/Generate.scala` also carries a second entry point,
`GenerateSva`, which prints `AssertOverflow` re-emitted with
`--emit-chisel-asserts-as-sva` — the concurrent-SVA form formal tools consume
rather than the `$error`/`$fatal` pair:

```
$ sbt "runMain GenerateSva"
```

---

## 13.6 Recap

- Debug with **waveforms** (VCD + GTKWave) and **printf**.
- Write tests as `AnyFlatSpec` + `ChiselScalatestTester`; `peekInt()` /
  `peekBoolean()` bring values back into Scala.
- Make test benches readable with **helper functions** — Listings 13.1 and 13.2
  are the same device tested by hand and through `read`/`write`, and only the
  second one caught the bug; **tag** tests to select subsets (`-l Tag`).
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
