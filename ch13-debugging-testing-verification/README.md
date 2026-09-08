# Chapter 13 — Debugging, Testing, and Verification

Chapter 3 introduced ChiselTest; this chapter digs deeper into how to **debug**,
**test**, and **verify** hardware. It covers waveform/printf debugging, making
tests readable with helper functions, selecting tests with **tags** and the
other test filters, reaching
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

### 13.2.2 Selecting tests with tags — and the other filters

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

**Tags are one filter of several.** A tag is the right tool when the *set* of
tests you want to skip is a property of the tests themselves (slow, flaky,
unnecessary in CI). For the everyday "just run this one thing" case there are
four more axes, and the `--` in the command line is the seam between the two
tools that provide them:

```
sbt 'testOnly TagTest -- -l Unnecessary'
     ^^^^^^^^ ^^^^^^^    ^^^^^^^^^^^^^^
     sbt task  which      handed verbatim to the ScalaTest Runner,
               suites     which picks tests *inside* those suites
```

`testOnly` is sbt's, and it only ever selects whole **suites**. Everything after
`--` goes to ScalaTest — and to chiseltest, which is how the `-DwriteVcd=1` form
from [§3.2.3](../ch03-build-and-testing/README.md#323-waveforms) reaches it.

**Filtering by test name — `-z` and `-t`.** `-z <substring>` runs every test
whose **full name** contains the substring. For an `AnyFlatSpec` the full name is
the subject line plus the clause, so `AssertTest`'s single test is named
`Assert should hold (even across an overflowing add)`, and any fragment of that
selects it — across all three suites of this chapter at once:

```
$ sbt 'testOnly * -- -z "overflowing"'
[info] BoringTest:
[info] Boring
[info] TagTest:
[info] Integers
[info] AssertTest:
[info] Assert
[info] - should hold (even across an overflowing add)
[info] Run completed in 956 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 3, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Note the same effect as with `-l`: all three suites are loaded and print their
subject lines, and only the matching test runs.

`-z` may be repeated, and the matches are OR'd together:

```
$ sbt 'testOnly * -- -z "add" -z "Boring"'
[info] BoringTest:
[info] Boring
[info] - should expose the internal counter
[info] TagTest:
[info] Integers
[info] - should add
[info] AssertTest:
[info] Assert
[info] - should hold (even across an overflowing add)
[info] Total number of tests run: 3
```

`-t <name>` is the exact-match sibling: it runs the one test with precisely that
name, so you must give the whole thing, subject included.

```
$ sbt 'testOnly AssertTest -- -t "Assert should hold (even across an overflowing add)"'
[info] AssertTest:
[info] Assert
[info] - should hold (even across an overflowing add)
[info] Total number of tests run: 1
```

`-z` is the one you reach for in practice, and it is what
[Chapter 12](../ch12-interconnect/README.md#1231-the-combinational-handshake) uses
to run one handshake style at a time out of `CounterDeviceTest`, a single suite
that describes the combinational, registered, and pipelined slaves together —
`sbt 'testOnly CounterDeviceTest -- -z "combinational"'`.

**Filtering by package — `-m` and `-w`.** When suites live in a package
(ch11's `fifo` and `uart`, ch14's `leros`, ch15's `wildcat`), `-m <package>`
restricts the run to that package's own members, while `-w <package>` does the
same and also descends into subpackages. None of the tutorial's test packages
are nested, so here the two are interchangeable — `-w uart` in ch11 selects
`UartTest`, and `-m fifo` selects `FifoTest`:

```
$ cd ../ch11-example-designs && sbt 'testOnly * -- -m fifo'
[info] FifoTest:
[info] BubbleFifo
[info] - should pass
[info] DoubleBufferFifo
[info] - should pass
[info] MemFifo
[info] - should pass
[info] RegFifo
[info] - should pass
[info] Total number of tests run: 4
[info] Suites: completed 1, aborted 0
```

Naming the suite on the sbt side does the same job — `sbt "testOnly fifo.FifoTest"`
— which is usually clearer for a single suite; `-m`/`-w` earn their keep when a
package holds many.

**The sbt side: choosing suites.** These need no `--` at all.

| Command | Runs |
|---------|------|
| `sbt test` | every suite in the project |
| `sbt "testOnly AssertTest"` | one suite |
| `sbt "testOnly TagTest AssertTest"` | several suites, space-separated |
| `sbt "testOnly *Assert*"` | glob over the **fully-qualified** suite name |
| `sbt "testOnly fifo.FifoTest"` | a suite inside a package (full path, as above) |
| `sbt testQuick` | only suites that failed, never ran, or whose dependencies recompiled |
| `sbt "Test/testOnly …"` | the same, with the config scope spelled out — `testOnly` already defaults to `Test` |

The glob matches the qualified name, which is why `*Assert*` works and why a
packaged suite needs either its package or a leading `*`:

```
$ sbt 'testOnly *Assert*'
[info] AssertTest:
[info] Assert
[info] - should hold (even across an overflowing add)
[info] Total number of tests run: 1
```

`testQuick` is the incremental form — after a green run it has nothing left to do:

```
$ sbt testQuick
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] No tests to run for Test / testQuick
```

**One more that is not a filter but pairs with them:** `-oD` appends each test's
duration, which is how you find the slow ones worth tagging.

```
$ sbt 'testOnly AssertTest -- -oD -z "overflowing"'
[info] AssertTest:
[info] Assert
[info] - should hold (even across an overflowing add) (557 milliseconds)
```

**Three things to watch out for.**

*A filter that matches nothing is a silent success.* Neither sbt nor ScalaTest
treats "you selected zero tests" as an error, so a typo looks like a pass. Both
of these exit `[success]`:

```
$ sbt 'testOnly AssertTest -- -t "no such test"'
[info] AssertTest:
[info] Assert
[info] Run completed in 51 milliseconds.
[info] Total number of tests run: 0
[info] No tests were executed.
[success] Total time: 0 s
```

```
$ sbt "testOnly NoSuchTest"
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] No tests to run for Test / testOnly
[success] Total time: 0 s
```

Always read the `Total number of tests run: N` line rather than trusting the
green `[success]`.

*Quoting.* The whole sbt command has to arrive as a single shell argument, so
when the filter itself contains quotes, put single quotes on the outside:
`sbt 'testOnly * -- -z "wait states"'`. That is why this chapter writes
`sbt "testOnly * -- -l Unnecessary"` (no inner quotes needed — a tag name has no
spaces) but Chapter 12 writes `sbt 'testOnly … -- -z "combinational"'`.

*There is no negative name filter.* ScalaTest has no "`-z` but inverted" — you
cannot say *run everything except this test* by name. Exclusion is exactly what
tags are for, which is the reason `Unnecessary` exists above. To make an
exclusion permanent rather than typing it each time, it belongs in `build.sbt` as
`Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "Unnecessary")`
(no chapter in this tutorial does that, so it is not exercised here).

Finally, one ScalaTest option that looks applicable and is not: `-q`, which
filters by suite-name *suffix*, is rejected outright under sbt.

```
[error] java.lang.IllegalArgumentException: Discovery suffixes (-q) is not supported
        when running ScalaTest from sbt; Please use sbt's test-only or test filter instead.
