# Chapter 11 — Example Designs

Two classic building blocks you'll reuse everywhere: a **FIFO buffer** (to
decouple a producer from a consumer) and a **UART** serial port (the easiest way
to talk to an FPGA board). We build a simple *bubble* FIFO first, then generalize
the FIFO interface and show several implementations with different
speed/area trade-offs, and finally a modular UART (transmitter, receiver,
buffer) — with an end-to-end loopback test.

*Conventions: every file path is relative to `tutorial/ch11-example-designs/`,
and every command is run from that folder.*

## What's in this project

```
ch11-example-designs/
├── build.sbt · project/build.properties
├── figures/fifo.png
├── src/main/scala/
│   ├── BubbleFifo.scala        bubble FIFO with a custom write/full read/empty interface
│   ├── fifo/fifo.scala         generalized ready/valid FIFOs (5 implementations)
│   ├── uart/uart.scala         UART: Tx, Rx, Buffer, BufferedTx, Sender, Echo
│   ├── uart/UartLoopback.scala Tx->Rx loopback (tutorial helper for testing)
│   └── Generate.scala
└── src/test/scala/
    ├── BubbleFifoTest.scala
    ├── fifo/FifoTest.scala
    └── uart/UartTest.scala
```

---

## 11.1 A bubble FIFO

A FIFO decouples a writer from a reader. This one uses a simple custom interface:
`write`/`full` on the writer side, `read`/`empty` on the reader side.

<p align="center">
  <img src="figures/fifo.png" alt="A writer, a FIFO buffer, and a reader" width="480">
</p>

***Figure 11.1** — Writer → FIFO → Reader. `full`/`empty` are the handshake
(flow-control) flags.*

Each **stage** is one data register plus a two-state (empty/full) FSM:

`src/main/scala/BubbleFifo.scala`
```scala
class FifoRegister(size: Int) extends Module {
  // ...
  when(stateReg === empty) {
    when(io.enq.write) { stateReg := full; dataReg := io.enq.din }
  }.elsewhen(stateReg === full) {
    when(io.deq.read) { stateReg := empty }
  }
  io.enq.full := (stateReg === full)
  io.deq.empty := (stateReg === empty)
  io.deq.dout := dataReg
}
```

The FIFO chains `depth` stages; data **bubbles** downstream one stage per cycle:

`src/main/scala/BubbleFifo.scala`
```scala
val buffers = Array.fill(depth) { Module(new FifoRegister(size)) }
for (i <- 0 until depth - 1) {
  buffers(i + 1).io.enq.din   := buffers(i).io.deq.dout
  buffers(i + 1).io.enq.write := ~buffers(i).io.deq.empty
  buffers(i).io.deq.read      := ~buffers(i + 1).io.enq.full
}
io.enq <> buffers(0).io.enq
io.deq <> buffers(depth - 1).io.deq
```

It's simple and cheap, but has two limits: max throughput is **one word per two
cycles** (each stage toggles empty/full), and latency is **`depth` cycles**
(the bubble has to travel through). `BubbleFifoTest` also demonstrates
ChiselTest's `fork`/`join` for concurrent producer/consumer threads.

---

## 11.2 Generalized FIFOs (ready/valid + inheritance)

Generalize the handshake to **ready/valid** (`DecoupledIO`) and parameterize by
a Chisel type. Chisel already ships a ready/valid bundle; simplified, it looks
like this:

```scala
class DecoupledIO[T <: Data](gen: T) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val bits  = Output(gen)
}
```
*illustrative — a simplification; the real `DecoupledIO` extends an abstract
base class.*