```

Use a `testOnly` glob (`*Test`) instead.

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

`src/main/scala/Boring.scala`
```scala
import chisel3.util.experimental.BoringUtils
```

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

simulate(new TickGen()) { dut =>
  for (_ <- 0 until 9) { dut.io.tick.expect(false.B); dut.clock.step(1) }
  dut.io.tick.expect(true.B)
}
```
*illustrative — the svsim path, which always compiles the SystemVerilog*

> **Toolchain check.** Both Verilog-level paths — `VerilatorBackendAnnotation`
> and `EphemeralSimulator` — **do** run against the pinned Chisel 6.5.0 /
> chiseltest 6.0.0 with the Verilator on this machine:
>
> ```
> $ verilator --version
> Verilator 5.022 2024-02-24 rev conda-forge build 1
> ```
>
> To reproduce: wrap the `simulate` block above in an `AnyFlatSpec` under
> `src/test/scala/` — **on its own**, because importing `chiseltest._` and
> `chisel3.simulator.EphemeralSimulator._` into the *same* file leaves their
> `poke`/`step` implicits unresolved (`value step is not a member of
> chisel3.Clock`). It passes, driving a real Verilator binary.
>
> The two paths differ in what they leave behind, which is worth seeing. Add
> `.withAnnotations(Seq(VerilatorBackendAnnotation))` to a `test(...)` and
> `test_run_dir/` holds the whole Verilog toolchain — the emitted `.sv`, the C++
> harness, Verilator's generated sources and the compiled binary:
>
> ```
> test_run_dir/<test name>/TickGen.sv
> test_run_dir/<test name>/TickGen.lo.fir
> test_run_dir/<test name>/TickGen-harness.cpp
> test_run_dir/<test name>/verilated/VTickGen__ALL.cpp
> test_run_dir/<test name>/verilated/VTickGen          <- the simulator binary
> ```
>
> `EphemeralSimulator`, by contrast, leaves **nothing** — that is what
> *ephemeral* means: it builds in a temporary directory and discards it, so
> there is no `.sv` to inspect afterwards. Use the chiseltest backend when you
> want the artifacts, or `chisel3.simulator` directly (rather than the
> `EphemeralSimulator` convenience object) to choose a workspace that persists.
>
> Verilator much newer than the pinned Chisel does break, in two ways worth
> recognizing: chiseltest's C++ harness stops compiling
> (`error: unknown type name 'WData'` — Verilator changed that API), and svsim
> fails its startup handshake (`java.lang.Exception: Unexpected message:
> Ready`). Neither is a problem with your design; both are version skew. If you
> meet either, check your Verilator version first and pair the pinned Chisel
> with one from its own era — the 5.022 above dates from February 2024, months
> before Chisel 6.5.0. The chapters here all pass on Treadle regardless, which
> is why the tutorial does not *require* Verilator at all.

> **Where this API is going.** ChiselTest is **archived**: the repository went
> read-only on 2024-08-19 ("we no longer have a maintainer"), and **6.0.0 — the
> version pinned here — is its last release**. There is no 7.x. Its successor is
> **ChiselSim**, the `chisel3.simulator.scalatest.ChiselSim` trait, which wraps
> the svsim path above in ScalaTest and is where the current Chisel
> documentation points. Two things to know before reaching for it:
>
> - It is a **Chisel 7** API — `chisel3.simulator.scalatest` does not exist in
>   the 6.5.0 pinned here, which has only the lower-level `chisel3.simulator`
>   shown above. Chisel 7.13.0+ also ships a chiseltest-*named* compatibility
>   shim, but it is ChiselSim underneath and does not preserve everything: its
>   `fork` runs sequentially rather than concurrently (so §13.2.4's lesson would
>   quietly stop holding), and its `expect` drops the message argument.
> - **ChiselSim has no FIRRTL interpreter.** Its only two backends are Verilator
>   and VCS, both external simulators, so `sbt test` would need a native
>   toolchain — where Treadle needs nothing but a JVM. That, plus the book being
>   written against Chisel 6, is why this tutorial stays on the pinned versions.
>
> §13.2.8 puts this chapter's own tests side by side in both styles, in a
> runnable Chisel 7 sub-project, so you can see what a migration actually costs.

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

### 13.2.8 Running the same tests on ChiselSim

The note above says ChiselTest is archived and **ChiselSim** is its successor.
This section is that successor applied to this chapter's own three tests, so you
can read the two styles side by side and see exactly what a migration costs.

ChiselSim is a **Chisel 7** API, and this chapter — like the whole tutorial — is
pinned to Chisel 6.5.0. Rather than unpin it, the ChiselSim tests live in a
**nested project** with its own versions:

`chiselsim/build.sbt`
```scala
// Chapter 13, ChiselSim addendum (§13.2.8).
// A SEPARATE project on purpose: ChiselSim is a Chisel 7 API, and the chapter
// itself is pinned to Chisel 6.5.0 / chiseltest 6.0.0 like the rest of the
// tutorial. Keeping it here lets both sets of tests be real and runnable
// without unpinning the chapter.
scalaVersion := "2.13.18" // Chisel 7 pulls scala-library 2.13.18 (SIP-51)

val chiselVersion = "7.15.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-language:reflectiveCalls",
)

addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full)
libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % "test"
```

Two version facts are forced, not chosen. Chisel 7 is what brings
`chisel3.simulator.scalatest`, and Chisel 7.15.0 pulls in scala-library 2.13.18,
so sbt's SIP-51 check rejects the tutorial's usual 2.13.14 with
`Expected scalaVersion to be 2.13.18 or later`. There is no `chiseltest`
dependency at all.

Run it from that folder, not the chapter root:

```
$ cd chiselsim
$ sbt test
```

#### What actually changes in a test

Almost nothing, and it is worth measuring rather than asserting. §13.2.3's
`BoringTest` and its ChiselSim twin differ by **three** lines — the import, the
trait, and the call that starts the simulation:

```
$ diff -u src/test/scala/BoringTest.scala \
          chiselsim/src/test/scala/BoringSimTest.scala
```

```diff
--- ch13/src/test/scala/BoringTest.scala
+++ ch13/chiselsim/src/test/scala/BoringSimTest.scala
@@ -1,11 +1,14 @@
 import chisel3._
-import chiseltest._
+import chisel3.simulator.scalatest.ChiselSim
 import org.scalatest.flatspec.AnyFlatSpec
 
-// Test the tick generator WITH access to its bored-out internal counter.
-class BoringTest extends AnyFlatSpec with ChiselScalatestTester {
+// The chapter's BoringTest, rewritten for ChiselSim. Three lines change: the
+// import, the trait mixed in, and `test(...)` becoming `simulate(...)`. The
+// class rename is only to keep the two readable side by side. Every
+// poke/step/expect inside the body is identical.
+class BoringSimTest extends AnyFlatSpec with ChiselSim {
   "Boring" should "expose the internal counter" in {
-    test(new TickGenTestTop()) { dut =>
+    simulate(new TickGenTestTop()) { dut =>
       dut.io.tick.expect(false.B)
       dut.io.counter.expect(0.U)
 
```

The diff stops there because the rest of the file is byte-identical: every
`poke`, `step`, and `expect` carries over untouched, and so does the
`AnyFlatSpec` "should" structure. The class rename is cosmetic — the two live in
separate projects, so they could share a name. Read the body in §13.2.3's
Listing 13.5; it is not reproduced here.

The DUT needed one change, and it is not a ChiselSim change. `BoringUtils.bore`
has a newer form in which the bored value is **returned** rather than written
into a sink you pass in, so the wrapper reads like an ordinary connection:

`chiselsim/src/main/scala/Boring.scala`
```scala
// chapter's Chisel 6 version calls the older `bore(source, Seq(sink))` form,
// which still exists in Chisel 7 but is deprecated.
class TickGenTestTop extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
    val counter = Output(UInt(8.W))
  })

  val tickGen = Module(new TickGen)
  io.tick := tickGen.io.tick
  io.counter := BoringUtils.bore(tickGen.cntReg)
}
```

The chapter's Chisel 6 `bore(source, Seq(sink))` form still exists in Chisel 7
— it is deprecated, not removed, despite a Chisel 6 deprecation warning that
says it would go in 7.0.

```
$ sbt "testOnly BoringSimTest"
```

```
[info] BoringSimTest:
[info] Boring
[info] - should expose the internal counter
[info] Run completed in 2 seconds, 735 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

#### Assertions, and proving one fires

`AssertSimTest`'s first test is §13.3's `AssertTest` with the same three lines
swapped, so it is not repeated here. What is new is the rest of the file.
ChiselSim reports a failed Chisel `assert` as a specific exception —
`chisel3.simulator.Exceptions.AssertionFailed`, message *"One or more assertions
failed during Chiselsim simulation"* — which means you can **catch** it, and
finally test the thing the chiseltest version never checks: that
`AssertOverflow`'s assertion really does fire when the 8-bit add wraps.

`chiselsim/src/test/scala/AssertSimTest.scala`
```scala
  // AssertOverflow asserts `io.sum >= io.a`, which is false whenever the 8-bit
  // add wraps. A passing test here means the assertion fired.
  "AssertOverflow" should "stop the simulation when its assertion fails" in {
    intercept[Exceptions.AssertionFailed] {
      simulate(new AssertOverflow()) { dut =>
        dut.io.a.poke(100.U)
        dut.io.b.poke(200.U) // 44 >= 100 is false
        dut.clock.step()
      }
    }
  }

  it should "be satisfied when the add does not overflow" in {
    simulate(new AssertOverflow()) { dut =>
      dut.io.a.poke(100.U)
      dut.io.b.poke(20.U) // 120 >= 100 holds
      dut.clock.step()
    }
  }
```

The first of those passes *because* the simulation died; the second shows the
same module staying quiet when the add does not overflow. Together they pin the
assertion from both sides — a strictly stronger claim than §13.3's, which only
ever exercises the passing case.

```
$ sbt "testOnly AssertSimTest"
```

```
[info] AssertSimTest:
[info] Assert
[info] - should hold (even across an overflowing add)
[info] AssertOverflow
[info] - should stop the simulation when its assertion fails
[info] - should be satisfied when the add does not overflow
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

#### Tags are unaffected

§13.2.2's filters are a **ScalaTest** feature, so they know nothing about which
simulator runs underneath and keep working unchanged. That matters more here
than it did on Treadle: every ChiselSim test pays for a Verilator build, so a
tag that separates the simulating tests from the pure-Scala ones is worth having.

`chiselsim/src/test/scala/TagSimTest.scala`
```scala
import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec

// Tags are a ScalaTest feature, not a simulator feature, so §13.2.2's filters
// work on ChiselSim tests unchanged. `Slow` marks the tests that pay for a
// Verilator build; exclude them with
//   sbt "testOnly * -- -l Slow"
object Slow extends Tag("Slow")

class TagSimTest extends AnyFlatSpec with ChiselSim {
  "A tagged ChiselSim test" should "still simulate" taggedAs (Slow) in {
    simulate(new TickGen()) { dut =>
      dut.clock.step(9)
      dut.io.tick.expect(true.B)
    }
  }

  it should "run alongside plain ScalaTest assertions" in {
    assert(17 + 25 == 42) // no simulator involved, so no Verilator build
  }
}
```

```
$ sbt 'testOnly * -- -l Slow'
```

```
[info] TagSimTest:
[info] A tagged ChiselSim test
[info] - should run alongside plain ScalaTest assertions
```

The `should still simulate` line is gone — `-l` excluded it — while the untagged
assertion in the same suite still ran.

#### Waveforms without an annotation

§13.1 dumps a VCD with `WriteVcdAnnotation`, which is baked into the test code.
ChiselSim instead exposes tracing as a **command-line option**: mix in
`Cli.EmitVcd` and the suite gains an `emitVcd` option, so one test serves both
the traced and untraced runs.

`chiselsim/src/test/scala/WaveSimTest.scala`
```scala
import chisel3._
import chisel3.simulator.scalatest.{ChiselSim, Cli}
import org.scalatest.flatspec.AnyFlatSpec

// §13.1's waveform debugging, on the ChiselSim side. Mixing in Cli.EmitVcd
// adds an `emitVcd` command-line option to this suite instead of hard-coding
// a WriteVcdAnnotation, so the same test runs with or without tracing:
//   sbt "testOnly WaveSimTest -- -DemitVcd=1"
class WaveSimTest extends AnyFlatSpec with ChiselSim with Cli.EmitVcd {
  "TickGen" should "tick on the tenth cycle" in {
    simulate(new TickGen()) { dut =>
      dut.clock.step(9)
      // Real ChiselSim keeps expect's message argument (the Chisel 7
      // chiseltest-compatibility shim does not).
      dut.io.tick.expect(true.B, "tick must be high on the tenth cycle")
    }
  }
}
```