By convention, data is transferred on a cycle where **both** `ready` and
`valid` are asserted. A shared interface and an abstract base make FIFO
implementations interchangeable; the enqueue port flips `DecoupledIO` because
it's defined from the producer's (writer's) viewpoint:

`src/main/scala/fifo/fifo.scala`
```scala
class FifoIO[T <: Data](private val gen: T) extends Bundle {
  val enq = Flipped(new DecoupledIO(gen))
  val deq = new DecoupledIO(gen)
}

abstract class Fifo[T <: Data](gen: T, val depth: Int) extends Module {
  val io = IO(new FifoIO(gen))
  require(depth > 0, "Number of buffer elements needs to be larger than 0")
}
```

Five implementations (all in `fifo.scala`), each a subclass of `Fifo`:

| Class | Idea | Trade-off |
|-------|------|-----------|
| `BubbleFifo` | chain of 1-word buffers | simplest; 1 word / 2 cycles |
| `DoubleBufferFifo` | each stage holds 2 words (data + shadow) | half the stages/latency; full throughput |
| `RegFifo` | circular buffer in a `Reg(Vec(...))` | good for small FIFOs |
| `MemFifo` | circular buffer in `SyncReadMem` | good for large FIFOs (1-cycle read latency handled) |
| `CombFifo` | `MemFifo` + a `DoubleBuffer` output stage | decouples the memory-read path |

**No combinational peek-ahead.** It's tempting to let a single-buffer stage
look at *both* handshakes at once — accept new data whenever the producer's
`valid` **and** the consumer's `ready` are asserted, even while "full" — to
skip the two-cycle empty/full toggle. Don't: that wires a combinational path
straight from the consumer's handshake to the producer's, which breaks the
ready/valid protocol's contract that `ready` and `valid` must not depend
combinationally on each other. `DoubleBufferFifo` gets the same benefit
legitimately, with a second (shadow) register instead of a forbidden
shortcut.

Because they share `FifoIO`, **one generic test drives them all**
(`src/test/scala/fifo/FifoTest.scala`, `def testFn[T <: Fifo[_ <: Data]]`),
checking a single transfer, fill/drain in order, and a full-throughput speed
test. The double-buffer stage shows how to stay `ready` while full without
creating a combinational path between the two handshakes:

`src/main/scala/fifo/fifo.scala`
```scala
io.enq.ready := (stateReg === empty || stateReg === one)
io.deq.valid := (stateReg === one   || stateReg === two)
io.deq.bits  := dataReg
```

**`RegFifo`** — the hardware version of a software **circular buffer**: two
pointers (read and write) walk around a small register file
(`Reg(Vec(depth, gen))`) and wrap to `0` at `depth`; the number of elements
queued is the distance between them. When the two pointers are equal, the
queue is either empty or full — indistinguishable from the pointers alone —
so `RegFifo` also keeps an explicit `emptyReg`/`fullReg` pair.

Both pointers come from the same wrapping-counter helper, which needs to
return **two** values: the current count, and its would-be *next* value (so
the caller can tell whether a pointer is about to catch up to the other one
*before* it does). In Scala, a function can return a **tuple** — just wrap
comma-separated values in parentheses:

```scala
val t = (v1, v2)
```
*illustrative*

and unpack one back into two names, using the same parenthesis syntax on the
left of an assignment:

```scala
val (x1, x2) = t
```
*illustrative*

`src/main/scala/fifo/fifo.scala`
```scala
def counter(depth: Int, incr: Bool): (UInt, UInt) = {
  val cntReg = RegInit(0.U(log2Ceil(depth).W))
  val nextVal = Mux(cntReg === (depth - 1).U, 0.U, cntReg + 1.U)
  when(incr) { cntReg := nextVal }
  (cntReg, nextVal)
}
```

`readPtr`/`writePtr` are each `counter`'s current value; `nextRead`/`nextWrite`
are its look-ahead value. The empty/full flags update per case:

- **write only** (`enq.valid`, not full): store `io.enq.bits` at `writePtr`,
  clear `emptyReg`, set `fullReg` when `nextWrite === readPtr` (the write
  pointer is about to catch the read pointer), advance the write pointer.
- **read only** (`deq.ready`, not empty): clear `fullReg`, set `emptyReg` when
  `nextRead === writePtr`, advance the read pointer.
- **read and write in the same cycle**: do both at once — write and advance
  the write pointer, read and advance the read pointer — comparing each
  pointer against the *other's next* value so a simultaneous read+write on a
  full (or empty) buffer still resolves correctly.

---

## 11.3 A serial port (UART)

A UART sends a byte as: one **start** bit (0), 8 **data** bits (LSB first), then
one or two **stop** bits (1); the line idles high. We build it modularly.

The line sits idle high until a byte is sent: it drops low for one bit period
(the start bit), then holds each data bit `b0` through `b7` in turn (least
significant first) for one bit period each, then rises back high for one or
two stop-bit periods and stays high (idle) until the next byte begins.

***Figure 11.2** — One byte transmitted by a UART: idle-high, start bit low,
data bits `b0`…`b7`, then stop bit(s) high.*

**Transmitter** — all state in three registers (a shift register, a baud-rate
counter, and a bit counter); no explicit FSM. It builds the 11-bit frame
`stop,stop ## data ## start` and shifts it out LSB-first:

`src/main/scala/uart/uart.scala`
```scala
io.channel.ready := (cntReg === 0.U) && (bitsReg === 0.U)
io.txd := shiftReg(0)
// when a bit period elapses and a byte is valid:
shiftReg := 3.U ## io.channel.bits ## 0.U   // two stop, data, one start
bitsReg := 11.U
```

**Receiver** — synchronize `rxd`, wait for the start-bit falling edge, then wait
1.5 bit-times to land in the *center* of bit 0, and sample every bit-time after:

`src/main/scala/uart/uart.scala`
```scala
val rxReg = RegNext(RegNext(io.rxd, 0.U), 0.U)          // synchronizer
val falling = !rxReg && (RegNext(rxReg) === 1.U)
// START_CNT ~ 1.5 bit times, BIT_CNT ~ 1 bit time
```

A single-byte **`Buffer`**, a **`BufferedTx`** (Tx + buffer), a **`Sender`**
(streams "Hello World!"), and an **`Echo`** (Rx → Tx) round out the design.

> **Book vs. here:** the book verifies the UART on real hardware (no unit test).
> To make it checkable in simulation, this project adds `UartLoopback`
> (`src/main/scala/uart/UartLoopback.scala`) — a `Tx` whose `txd` is wired
> straight into an `Rx`'s `rxd`. `UartTest` sends a byte in and expects the same
> byte out.
>
> Two things that test taught us, worth remembering: (1) the Rx has an initial
> idle countdown, so the transmitter must not start before the receiver is
> listening (the test steps 200 cycles first); and (2) a full frame exceeds
> ChiselTest's default 1000-cycle idle timeout, so the test calls
> `dut.clock.setTimeout(0)`.

---

## 11.4 A multi-clock memory

In a design with several clock domains you sometimes need to move data safely
from one domain to another. A synchronizer chain (like the Rx's `rxReg`
above) is one option; a **multi-clock memory** — one memory, several ports,
each running off its own clock — is another.

Chisel supports multiple clocks with `withClock` and `withClockAndReset`:
every storage element created inside a `withClock(clk) { ... }` block is
clocked by `clk` instead of the enclosing module's implicit clock. For a
multi-clock *memory* specifically, the rule is: define the memory itself
**outside** any `withClock` block, and wrap **each port's** access logic in
its own `withClock` block, so port `i` is clocked by its own `io.ps(i).clk`:

```scala
class MemoryIO(val n: Int, val w: Int) extends Bundle {
  val clk   = Input(Bool())
  val addr  = Input(UInt(log2Up(n).W))
  val datai = Input(UInt(w.W))
  val datao = Output(UInt(w.W))
  val en    = Input(Bool())
  val we    = Input(Bool())
}

class MultiClockMemory(ports: Int, n: Int = 1024, w: Int = 32) extends Module {
  val io = IO(new Bundle {
    val ps = Vec(ports, new MemoryIO(n, w))
  })

  val ram = SyncReadMem(n, UInt(w.W))   // the memory: outside every withClock block

  for (i <- 0 until ports) {
    val p = io.ps(i)
    withClock(p.clk.asClock) {          // this port's own withClock block
      val datao = WireDefault(0.U(w.W))
      when(p.en) {
        datao := ram(p.addr)
        when(p.we) {
          ram(p.addr) := p.datai
        }
      }
      p.datao := datao
    }
  }
}
```
*illustrative — not part of this chapter's `src/`; adapted from the book's
`MultiClockMemory.scala`.*

Multi-clock memories bring their own constraints: two (or more) ports must
never write the **same address on the same cycle** — doing so risks
metastability — and you must decide (and configure) the read-during-write
behavior: **write-first** (a simultaneous read on the written address is
forwarded the new data) or **read-first** (the read returns the *old* value).

> Multi-clock support in ChiselTest is still early-stage: there is no
> automatic multi-domain clock stepping, so a test must toggle each clock
> domain's clock signal manually.

---

## 11.5 Build, run, and check

```
$ sbt test
```

Expected tail (7 tests):

```
[info] Tests: succeeded 7, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits `BubbleFifo.sv`, `MemFifo.sv`, `DoubleBufferFifo.sv`, and `Sender.sv`.

---

## 11.6 Recap

- A **FIFO** decouples producer and consumer; the **bubble FIFO** is simple but
  peaks at one word per two cycles with `depth`-cycle latency.
- Generalize to **ready/valid** (`DecoupledIO`) + a Chisel type parameter, and
  use an **abstract base** so many implementations share one interface — and one
  test (`DoubleBuffer`, `Reg`/`Mem` circular buffers, combined).
- A **UART** decomposes into `Tx`/`Rx`/`Buffer`; the Rx samples each bit at its
  center after a 1.5-bit start delay. Test serial links with a **loopback**.
- A **multi-clock memory** moves data across clock domains without a
  synchronizer: the memory lives outside every `withClock` block, each port's
  logic lives inside its own — but no two ports may write the same address at
  once, and the read-during-write behavior must be chosen explicitly.

## 11.7 Exercises

1. **Explore the bubble FIFO.** `BubbleFifoTest` (and the book's `FifoSpec`)
   drive the FIFO through three phases worth watching in a waveform viewer
   such as [GTKWave](http://gtkwave.sourceforge.net/) (the book's repository
   generates a VCD and runs this via `make fifo`): (1) a single word written
   and read, which you can watch *bubble* one stage per cycle all the way
   through — latency equals `depth`; (2) filling the FIFO completely, then a
   single read, where the resulting "empty" bubbles from the reader's end
   back to the writer's end, again taking `depth` cycles before the writer
   sees room again; and (3) a tight write/read loop at maximum speed, capped
   at one word every **two** clock cycles (a stage must toggle empty↔full for
   every word). Compare `BubbleFifo` vs. `DoubleBufferFifo` vs. `MemFifo` in
   `FifoTest`'s speed test (the assert prints cycles/word) — which reach one
   word per cycle? Then rewrite/rerun the bubble-FIFO test against a
   circular-buffer FIFO (`RegFifo`/`MemFifo`) and compare bandwidth, latency,
   and (after synthesis) resource use.
2. **A simpler FIFO.** Write a 4-element register FIFO with 2-bit wrapping
   read/write pointers, treating equal pointers as *empty* (max 3 stored) — no
   empty/full flags, and no need for the `counter()` look-ahead tuple since
   there's nothing to disambiguate. How much simpler is it?
3. **UART.** You'll need an FPGA board with a serial port (or USB-serial
   adapter) and a terminal program — e.g. `gtkterm` on Linux — configured to
   the board's port at **115200 baud, no parity, no flow control**.
   `make uart` generates the Verilog; the book's repository also includes a
   Quartus project for the DE2-115 board. Synthesize and program the FPGA —
   you should see a greeting message appear in the terminal. Then: (a) extend
   the blinking-LED example with a UART that writes `0`/`1` (via
   `BufferedTx`, as in `Sender`) each time an LED turns off/on; and (b) since
   that's slow enough (a couple of characters a second) to ignore the
   ready/valid handshake, go further and stream the digits 0–9 repeatedly as
   fast as the baud rate allows — this requires polling `ready` (the
   transmit-buffer-free status) between characters — or place one of your
   FIFOs in front of the `Tx` for buffering.
4. **FIFO exploration.** Synthesize all the FIFO variations — bubble, double
   buffer, register-memory, on-chip-memory, combined — at three sizes (4, 16,
   and 256 words) and compare throughput, fall-through latency, resource use,
   and maximum clock frequency. (The
   [`ip-contributions`](https://github.com/freechipsproject/ip-contributions)
   repository has further FIFO variations to include.) Where are the sweet
   spots for each size?

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 10 — Hardware Generators](../ch10-hardware-generators/README.md)**.
Next: **[Chapter 12 — Interconnect](../ch12-interconnect/README.md)**.