Without the option, no trace is written at all. With it:

```
$ sbt "testOnly WaveSimTest -- -DemitVcd=1"
$ find build -name "*.vcd"
build/chiselsim/WaveSimTest/TickGen/should-tick-on-the-tenth-cycle/workdir-verilator/trace.vcd
```

That path is worth a second look. The ScalaTest ChiselSim trait keeps a
**persistent** workspace under `build/chiselsim/<suite>/<module>/<test>/`, unlike
the `EphemeralSimulator` of §13.2.6 which discards its temporary directory. And
the trace reaches inside the module without any boring at all:

```
$version Generated by VerilatedVcd $end
$timescale 100ps $end
 $scope module TOP $end
  $scope module svsimTestbench $end
   $var wire 1 # clock [0:0] $end
   $var wire 1 $ reset [0:0] $end
   $var wire 1 % io_tick [0:0] $end
   $var wire 32 & simulationState [31:0] $end
   $var wire 32 ' traceSupported [31:0] $end
   $scope module dut $end
    $var wire 1 # clock $end
    $var wire 1 $ reset $end
    $var wire 1 % io_tick $end
    $var wire 8 ( cntReg [7:0] $end
    $var wire 1 % io_tick_0 $end
   $upscope $end
  $upscope $end
 $upscope $end
$enddefinitions $end
```

`cntReg` is right there in the waveform. Boring (§13.2.3) is for reading an
internal signal from *test code*; a waveform never needed it.

#### What does not port

- **`fork`/`join` (§13.2.4) has no ChiselSim equivalent.** Chisel 7.13.0+ ships
  a chiseltest-*named* shim in which `fork` compiles but runs the block
  immediately to completion, so a concurrent producer/consumer test silently
  becomes sequential. If you migrate a test like Chapter 11's `BubbleFifoTest`,
  restructure it rather than trusting the shim.
- **Treadle is gone.** ChiselSim's only backends are Verilator and VCS, so `sbt
  test` now needs a native simulator. This chapter's four ChiselSim suites take
  about 8 seconds against the chapter's own 3 seconds on Treadle, and they
  cannot run at all on a machine without Verilator.
- **`expect`'s message argument survives** in real ChiselSim (`WaveSimTest`
  above uses it); it is only the compatibility shim that drops it.

The chapter itself deliberately stays on chiseltest — see the note in §13.2.6
for why. This sub-project is the parallel track, kept runnable so that both
sides of the comparison are real.

```
$ sbt test
```

```
[info] Run completed in 8 seconds, 199 milliseconds.
[info] Total number of tests run: 7
[info] Suites: completed 4, aborted 0
[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

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

### 13.4.1 What a bounded check actually does

`BoundedCheck(k)` runs **bounded model checking**. The tool unrolls the circuit
`k` times — `k` copies of the combinational logic, chained through the
registers — and hands the solver one question: *is there any input sequence,
starting from reset, that violates an assertion within `k` cycles?* The solver
answers in one of two ways, and both are useful:

| answer | meaning | what you get |
|---|---|---|
| satisfiable | such a sequence exists | a **counterexample**: concrete input values, cycle by cycle |
| unsatisfiable | no such sequence exists | a **proof** — for every input, but only up to `k` cycles |

That is the whole trade. You are not writing stimulus, so you cannot miss a
corner case *within the bound*; but the bound is real, and §13.4.6 shows a bug
that hides just beyond a shallow one.

Three constructs do the talking, and only the first is new to this section:

- **`assert(cond)`** — a claim about the design. The solver tries to break it.
- **`assume(cond)`** — a constraint on the *environment*. The solver may only
  choose inputs satisfying it, so an assumption narrows the search rather than
  being checked (§13.4.4).
- **`past(x)`** — the value `x` held on the previous cycle, which is what lets a
  property span cycles (§13.4.6).

Under the hood, `verify` takes the same FIRRTL the tests run on, converts it to
a transition system, encodes that as SMT-LIB, and shells out to the solver —
chiseltest's `Maltese` layer, which appears in the stack trace when a check
fails. Six engines are supported (`Z3EngineAnnotation`,
`BtormcEngineAnnotation`, `Yices2EngineAnnotation`, `CVC4EngineAnnotation`,
`BitwuzlaEngineAnnotation`, `BoolectorEngineAnnotation`); Z3 is the default.

### 13.4.2 Installing a solver and running the checks

Formal verification needs an SMT solver on the `PATH`, which is why the chapter's
plain `sbt test` does not run these checks: they are tagged, and `build.sbt`
excludes the tag from the `test` task only.

`src/test/scala/FormalTest.scala`
```scala
// Formal verification needs an SMT solver on the PATH, which not every reader
// will have, so these tests carry a tag that build.sbt excludes from the
// default `sbt test`. Run them with:
//   sbt "testOnly * -- -n NeedsSolver"
object NeedsSolver extends Tag("NeedsSolver")
```

`build.sbt`
```scala
// Formal verification (§13.4) needs an SMT solver on the PATH. Those tests are
// tagged NeedsSolver and excluded here so that a plain `sbt test` works with no
// native tools at all. Run them with:  sbt "testOnly * -- -n NeedsSolver"
Test / test / testOptions += Tests.Argument("-l", "NeedsSolver")
```

Scoping the option to `Test / test` rather than `Test` is the part that matters:
an unscoped `testOptions` also applies to `testOnly`, which would leave you
unable to run the very tests you asked for by name.

Any of the six solvers will do. The most self-contained route, verified here, is
the Python wheel, which ships a `z3` executable that needs nothing but `PATH`:

```
$ python3 -m venv ~/z3env && ~/z3env/bin/pip install z3-solver
$ export PATH="$HOME/z3env/bin:$PATH"
$ z3 --version
Z3 version 5.1.0 - 64 bit
```

Then run the checks. `testOnly` is not tag-filtered, so naming the suite is
enough:

```
$ sbt "testOnly FormalTest"
```

```
[info] FormalTest:
[info] Assert
[info] - should pass a bounded check
[info] AssertOverflow
[info] - should be refuted, because an 8-bit add can wrap
[info] Saturate
[info] - should be refuted, with a counterexample trace
[info] SaturateFixed
[info] - should pass, for all 2^32 inputs
[info] AssumeNoOverflow
[info] - should pass, because assume rules out the counterexample
[info] MonotonicCounter
[info] - should survive a shallow bounded check
[info] - should be refuted once the bound reaches the wrap
[info] Run completed in 16 seconds, 695 milliseconds.
[info] Total number of tests run: 7
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Note what "succeeded" means for the refutation tests. A check that *finds* a
violation throws `FailedBoundedCheckException`, so the tests that are supposed
to find one wrap `verify` in `intercept` — the same idiom §13.2.8 uses for
ChiselSim assertions. A green suite therefore means every provable property was
proved *and* every planted bug was caught.

### 13.4.3 The book's example: the assertion was wrong

Substitute `test(..)` by `verify(..)` and the chapter's adder goes through
formal. Listing 13.7 is that, on the naive assertions:

*illustrative — the book's formulation; this project's version is `FormalTest` above*
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
inputs are wrong. Adding `WriteVcdAnnotation` to the annotation list is what
dumps that counterexample trace to a `.vcd`, which you open in GTKWave the same
way as a regular simulation waveform.

In this project the two halves of that story are separate modules, so both can
be checked at once: `Assert` carries only the tautology and is proved, while
`AssertOverflow` carries the naive claim and is refuted.

`src/test/scala/FormalTest.scala`
```scala
  // The chapter's adder carries only a tautology, so it is provable.
  "Assert" should "pass a bounded check" taggedAs (NeedsSolver) in {
    verify(new Assert(), Seq(BoundedCheck(5)))
  }

  // AssertOverflow's assertion is the one the book's author found to be wrong.
  // A passing test here means the solver refuted it, as it should.
  "AssertOverflow" should "be refuted, because an 8-bit add can wrap" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new AssertOverflow(), Seq(BoundedCheck(5)))
    }
  }
```

Your counterexample will not necessarily use the book's `0xdb`/`0x65`: any
overflowing pair refutes the property, and which one the solver reports is its
choice.

### 13.4.4 `assume`: constraining the environment

A refuted assertion does not always mean the *hardware* is wrong. Here it means
the property was stated too strongly: `sum >= a` only holds if the caller never
overflows the adder. `assume` says exactly that, and the solver then restricts
itself to inputs that satisfy it:

`src/main/scala/Saturate.scala`
```scala
// §13.4: `assume` constrains the environment. AssertOverflow's assertion is
// false in general, but it becomes provable once the caller promises not to
// overflow - which is what an assumption states. `+&` is the widening add, so
// the sum in the assumption itself cannot wrap.
class AssumeNoOverflow extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  assume(io.a +& io.b <= 255.U)

  assert(io.sum >= io.a, "8-bit add must not overflow")
}
```

The same assertion that is refuted on `AssertOverflow` is proved here. That is
the point of an assumption: it moves an obligation from the design to whatever
drives it — and it is why assumptions are dangerous if you get them wrong. An
over-strong `assume` can make anything "provable" by ruling out the very inputs
that break it.

Assumptions are not simulation-only. `assume` survives generation as a real
SystemVerilog `assume`, next to the `$error`/`$fatal` pair an assertion becomes:

```systemverilog
      assume__assume: assume(~(_io_sum_T[8]));
```

### 13.4.5 A needle in 2^32: what formal buys over simulation

The adder's counterexamples are dense — roughly half of all input pairs overflow
— so a random test would find them too. The case that shows what formal is
*for* is a bug with one triggering input. `Saturate` is meant to hold at the
largest 32-bit value instead of wrapping, and its bound is off by one:

`src/main/scala/Saturate.scala`
```scala
// An incrementer meant to SATURATE at the largest 32-bit value instead of
// wrapping around to zero. The bound is off by one - it holds at 0xfffffffe
// instead of 0xffffffff - so exactly one input out of 2^32 makes the output
// wrap, and the assertion catches it. That is a bug simulation is very
// unlikely to stumble on and formal verification finds at once (§13.4).
class Saturate extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  io.out := Mux(io.in === "hffff_fffe".U, io.in, io.in + 1.U)

  assert(io.out >= io.in, "a saturating increment must never wrap")
}

// The same circuit with the bound corrected, which the same bounded check
// then proves for every input.
class SaturateFixed extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  io.out := Mux(io.in === "hffff_ffff".U, io.in, io.in + 1.U)

  assert(io.out >= io.in, "a saturating increment must never wrap")
}
```

Exactly one input in 2^32 — `0xffffffff` — makes the output wrap to zero. The
solver finds it at bound 1, and `WriteVcdAnnotation` writes the trace that names
it:

`src/test/scala/FormalTest.scala`
```scala
  // The needle-in-a-haystack case: one failing input out of 2^32.
  // WriteVcdAnnotation dumps the counterexample trace that names it.
  "Saturate" should "be refuted, with a counterexample trace" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new Saturate(), Seq(BoundedCheck(1), WriteVcdAnnotation))
    }
  }

  "SaturateFixed" should "pass, for all 2^32 inputs" taggedAs (NeedsSolver) in {
    verify(new SaturateFixed(), Seq(BoundedCheck(1)))
  }
```

```
$ sbt "testOnly FormalTest"
$ cat test_run_dir/Saturate_should_be_refuted_with_a_counterexample_trace/Saturate.bmc.vcd
```

```
$scope module Saturate $end
 $var wire 64 ! Step $end
 $var wire 1 " reset $end
 $var wire 32 # io_in $end
 ...
 $var wire 1 - assert $end
$upscope $end
$enddefinitions $end
#0
b0000000000000000000000000000000000000000000000000000000000000000 !
1"
b11111111111111111111111111111111 #
b00000000000000000000000000000000 (
0-
```

`io_in` is all ones, `io_out` (`(`) is all zeros, and `assert` (`-`) is `0` at
step 0. The solver did not search for that input; it *solved* for it.

Now the other side of the comparison, in ChiselSim. A hundred thousand random
inputs do not find the bug, and the value formal named fails on the first cycle:

`chiselsim/src/test/scala/SaturateSimTest.scala`
```scala
class SaturateSimTest extends AnyFlatSpec with ChiselSim {

  "A random search" should "not find the bug in 100000 tries" in {
    val rng = new scala.util.Random(42) // fixed seed, so this is deterministic
    simulate(new Saturate()) { dut =>
      for (_ <- 0 until 100000) {
        dut.io.in.poke((rng.nextLong() & 0xffffffffL).U)
        dut.clock.step()
      }
    }
    // Reaching this line is the result: 100000 random inputs, no violation.
    // At one failing value in 2^32 the chance of hitting it is about 0.0023%.
  }

  "The counterexample formal produced" should "fail on the first cycle" in {
    intercept[Exceptions.AssertionFailed] {
      simulate(new Saturate()) { dut =>
        dut.io.in.poke("hffffffff".U) // the input named in Saturate.bmc.vcd
        dut.clock.step()
      }
    }
  }

  "SaturateFixed" should "survive the same input" in {
    simulate(new SaturateFixed()) { dut =>
      dut.io.in.poke("hffffffff".U)
      dut.clock.step()
    }
  }
```

```
$ cd chiselsim && sbt "testOnly SaturateSimTest"
```

```
[info] SaturateSimTest:
[info] A random search
[info] - should not find the bug in 100000 tries
[info] The counterexample formal produced
[info] - should fail on the first cycle
[info] SaturateFixed
[info] - should survive the same input
[info] Run completed in 8 seconds, 567 milliseconds.
[info] Total number of tests run: 3
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

The DUT is *shared*, not copied — `chiselsim/build.sbt` adds
`../src/main/scala/Saturate.scala` to its sources — so the solver and the
simulator provably ran on the same circuit. The two tools are complementary in
exactly this shape: formal locates the input, simulation reproduces and debugs
it.

The off-by-one is visible in the generated SystemVerilog too, which is one
constant apart:

```
$ sbt "runMain Generate"
$ diff generated/Saturate.sv generated/SaturateFixed.sv
```

```diff
<   wire [31:0] io_out_0 = io_in == 32'hFFFFFFFE ? io_in : io_in + 32'h1;
>   wire [31:0] io_out_0 = (&io_in) ? io_in : io_in + 32'h1;
```

(firtool recognizes the *correct* bound as "every bit set" and emits the
reduction-AND `&io_in`, which is why the fixed line does not simply read
`32'hFFFFFFFF`.)

### 13.4.6 The bound is a bound: `past` and depth

`past(x)` refers to a previous cycle's value, which is how a property spans
cycles. `MonotonicCounter` claims its output never decreases — true for 255
cycles, false on the 256th, when the 8-bit register wraps:

`src/main/scala/FormalProps.scala`
```scala
// §13.4: a property that spans cycles, and a bug that only a DEEP check finds.
// The counter must never decrease, which holds for 255 cycles and then fails
// when it wraps - so a shallow bounded check passes and a deep one refutes.
class MonotonicCounter extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.out := reg

  assert(io.out >= past(io.out), "the counter must never decrease")
}
```

Both of these tests pass, and that is the lesson:

`src/test/scala/FormalTest.scala`
```scala
  // A bound is a bound. The counter only misbehaves when it wraps at 255, so a
  // shallow check passes and only a deep one finds the bug.
  "MonotonicCounter" should "survive a shallow bounded check" taggedAs (NeedsSolver) in {
    verify(new MonotonicCounter(), Seq(BoundedCheck(10)))
  }

  it should "be refuted once the bound reaches the wrap" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new MonotonicCounter(), Seq(BoundedCheck(300)))
    }
  }
```

`BoundedCheck(10)` proves the property — *for ten cycles* — and reports success
on a design that is broken. Only once the bound reaches the wrap does the check
refute it. An unsatisfiable answer is never "this design is correct"; it is
"no counterexample exists within `k`". Choosing `k` is the engineering, and a
deeper bound costs time: the 300-cycle check is most of this suite's 16 seconds.

`past` is also the one construct here that is **not hardware**. It is a
chiseltest FIRRTL transform, so it attaches annotations firtool has never heard
of, and `MonotonicCounter` cannot be emitted at all:

```
error: Unhandled annotation: {anno = {target = ...}, class = "chiseltest.simulator.Firrtl2AnnotationWrapper"}
```

That is why `Generate` emits `Saturate`, `SaturateFixed`, and `AssumeNoOverflow`
but not `MonotonicCounter`.

### 13.4.7 Where ChiselSim fits — and does not

ChiselSim (§13.2.8) is a **simulator**; it has no formal engine, so none of this
section ports to it. Three things follow:

- **The properties themselves carry over.** A Chisel `assert` is checked by
  Treadle, by ChiselSim, and by the solver — one property, three tools. That is
  the whole reason the book's "same assertions are reused" point matters.
- **Reproducing a counterexample is ChiselSim's job**, as `SaturateSimTest`
  above shows. Formal tells you *which* input; a simulator lets you watch what
  the circuit then does, with a waveform if you ask for one.
- **`past` is not available.** Chisel 7's chiseltest-compatibility shim ships a
  formal package, but calling into it fails at compile time with
  `chiseltest.formal.past is unsupported in this compatibility layer` — so a
  multi-cycle property has to be rewritten with an explicit `RegNext` if you
  move to Chisel 7. `chisel3.ltl` (`AssertProperty` and friends) is the modern
  route for temporal properties, and pairs with
  `--emit-chisel-asserts-as-sva` — which `runMain GenerateSva` already
  demonstrates — to hand real SVA to an external formal tool.

Since chiseltest is archived, formal verification through `verify` is frozen at
Chisel 6 along with the rest of it. It still works, as the output above shows;
it just will not follow you to Chisel 7.

---

## 13.5 Build, run, and check

```
$ sbt test
```

Expected tail. Four suites are discovered but only three tests run: `FormalTest`
is entirely tagged `NeedsSolver`, which this task excludes (§13.4.2), so a plain
`sbt test` needs no native tools at all.

```
[info] Run completed in 798 milliseconds.
[info] Total number of tests run: 3
[info] Suites: completed 4, aborted 0
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

With a solver installed, add the formal checks:

```
$ sbt "testOnly FormalTest"
```

§13.2.8's ChiselSim tests are a **separate project** on Chisel 7, so the command
above does not include them. Run them from their own folder:

```
$ cd chiselsim && sbt test
```

```
[info] Run completed in 8 seconds, 199 milliseconds.
[info] Total number of tests run: 7
[info] Suites: completed 4, aborted 0
[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Those four suites need a working **Verilator**; the chapter's own three do not.

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

emits `Assert.sv`, `AssertOverflow.sv`, `TickGenTestTop.sv`, `Saturate.sv`,
`SaturateFixed.sv`, and `AssumeNoOverflow.sv` into `generated/`.
`MonotonicCounter` is deliberately absent — `past` is not hardware, as §13.4.6
explains. `src/main/scala/Generate.scala` also carries a second entry point,
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
  (`verify` + Z3) proves them for all inputs *up to a bound* and catches corner
  cases like overflow. `assume` constrains the environment, `past` spans cycles,
  and an unsatisfiable answer means "no counterexample within `k`" — not
  "correct" (§13.4.6). Formal finds the input; a simulator reproduces it.
- ChiselTest is **archived**; its successor is **ChiselSim** on Chisel 7. §13.2.8
  runs this chapter's tests in both styles: the port is a three-line diff per
  test, tags and `expect` messages survive, `fork`/`join` and Treadle do not.

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
