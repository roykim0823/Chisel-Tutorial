# Chapter 12 — Interconnect

Larger systems are built by connecting components, and **interconnect** defines
how. Standards such as [Wishbone](https://en.wikipedia.org/wiki/Wishbone_(computer_bus))
or AXI exist to simplify that composition: a device built against a published
interface works with any master that speaks the same one, so neither side has to
be designed for the other. Interconnect is used **between chips** (external, e.g.
a CPU talking to an external memory chip) or **within a chip**, where the
resulting system is called a system-on-chip (SoC).

This chapter starts from the classic microprocessor bus, adapts it to an on-chip
"bus" (multiplexers instead of tri-state), adds handshaking for devices with
variable latency — combinational, registered, and pipelined, then ready/valid
and tagged out-of-order completion — builds a memory-mapped IO device bridging a
bus to a ready/valid stream, and surveys the standards (Wishbone, AXI).

*Conventions: every file path is relative to `tutorial/ch12-interconnect/`, and
every command is run from that folder.*

### How the chapter fits together

One question runs through the whole chapter: **when is a transfer finished, and
who says so?** Each section answers it differently.

**[12.1](#121-a-classic-microprocessor-bus) — the off-chip bus.** Nobody says
so. The peripheral's datasheet states an **access time**; the processor waits
that long and reads. No signal announces the end of a transfer, and no clock is
needed to count it out.

**[12.2](#122-an-on-chip-bus) — the same bus, on chip.** Tri-state sharing is
[not practical inside a chip](#why-tri-state-is-not-practical-inside-a-chip), so
an address decoder and a read multiplexer replace it. Every access still takes
exactly one clock cycle, fixed in advance, so there is still nothing to say.

**[12.3](#123-handshake-schemes) — handshaking.** Devices now take a *varying*
number of cycles, so only the device knows when it is done, and it has to say
so on a wire. Five ways to say it, each built as the same four counters so that
the handshake is the only difference:

| Scheme | The rule | Costs |
|---|---|---|
| [combinational](#1231-the-combinational-handshake) | `ack` comes back in the request cycle | a long path master → decoder → slave → master, which limits the clock |
| [pipelined](#1232-the-pipelined-handshake) | request lasts one cycle, `ack` arrives later | must remember which command an `ack` belongs to |
| [registered](#1233-the-registered-handshake) | `ack` from a flip-flop, request held until it comes | the bus is busy for two cycles per transfer |
| [ready/valid](#1235-readyvalid-the-two-sided-handshake) | both sides can stall: transfer only when `ready` *and* `valid` | one channel per direction, so a transaction needs several |
| [tagged](#1236-tagged-completion-answering-out-of-order) | every command carries an id, every answer repeats it | a table of outstanding commands in the slave |

[Section 12.3.4](#1234-the-three-schemes-compared) measures the first three
against each other: pipelined manages 1 transfer per cycle, registered 1 per 2,
and combinational 1 per cycle when the device answers at once but 1 per 3 once
it needs two wait states. [Section
12.3.7](#1237-handshakes-this-chapter-does-not-build) names the schemes this
chapter does *not* build.

**[12.4](#124-memory-mapped-devices) — devices in an address map.** A UART-like
device gets an address range, and `MemMappedRV` bridges the bus to a
ready/valid stream, with a status register software can poll.

**[12.5](#125-bus-and-interface-standards) — the real standards.** Wishbone and
AXI4-Lite turn out to be the schemes above under other names. The same four
counters are built four more times — three Wishbone slaves and `AxiLiteCounter`
— so the only thing that differs is the protocol.

**[Recap](#127-recap).** One table lists every module in the chapter next to the
actual line of Chisel that drives its `ack` or `ready`, its scheme, and its
measured throughput.

If you only read one thing, read [Section 12.3.4](#1234-the-three-schemes-compared).

---

## 12.1 A classic microprocessor bus

Figure 12.1 shows a classic microcomputer: a CPU connected over one shared
[system bus](https://en.wikipedia.org/wiki/System_bus) to memory and I/O
devices, as was common with early microprocessors such as the
[Z80](https://en.wikipedia.org/wiki/Zilog_Z80) and the
[6502](https://en.wikipedia.org/wiki/MOS_Technology_6502). Nothing is built here:
the shared off-chip bus is obsolete, and it is worth two minutes only because
every on-chip interconnect in this chapter is a variation on it.

<p align="center">
  <img src="figures/bus.png" alt="A classic computer bus" width="480">
</p>

***Figure 12.1** — A CPU, memory, and I/O on shared address/data/control buses.*

The bus splits into an **address** bus, a **data** bus, and **control** signals
such as *read* and *write*. The CPU drives the address and control lines and is
the only master, so it issues every command and nothing needs arbitration (a
second master would; [Chapter 5
§5.4](../ch05-combinational-building-blocks/README.md#54-arbiter) builds the
arbiters). Both commands carry an address, which selects either a word of memory
or a **register in an I/O device** — the wiring does not distinguish the two,
which is what memory-mapped I/O means
([Section 12.4](#124-memory-mapped-devices)). Not all address lines reach every
peripheral: the upper bits feed a decoder whose outputs drive the devices'
chip-select (CS) inputs.

On a read, the selected device drives the data bus after its **access time**. On
a write, the CPU drives the data bus and the peripheral accepts the data, often
on the rising edge of the write strobe. Because that bus is bidirectional and
shared by every device, each output needs a
[tri-state](https://en.wikipedia.org/wiki/Three-state_logic) driver: in the
tri-state configuration both output transistors are disabled and the pin is
practically disconnected from the logic.

Note that in its simplest form **the bus has no clock at all** — timing is
defined purely by the read and write access times of the peripherals, which
means the CPU's cycle has to suit the slowest device on the bus.
[Section 12.3](#123-handshake-schemes) is where that assumption is replaced by a
signal.

Modern computers use a dedicated bus per purpose — a memory bus for external
memory, and serial, point-to-point I/O buses such as
[PCI Express](https://en.wikipedia.org/wiki/PCI_Express) for peripherals. Even
so, the classic picture of an address bus, a data bus, and chip selects is still
the mainstream mindset for core interconnection, and it is what we adapt for
on-chip use next.

---

## 12.2 An on-chip bus

The concept translates to the inside of a chip, with two changes: the
**tri-state data bus goes away**, and the connections are **clocked**.

### Why tri-state is not practical inside a chip

On a board tri-state buys scarce pins and traces, and a board can police it.
Inside a chip the wires it saves are nearly free, and the risk is not worth it:
if two output enables are ever on at once — a decoder bug, a glitch while the
selection switches — the wire becomes a direct path from supply to ground, and
on silicon that is a respin rather than a rework. Internal tri-state buffers are
scarce in standard-cell flows and absent from modern FPGA fabrics in any case,
where synthesis rewrites such a net into the multiplexer below — so you get the
mux either way, just further from the source.

Tri-state does survive at the **chip boundary**, where an external bus still
needs bidirectional pins, and that is the one place Chisel offers it: an
`Analog(w.W)` port, `attach`ed to another `Analog` or to a `BlackBox` pad cell.
`Analog` extends `Element` rather than `Bits`, so it carries no operators at
all — a tri-state *internal* bus is not merely discouraged in Chisel, it is
inexpressible (see [§L](../SYSTEMVERILOG-NOTES.md#l-things-chisel-will-not-generate)).

### The read multiplexer

So we **split** the data bus into two sets of wires, one for writing and one for
reading, and select the read path with a **multiplexer** driven by the address
decoder. On-chip wires are cheap compared with PCB traces and connectors, so the
duplication costs little.

<p align="center">
  <img src="figures/bus-on-chip.png" alt="The on-chip bus" width="520">
</p>

***Figure 12.2** — On-chip: a read mux replaces the tri-state data bus; the
decoder drives both the chip selects and the mux.*

Figure 12.2 is small enough to build outright, and worth building because it is
the only part of the on-chip bus with no protocol in it at all — just wiring:

`src/main/scala/soc/BusDecoder.scala`
```scala
class BusDecoder(val devices: Int = 4, val addrWidth: Int = 8,
                 val deviceBytes: Int = 16) extends Module {
  ...
  // Each device owns `deviceBytes` of the address space, so the bits below that
  // window address *within* a device, and only the bits above it choose one.
  private val index = io.address(lo + sel - 1, lo)

  for (i <- 0 until devices) {
    io.cs(i) := index === i.U
  }
  io.rdData := io.deviceRdData(index)
}
```

Those three lines are the whole of Figure 12.2: `index` is the address decode,
the `for` loop fans it out into one-hot chip selects, and
`io.deviceRdData(index)` is the read multiplexer that replaces the tri-state
data bus — a `Vec` indexed by hardware, which Chisel elaborates into the mux the
figure draws. There are no registers, which is the point: this is
who-is-selected and whose-data-comes-back, not when-a-transfer-completes.

Note that `addrWidth` is the width of the *whole* bus address, not of the device
selector. With the default parameters — `devices = 4`, `deviceBytes = 16`,
`addrWidth = 8` — `index` is `io.address(5, 4)`, so the eight bits split three
ways:

| Bits | Role | Read by |
|---|---|---|
| `[3:0]` | offset inside a device: 16 bytes, four 32-bit registers | the selected device |
| `[5:4]` | `index` — which of the four devices | the decoder and the read mux |
| `[7:6]` | headroom — unused at four devices | the decoder, once `devices` grows |

The two top bits are spare rather than wasted: `sel` widens with the device
count, so `devices = 8` makes `index` `io.address(6, 4)` and `devices = 16`
makes it `io.address(7, 4)`, decoding the whole address. Six bits (`lo + sel`)
is the narrowest address the `require` accepts at four devices.

With four devices, then, the decoder inspects two bits and ignores two. `0x00`
and `0x0c` both select device 0 and `0x10` selects device 1, while `0x40`
aliases onto `0x00` because bits 7 and 6 are never examined — the same "not all
address lines reach every peripheral" arrangement as off-chip, done with bit
slicing instead of wiring. Widen `devices` to 16 and the aliasing disappears,
since every bit is then decoded.

Splitting the data bus makes the two directions asymmetric, which is why both
`cs` and the mux exist:

- **Writing is a broadcast.** Address and write data reach every device, and the
  chip select is what stops all but one from latching — the same job it did
  off-chip, and the reason `BusDecoder` needs no write port.
- **Reading is a selection.** Every device drives its own `rdData` wires
  continuously and the mux picks one. Nothing is ever switched off.

The cost of the trade is that the read path grows with the system: a tri-state
bus grows by one more tap on the same wire, while `deviceRdData` grows by another
32 wires and another mux input, so area and mux delay both rise with the device
count. For a handful of peripherals that is the right trade; past that, systems
stop widening one flat bus and go hierarchical — segments joined by bridges, a
crossbar, or a network-on-chip, such as the OCP-based one mentioned in
[Section 12.5](#open-core-protocol).

### Checking the wiring

`BusDecoder` has no registers, so the test needs no clock: `poke` an address,
`expect` the outputs, and read the answer in the same instant. The first case
walks a handful of byte addresses and checks the one-hot chip selects, the
address split above included: `0x0c` still selects device 0, and `0x35` selects
device 3.

`src/test/scala/BusDecoderTest.scala`
```scala
  "A bus decoder" should "select one device per 16-byte window" in {
    test(new BusDecoder(devices = 4)) { dut =>
      // Byte address -> which device. The low four bits address *within* a
      // device, so 0x00 and 0x0c both land on device 0.
      for ((addr, device) <- Seq(0x00 -> 0, 0x0c -> 0, 0x10 -> 1,
                                 0x20 -> 2, 0x35 -> 3)) {
        dut.io.address.poke(addr.U)
        for (i <- 0 until 4) {
          dut.io.cs(i).expect((i == device).B,
            f"address 0x$addr%02x should select device $device, not $i")
        }
      }
    }
  }
```

The second case is the property that matters for a bus that used to be
tri-state: sweep every address in the map and count how many chip selects are
active. Anything but exactly one would be two devices answering at once. The
third gives each device a distinguishable read value and checks the mux hands
back the selected one:

```scala
  it should "never select two devices at once" in {
    test(new BusDecoder(devices = 4)) { dut =>
      for (addr <- 0 until 64) {
        dut.io.address.poke(addr.U)
        val hot = (0 until 4).count(i => dut.io.cs(i).peekBoolean())
        assert(hot == 1, f"address 0x$addr%02x drove $hot chip selects, expected 1")
      }
    }
  }

  it should "route the selected device's data back through the read mux" in {
    test(new BusDecoder(devices = 4)) { dut =>
      // Give each device a distinguishable value, then check the mux picks it.
      for (i <- 0 until 4) {
        dut.io.deviceRdData(i).poke((0xd0 + i).U)
      }
      for ((addr, device) <- Seq(0x00 -> 0, 0x10 -> 1, 0x20 -> 2, 0x30 -> 3)) {
        dut.io.address.poke(addr.U)
        dut.io.rdData.expect((0xd0 + device).U,
          f"address 0x$addr%02x should read device $device")
      }
    }
  }
```

Run just this suite:

```
sbt "testOnly BusDecoderTest"
```

```
[info] BusDecoderTest:
[info] A bus decoder
[info] - should select one device per 16-byte window
[info] - should never select two devices at once
[info] - should route the selected device's data back through the read mux
[info] Run completed in 1 second, 15 milliseconds.
[info] Total number of tests run: 3
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

[Section 12.6](#126-build-run-and-check) runs the chapter's whole suite.

With this simple setup we assume every read or write completes in a single
clock cycle — realistic only for very small systems. A first, natural
extension is to expect the read result **one clock cycle after** the request,
which matches on-chip memories with a synchronous (registered), one-cycle-
latency read port, and also relaxes timing for IO devices. Writes are still
assumed to complete in one cycle.

To go further — devices with different or *varying* latency — we need
**handshaking**: the processor signals the start of a transaction with a read
or write request, and the device signals the *end* of the transaction with an
acknowledgment.

---

## 12.3 Handshake schemes

Up to here the master knew the timing. With a fixed single-cycle bus — or the
fixed one-cycle-later variant — "the transfer is finished" is something the
master can *count* rather than observe, and no signal has to say it. A device
with variable latency breaks that arrangement, because the only party that knows
when the data is good is the device. So the timing contract moves onto a wire:
the master **requests** by asserting `rd` or `wr`, and the slave
**acknowledges** with `ack` when the transfer is complete. A device that needs
longer simply keeps `ack` low, and the cycles it inserts that way are called
**wait states**.

That much the whole **request/acknowledge family** shares. What its members
disagree about is a single question — **when may `ack` rise?** — and the
disagreement is generated by two independent decisions:

- **Where does `ack` come from?** A wire off the request can answer inside the
  request cycle, but it strings the master, the address decoding, the slave, and
  the master's `ack` input onto one combinational path. Out of a flip-flop that
  path is gone, but the answer can then never arrive before the next clock edge.
- **How long does the master drive the request?** Holding it until `ack` keeps
  the bus occupied for the whole transfer. Releasing it after one cycle frees the
  bus immediately — but then `ack` refers to a command that is no longer on the
  wires, and the two sides have to agree on which one it was.

Three of the four combinations are worth building, in order of how much they
decouple the two sides ([Section 12.3.4](#1234-the-three-schemes-compared)
explains why the fourth is not):

| | `ack` is… | master holds the request? | cost |
|---|---|---|---|
| [12.3.1 combinational](#1231-the-combinational-handshake) | a wire off the request | yes | a path from master to slave and back, on the critical path |
| [12.3.2 pipelined](#1232-the-pipelined-handshake) | a flip-flop | no — one cycle | must track which command an ack belongs to |
| [12.3.3 registered](#1233-the-registered-handshake) | a flip-flop | yes | the bus stays busy for the whole transfer |

All three build their device behind one and the same port:

`src/main/scala/soc/ReqAckIO.scala`
```scala
class ReqAckIO(addrWidth: Int) extends Bundle {
  val address = Input(UInt(addrWidth.W))
  val rd = Input(Bool())
  val wr = Input(Bool())
  val rdData = Output(UInt(32.W))
  val wrData = Input(UInt(32.W))
  val wrMask = Input(UInt(4.W))
  val ack = Output(Bool())
}
```

Seven wires: an address, a read and a write strobe, the two data directions, a
byte mask, and an acknowledgment. Nothing in them fixes *when* `ack` may rise —
that is the handshake, and it is a property of the device rather than of the
port. What changes between the three schemes is the slave's logic, not this
declaration. Nothing in the types enforces a scheme either: a slave that
acknowledges too early, or a master that lets go of its request too soon, is a
type-correct Chisel design and a broken bus.

The device behind that port is **the same four counters** every time, so that
nothing but the handshake varies: four 32-bit counters at word addresses `0x0`,
`0x4`, `0x8` and `0xc`, each incrementing on every clock cycle, and each
writable — a write loads a new value into one counter. Free-running counters are
what make the timing visible in the *data* and not only in the waveform: because
the value moves every cycle, the number a read returns depends on exactly which
cycle the device sampled it, so a scheme that answers one cycle later answers
with a different number.

The three subsections come in the order the schemes are usually met: the
same-cycle handshake first, then the pipelined one that removes its combinational
path, and last the registered one that sits between them — the halfway house a
first design tends to reach for. [Section 12.3.4](#1234-the-three-schemes-compared)
then measures what each one actually costs.

Those three exhaust the req/ack family, not handshaking. Two further schemes are
built elsewhere in this chapter and get a subsection of their own here, because
they answer questions `ack` cannot:
[Section 12.3.5](#1235-readyvalid-the-two-sided-handshake) is **ready/valid**,
where the *receiver* can stall too (`MemMappedRV`, `RegFifo`, and every AXI
channel), and [Section 12.3.6](#1236-tagged-completion-answering-out-of-order)
is **tagged completion**, where a response names the transaction it belongs to
and may therefore overtake another (`Axi4OooReadMemory`).
[Section 12.3.7](#1237-handshakes-this-chapter-does-not-build) then names the
families that are real but unbuilt here, so the boundary of the chapter is
explicit rather than implied.

### 12.3.1 The combinational handshake

The simplest handshake reacts within the request cycle: the processor drives
the address bus (`address`) and the read signal (`rd`) in cycle 2, and `ack`
must react **combinationally**, within that same first clock cycle.

<p align="center">
  <img src="figures/bus-ack.png" alt="A read transaction with a combinational acknowledge" width="560">
</p>

***Figure 12.3** — A read transaction with a combinational acknowledge. Gray
shading marks a signal whose value is undefined — nobody is driving it, or it
does not matter.*

In Figure 12.3, the read data is *not* available within one clock cycle but two
clock cycles later, as seen in cycle 4; `data` and `ack` are each valid for a
single clock cycle. Since `ack` is what ends the transaction, the processor has
to keep `address` and `rd` driven **until it sees `ack`** — which is why both
stay asserted across cycles 2, 3, and 4 in the diagram, and why no second
request can be issued in the meantime. Note also that `ack` is drawn as
undefined until part-way through cycle 2: it settles to its (low) value after a
combinational delay from `rd` rising, not on a clock edge.

The benefit of this protocol specification is that a single-cycle transaction
becomes *possible* — a device that can answer immediately raises `ack` already
in cycle 2. The price is that the handshake, including address decoding, is a
combinational circuit through the peripheral, which can hurt the maximum clock
frequency. The classic Wishbone protocol uses exactly this same-cycle
acknowledgment (Wishbone later added a pipelined mode too), and
`WishboneCounterWait` in [Section 12.5](#wishbone) is a working device with
exactly the timing drawn above.

Built against this chapter's own port, the scheme is almost entirely wire:

`src/main/scala/soc/CounterDevice.scala`
```scala
class CounterDeviceComb(val waitStates: Int = 0) extends Module {
  val io = IO(new ReqAckIO(4))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.address(3, 2)
  val active = io.rd || io.wr

  val waitReg = RegInit(0.U(math.max(1, log2Ceil(waitStates + 1)).W))
  val done = waitReg === waitStates.U

  io.ack := active && done              // combinational in the request
  io.rdData := cntRegs(idx)
  ...
}
```

`waitStates` is the device's access time. At `0` the `done` term is always true,
`ack` collapses to `active`, and a transfer finishes inside its request cycle.
At `2` this is Figure 12.3 exactly: the ack stays low for the request cycle and
the one after, and rises in the third, while the master holds `rd` throughout
because it has no way to know when the answer is coming.

Either way `ack` is **combinational in the request** — `active` is *this*
cycle's `rd || wr`, so the path from the master's request through the device and
back to its `ack` input never crosses a flip-flop. A test pins that down with no
clock stepping at all: assert the request, see the ack, withdraw the request,
and the ack is gone in the same cycle. A registered ack could not do that, and
this is precisely the path that limits the clock frequency.

Same-cycle acknowledgment has been criticized — a single-cycle transaction is
rarely realistic in a larger system — leading to the **SimpCon** proposal: a
specification where `ack` (or busy/ready) need not be valid in the request
cycle, enabling pipelined transactions and avoiding the combinational path
between processor, address decoding, and device.

### 12.3.2 The pipelined handshake

A pipelined handshake avoids the single-cycle combinational loop: a read or
write command is signaled by asserting `rd` or `wr` for a single clock cycle
(address and, for a write, the write data must be valid during that cycle —
commands are valid for one cycle only), and each command must be acknowledged
by an active `ack` **the earliest
one cycle after the command** — later still if the device needs to insert
**wait states** by delaying `ack`. Read data is available together with `ack`,
for one clock cycle.

<p align="center">
  <img src="figures/bus-pipe-ack.png" alt="Read transaction with a pipelined acknowledgement" width="620">
</p>

***Figure 12.4** — Read transaction with a pipelined acknowledgement: a first
read with a wait state, then two requests issued back to back.*

The request from the processor is only a single clock cycle long: `rd` is
asserted in cycle 2 with `A1` on `address`, and — unlike the combinational
protocol — **neither needs to stay driven until the acknowledgment**, so both
are released again in cycle 3. Compared to the former protocol, `ack` needs to be
valid (low or high) no earlier than one clock cycle after the `rd` command,
that is in cycle 3; there it is *low*, meaning the device inserts one wait
state, and it goes high in cycle 4 together with `D1`. So the first read has
two clock cycles of latency — the same latency as the combinational example
above.

The difference shows up on the next two reads. Because a request needs to be
valid for only a single cycle, `A2` and `A3` can be requested **back-to-back**
in cycles 5 and 6, and each is acknowledged at the earliest time the protocol
allows — one cycle later, in cycles 6 and 7, with `D2` and `D3`. Once the
pipeline is full this allows a throughput of one data word per clock cycle,
which the combinational protocol cannot reach.

The Patmos processor uses an OCP variant with exactly this protocol for its IO
devices (memory is connected via a separate burst interface); the *Patmos
Handbook* documents the OCP interfaces in detail. The
[`t-crest/soc-comm`](https://github.com/t-crest/soc-comm) Chisel repository
implements this pipelined interface for multicore devices such as a
network-on-chip.

This point-to-point, pipelined interconnect generalizes naturally: processor
and peripherals each connect via such an interface to a switching fabric, and
if the system has more than one master, the fabric must **arbitrate** among
masters requesting reads or writes.

`CounterDevice` is that scheme built: four free-running 32-bit counters you can
read and load. Because the read result arrives the cycle *after* the command,
and the command is valid only during that cycle, it **registers the address**
(`addrReg`) and **delays the ack** (`ackReg`):

`src/main/scala/soc/CounterDevice.scala`
```scala
class CounterDevice extends Module {
  val io = IO(new ReqAckIO(4))

  val ackReg = RegInit(false.B)
  val addrReg = RegInit(0.U(2.W))
  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  ackReg := io.rd || io.wr
  when(io.rd) {
    addrReg := io.address(3, 2)   // byte address -> which 32-bit counter
  }
  io.rdData := cntRegs(addrReg)

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  when (io.wr) {
    cntRegs(io.address(3, 2)) := io.wrData
  }

  io.ack := ackReg
}
```

Addressing four 32-bit counters needs **4 address bits**, not 2: addresses
count in *bytes*, while each counter is a 32-bit (4-byte) word, so the two
low address bits select a byte within a word and only the upper two bits
(`address(3, 2)`) select one of the four counters.

The counters themselves are a small **register file**: a `Reg` of a `Vec`,
initialized to all zeros by building a Scala `Seq` with `Seq.fill` (four
Chisel `0.U(32.W)` constants) and passing it to `VecInit`. Each counter is
free-running — it increments by one every cycle — except when a write
overwrites it that cycle.

`CounterDeviceTest` wraps the protocol in `read()`/`write()` helpers that poll
`ack` — a clean pattern for driving a pipelined interface from a test. `step`
takes a default argument, and `read` is a nested function closing over `dut`:

`src/test/scala/CounterDeviceTest.scala`
```scala
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
```

*Scala note — default arguments → [§C.7](../SCALA-NOTES.md#c7-default-arguments), nested (local) functions & closures → [§C.8](../SCALA-NOTES.md#c8-nested-local-functions--closures); string interpolation `s"…"` → [§J.5](../SCALA-NOTES.md#j5-string-interpolation-s).*

The same file also keeps the hand-written, "bit-banging" version of this test —
every pin poked and expected by hand — as `"CounterDevice" should "work"`. The
two are meant to be read side by side; [Chapter 13
§13.2–13.2.1](../ch13-debugging-testing-verification/README.md#132-testing-in-chisel)
uses exactly this pair to make the case for wrapping a protocol in functions.

### 12.3.3 The registered handshake

Between the two schemes above sits a third, and it is the one a first design
usually reaches for: keep the master holding its request, as the
combinational handshake does, but drive `ack` out of a **flip-flop** so nothing
combinational runs from the master, through address decoding, and back.

That single change buys the timing closure the combinational scheme costs. It
does *not* buy back the bus: the master must still keep `address` and `rd`/`wr`
asserted until the ack arrives, so a transfer occupies the request cycle plus
the ack cycle and nothing else can be issued meanwhile.

`src/main/scala/soc/CounterDevice.scala`
```scala
class CounterDeviceReg extends Module {
  val io = IO(new ReqAckIO(4))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.address(3, 2)
  val active = io.rd || io.wr

  val ackReg = RegInit(false.B)
  ackReg := active && !ackReg
  io.ack := ackReg

  // Read data is registered alongside the ack, so it is valid in the cycle the
  // master samples the ack.
  val dataReg = RegInit(0.U(32.W))
  dataReg := cntRegs(idx)
  io.rdData := dataReg
  ...
}
```

<p align="center">
  <img src="figures/reg-handshake.png" alt="Two reads over a registered handshake" width="600">
</p>

***Figure 12.5** — Two reads over a registered handshake, captured from
`CounterDeviceReg`. Grey marks a don't-care.*

The shape of the cost is right there. `rd` goes high in cycle 2 and stays high
until cycle 5, because the master cannot know when the ack is coming; `ack`
answers in cycle 3, and the second read's ack lands in cycle 5. Two transfers,
four cycles — and cycle 4, where the device is idle but the bus is not free, is
the price of holding the request. Compare Figure 12.4, where the same two reads
would occupy cycles 2 and 3 alone.

The `&& !ackReg` is where a first attempt goes wrong, and the reason is exactly
the property that defines the scheme. Because the master holds its request
*through* the ack cycle, a plain `ackReg := active` sees the same request still
asserted in that cycle and acknowledges it a second time — one command, two
acks. Guarding with `!ackReg` keeps the pulse one cycle wide. The same guard
appears on the write, so a held request cannot store its data twice:

`src/main/scala/soc/CounterDevice.scala`
```scala
  when(active && !ackReg && io.wr) {
    cntRegs(idx) := io.wrData
  }
```

In the generated code the acknowledgment is a flip-flop read out through an
`assign`, where the combinational device of Section 12.3.1 had a bare wire:

```systemverilog
      ackReg <= active & ~ackReg;
```
```systemverilog
  assign io_ack = ackReg;
```

And the cost is measurable. Holding the request high continuously, so the device
is never idle for want of work, it still answers only every other cycle:

`src/test/scala/HandshakeStylesTest.scala`
```scala
  "A registered ReqAckIO device" should "manage one transfer every two cycles" in {
    test(new CounterDeviceReg()) { dut =>
      // The request is held continuously and the device is never idle, yet it
      // can only answer every other cycle -- the cost of keeping the request
      // asserted through the ack cycle.
      assert(reqAckRate(dut.io, dut.clock, 12) == 6,
        "a registered device acks every other cycle")
    }
  }
```

This is not a strawman: it is what a **synchronous Wishbone slave** does, and
[Section 12.5](#wishbone) builds exactly this device again against the Wishbone
signal set, where Figure 12.7 shows its timing. Half the fix, in other words, is
what a real and widely used protocol settles for.

### 12.3.4 The three schemes compared

The three subsections above each built the same four counters behind the same
`ReqAckIO` port, changing only the handshake. This section puts them next to one
another, because the registered and pipelined schemes are easy to conflate —
both put a flip-flop in front of `ack` — and the difference between them matters
more than the similarity.

The two decisions the section opened with are the table's "`ack` comes from" and
"Master holds the request until `ack`?" rows. The first decides whether a
combinational path runs from the master, through address decoding and the slave,
and back into the master — the path that limits the clock frequency; the second
decides whether a second transaction can start before the first has finished.
Every other row is a consequence of those two:

| | Combinational | Registered | Pipelined |
|---|---|---|---|
| Built here as | `CounterDeviceComb` (§12.3.1) | `CounterDeviceReg` (§12.3.3) | `CounterDevice` (§12.3.2) |
| `ack` comes from | a wire off the request | a flip-flop | a flip-flop |
| Can `ack` land in the request cycle? | yes | no | no |
| Master holds the request until `ack`? | yes | yes | **no — one cycle** |
| Transactions in flight | one | one | **many** |
| Combinational path master→slave→master | **yes** | no | no |
| Measured throughput | 1 per cycle with no wait states, 1 per 3 with two | 1 per 2 cycles | **1 per cycle** |

The registered style answers question 1 and stops there. It removes the
combinational path — the master's `ack` input now comes straight out of a
flop — but the master is still required to keep address and command asserted
until that ack arrives, so the bus is occupied for the whole transaction and
nothing else can be issued meanwhile. A minimum-latency transfer costs the
request cycle plus the ack cycle: two cycles, half the throughput of the
combinational protocol at zero wait states. **Registered is half a fix.**

The pipelined style answers both. The command is a single-cycle pulse and the
master lets go of it immediately, so the slave can be handed a second command
while the first result is still on its way back. That is what makes one
transfer per cycle possible in Figure 12.4, and it is the only one of the three
that gets there. The price is bookkeeping: since `ack` no longer arrives while
the request that caused it is still being driven, it refers to *a command
issued some cycles ago*, and master and slave must agree on the order — which
is exactly why `ReqAckToWishbone` in Section 12.5 needs a state machine, and
why AXI eventually needs the transaction ids of
[Section 12.3.6](#1236-tagged-completion-answering-out-of-order).

The fourth combination — a combinational `ack` with a single-cycle command — is
degenerate rather than useful. If the master releases the request after one
cycle there is nothing left for a combinational `ack` to be a function of, so
the slave would have to answer within that one cycle, and there would be no
latency left to pipeline.

`src/test/scala/HandshakeStylesTest.scala` measures the bottom row by keeping
each slave maximally busy and counting completed transfers, so the throughputs
above are numbers the build checks rather than claims:

```scala
  "A pipelined slave" should "complete one transaction every cycle" in {
    test(new CounterDevice()) { dut =>
      val n = 6
      var acks = 0
      for (i <- 0 until n) {
        dut.io.address.poke(((i % 4) * 4).U)
        dut.io.rd.poke(true.B)
        dut.clock.step()
        if (dut.io.ack.peekBoolean()) acks += 1
      }
      dut.io.rd.poke(false.B)
      assert(acks == n, s"a pipelined slave sustains one ack per cycle, got $acks in $n")
    }
  }
```

### 12.3.5 Ready/valid: the two-sided handshake

Every scheme so far shares an asymmetry: the master decides when a transfer
starts, and the slave only decides when it ends. Nothing lets the slave say *not
yet, do not even start* — `ReqAckIO` has no wire for it, so a slave that cannot
take a command must take it anyway and withhold `ack`. The **ready/valid**
handshake of [Chapter 9](../ch09-communicating-state-machines/README.md#93-the-readyvalid-interface)
removes the asymmetry: the producer raises `valid` when it has data, the
consumer raises `ready` when it can accept data, and a transfer happens in
exactly those cycles where both are high — what `Decoupled` calls `fire`.

This is not a fourth row of the table above, because it answers a different
question. Req/ack is a **transaction** handshake: one request, one response, the
response bound to the request that caused it. Ready/valid is **flow control on
one unidirectional channel**, with no notion of a response at all — so the two
compose rather than compete, and a request/response protocol built on
ready/valid needs one channel per direction plus a rule for pairing them up.
Three properties follow, and all three matter later in the chapter:

- **Backpressure is symmetric.** Either side can stall the other by holding its
  flag low, so a slow consumer needs no separate "wait" signal.
- **`valid` must not depend combinationally on `ready`** (or the reverse) — two
  modules each waiting for the other's flag deadlock, and Chisel will not catch
  it for you.
- **A raised `valid` should not be withdrawn** before it is consumed. Chisel
  spells the stronger promise `IrrevocableIO`, and AXI requires it: once
  asserted, `VALID` stays asserted until the transfer completes.

Two of this chapter's own modules speak it directly. `RegFifo` (borrowed from
[Chapter 11](../ch11-example-designs/README.md)) is pure ready/valid on both
sides:

`src/main/scala/fifo/fifo.scala`
```scala
  io.enq.ready := !fullReg
  io.deq.valid := !emptyReg
```

And `MemMappedRV` ([Section 12.4](#124-memory-mapped-devices)) is the place the
two disciplines actually meet — pipelined req/ack facing the bus, ready/valid
facing the device:

`src/main/scala/soc/MemMappedRV.scala`
```scala
  statusReg := io.rx.valid ## io.tx.ready

  ackReg := io.mem.rd || io.mem.wr
  io.mem.ack := ackReg
```

Note what the bridge does *not* do: it never consults `io.tx.ready` before
acknowledging a write, so it does not translate backpressure into wait states —
it **exposes** it, as the two status bits software is expected to poll. That is
the whole reason [Section 12.4](#124-memory-mapped-devices) needs the TDRE/RDRF
status register, and the reason the no-deassert rule above is a correctness
requirement there rather than a style note: software that polls "ready", then
acts, must still find the condition true.

The third place ready/valid appears is the one that matters most in practice.
AXI applies it to **five independent channels**, so a transaction is no longer a
handshake at all — it is reassembled from five separate transfers, which is why
an AXI slave needs a state machine where a `ReqAckIO` slave needs none, and why
`AxiLiteCounter` must accept AW and W in either order
([Section 12.5](#axi)).

### 12.3.6 Tagged completion: answering out of order

The pipelined scheme of [Section 12.3.2](#1232-the-pipelined-handshake) lets many
commands be in flight, and pays with the bookkeeping named in
[Section 12.3.4](#1234-the-three-schemes-compared): master and slave must agree
on the order. That agreement is itself a cost. If responses must come back in
the order the commands went out, one slow command holds up every command behind
it however ready they are — **head-of-line blocking** — and the slave cannot use
the freedom the pipelining bought it.

The fix is to stop inferring which command a response belongs to and put the
answer on the wires: every command carries an **id**, every response carries the
id it belongs to, and the slave may then answer in whatever order it finishes.
This is the last question in the family — after *when may `ack` rise?* and *who
may stall whom?* comes **which transaction is this answer for?**

That is what AXI4's tag is for. `Axi4Addr`, `Axi4RdData`, and `Axi4WrResp` all
carry an `id`, and `Axi4OooReadMemory` — built and tested in
[the AXI4 appendix](APPENDIX-AXI4.md) — is a slave that uses it: a table of
`slots` accepted commands, each with an artificial `id * 4`-cycle delay standing
in for a bank conflict or a cache miss, and a picker that serves whichever one
finishes first:

`src/main/scala/axi4/Axi4.scala`
```scala
  // --- pick the next burst to serve ---------------------------------------
  val ready = VecInit((0 until slots).map(i => busyRegs(i) && delayRegs(i) === 0.U))
  when(!servingReg && ready.reduce(_ || _)) {
    servingReg := true.B
    slotReg := PriorityEncoder(ready)
  }
```

`src/test/scala/Axi4MemoryTest.scala` issues the slow command first and expects
the fast one back first, which is exactly the behaviour the ids exist to make
safe:

```scala
      sendAddr(dut.io.ar, dut.clock, id = 1, addr = 0, len = 0, burst = Axi4Burst.incr)
      sendAddr(dut.io.ar, dut.clock, id = 0, addr = 4, len = 0, burst = Axi4Burst.incr)

      dut.io.r.ready.poke(true.B)

      while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
      dut.io.r.bits.id.expect(0.U, "the fast request comes back first")
```

Three boundaries are worth being precise about:

- **Reordering is between transactions, not inside one.** AXI4 requires the
  beats of a burst to be contiguous — read data of different ids may not be
  interleaved (AXI3 allowed it and AXI4 dropped it) — so once
  `Axi4OooReadMemory` starts a burst it runs to its `last` beat before another
  id is served.
- **A tag says *which*, not *whether*.** Success is a separate field: the `resp`
  code on the B and R channels, `AxiResp.okay` / `exOkay` / `slvErr` / `decErr`
  in `src/main/scala/axi/AxiResp.scala`. Every slave in this chapter answers
  `okay`; a real one reports `decErr` for an address no slave claims.
- **Whether a write is answered at all is its own dimension.** AXI's B channel
  makes writes *non-posted* — the master gets a response — as does `wr`/`ack`
  here. A **posted** write gets none: PCIe posts memory writes and recovers
  ordering with explicit fences, trading the round trip for the loss of "the
  write has landed" as an observable event.

### 12.3.7 Handshakes this chapter does not build

The five schemes above are the ones this chapter has hardware for. They are not
the whole design space, and the families below are worth recognising by name
even though nothing here implements them — no code in this repository backs this
subsection, and the links are the reference.

- **Credit-based flow control.** Instead of a per-transfer stall signal, the
  receiver grants the sender a number of **credits** — free buffer slots. The
  sender spends one per beat and stops at zero; the receiver returns credits as
  it drains. The round trip leaves the stall path entirely, which is what makes
  it the scheme of choice when the link is long relative to the clock: PCIe,
  CXL, and InfiniBand all use it, as do virtual-channel routers in a
  network-on-chip. See Dally and Towles, *Principles and Practices of
  Interconnection Networks*, and the flow-control chapter of the
  [PCIe base specification](https://pcisig.com/specifications).
- **Asynchronous (clockless) handshakes.** This is where the word *handshake*
  comes from. With no clock, request and acknowledge are the only timing: a
  **4-phase** (return-to-zero) protocol takes both signals back down between
  transfers, a **2-phase** (transition-signalling) protocol treats every edge as
  an event and halves the transitions at the cost of harder logic. The data can
  be *bundled* (ordinary wires plus a matched delay) or *dual-rail* / 1-of-N,
  which encodes validity into the data and is delay-insensitive. Built out of
  Muller C-elements; see Sutherland's
  [Micropipelines](https://dl.acm.org/doi/10.1145/63526.63532) (CACM 1989) and
  Sparsø and Furber, *Principles of Asynchronous Circuit Design*.
- **Crossing clock domains.** Structurally the registered handshake of
  [Section 12.3.3](#1233-the-registered-handshake), except each side sees the
  other's signal through a two-flop synchronizer, so the full 4-phase sequence
  is mandatory: a pulse that is one cycle wide in the sending domain can be
  missed entirely in the receiving one. Bulk data usually skips the per-transfer
  handshake for an asynchronous FIFO with Gray-coded pointers (rocket-chip's
  `AsyncQueue`). See Cummings, *Clock Domain Crossing (CDC) Design &
  Verification Techniques Using SystemVerilog* (SNUG Boston 2008), in the
  [former Sunburst Design paper archive](https://www.paradigm-works.com/technical-library).
- **Retry, abort, and split responses.** Every scheme here assumes a slave that
  will eventually answer. A slave that cannot — a busy DRAM controller, a bridge
  whose far side is occupied — may instead be allowed to *refuse* and have the
  master reissue: AHB's `HRESP` has RETRY and SPLIT alongside ERROR, Wishbone
  adds `RTY_O` and `ERR_O` next to `ACK_O` (the bundle in
  `src/main/scala/wishbone/Wishbone.scala` implements only `ACK_O`), and PCI let
  a target disconnect mid-burst. The cost is that a transaction is no longer
  guaranteed to make progress, so the master needs a retry policy and the system
  needs an argument about why it terminates.

---

## 12.4 Memory-mapped devices

The devices here use the **pipelined** handshake of
[Section 12.3.2](#1232-the-pipelined-handshake): `MemMappedRV` drives
`ackReg := io.mem.rd || io.mem.wr`, so a single-cycle command is answered one
cycle later and the master never holds the bus. That is the minimum latency the
scheme allows, and the same shape as `CounterDevice`.

Devices share the address space; upper address bits are decoded to select one.
As part of the system design we must choose an address map — there is no one
right answer. An example memory map for a **16-bit** microcontroller (so
addresses run 0x0000–0xffff): the lowest addresses hold a read-only memory
(ROM) with the program to execute, followed by a writable RAM for data; all
IO devices are pushed to the top of the space (above 0xf000) so they stay out
of the way if the memory regions need to grow, with **16 bytes** reserved per
device. This is a made-up example — an address map has as much flexibility as
the designer wants:

| Address | Device |
|---------|--------|
| 0x0000–0x0fff | ROM |
| 0x1000–0x1fff | RAM |
| 0xf000 | UART |
| 0xf010 | LEDs |
| 0xf020 | Keys |

Some IO devices, like the counter above, expose ordinary registers. Others —
like a UART — expose a **ready/valid** interface instead (see the ready/valid
interface in [Chapter 9](../ch09-communicating-state-machines/README.md#93-the-readyvalid-interface),
and the UART shift registers in
[Chapter 6](../ch06-sequential-building-blocks/README.md)). The common
solution is to map the write and read channel onto one address (driving the
corresponding `valid`/`ready` on the write or read command), and map the two
flags into a **status register** at a different address so software can poll
before it reads or writes:

| Address | read | write |
|---------|------|-------|
| 0xf000 | status | control |
| 0xf001 | receive buffer | transmit buffer |

| Status bit | Meaning |
|-----------|---------|
| 0 (TDRE) | Transmit data register empty (ok to send) |
| 1 (RDRF) | Receive data register full (data to read) |

When the transmit data register is empty (TDRE) we can send new data; when
the receive data register is full (RDRF) we can read data. The terminology
sounds dated because it *is*: this is precisely the status-register mapping
of the first serial port of the IBM PC, built around the
[8250](https://en.wikipedia.org/wiki/8250_UART) UART chip — and it is still a
valid design today.

Polling a status register this way is only safe if, once asserted, `rx.valid`
and `tx.ready` are **not allowed to be deasserted again** before being
consumed — otherwise software could poll "ready", act on it, and find the
condition gone. If a device cannot guarantee that, insert a one-word buffer
(register) on each of the two ready/valid channels between the memory-mapped
interface and the device to restore the guarantee.

The memory-mapped device needs no new port: it reuses the `ReqAckIO` declared in
[Section 12.3](#123-handshake-schemes) at four address bits — with the pipelined
handshake of [Section 12.3.2](#1232-the-pipelined-handshake) behind it, as above
— so the whole chapter runs on one bus definition. Only `wrMask` goes unused here —
this device moves whole words between the bus and a byte stream, so there is no
sub-word write to mask.

`MemMappedRV` bridges that bus to a `Decoupled` (ready/valid) stream:
address 0 reads the status (`rx.valid ## tx.ready`), address 1 reads the receive
data / writes the transmit data:

`src/main/scala/soc/MemMappedRV.scala`
```scala
statusReg := io.rx.valid ## io.tx.ready
ackReg := io.mem.rd || io.mem.wr
io.mem.rdData := Mux(addrReg === 0.U, statusReg, io.rx.bits)
io.tx.bits := io.mem.wrData
io.tx.valid := io.mem.wr
```

Like `CounterDevice`, `MemMappedRV` is accessible with one cycle of latency
(the minimum under the pipelined handshake): it registers the read address
(`addrReg`) and delays `ack` by one cycle (`ackReg`).

**Simplification:** to keep the example small, a read always returns
`io.rx.bits` even if the receive channel has no valid data (`rx.valid` false),
and a write always asserts `tx.valid` even if the send buffer is full — instead
of stalling `ack` until the channel is actually ready. The example delegates
that check entirely to software, which is expected to read the status register
first and only read/write data once TDRE/RDRF say it is safe.

`UseMemMappedRV` connects it to a small `RegFifo` (tx → enq, deq → rx) as a
loopback, so `InterconnectTest` can write a value and read it back through the
status/data registers.

That `RegFifo` is carried over from [Chapter 11](../ch11-example-designs/README.md#112-generalized-fifos-readyvalid--inheritance),
and `src/main/scala/fifo/fifo.scala` here keeps just the two declarations it
needs — the port bundle and the abstract base:

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

`FifoIO` is a ready/valid port pair — an enqueue side (`Flipped`, so the FIFO
*receives*) and a dequeue side. `Fifo` is `abstract`: it fixes the interface and
the `depth` parameter, requires a sensible depth, and leaves the storage to a
subclass. `RegFifo` is the one implementation kept in this chapter; Chapter 11
develops four others against the same base. The payoff is exactly what this
chapter is about — because the buffer is behind a standard interface, the
memory-mapped device does not care which implementation sits behind it.

---

## 12.5 Bus and interface standards

Several point-to-point and bus standards have been proposed over the years;
the ready/valid discipline from [Chapter 9](../ch09-communicating-state-machines/README.md)
underlies most of them.

Each standard below picks one of the three schemes from Sections 12.3.1 to 12.3.3
and layers the ready/valid and tagging of
[Sections 12.3.5](#1235-readyvalid-the-two-sided-handshake)–[12.3.6](#1236-tagged-completion-answering-out-of-order)
on top, so it is worth naming them up front. Classic Wishbone is the **combinational**
handshake, and its synchronous variant is the **registered** one — the two
Wishbone slaves built here are the same devices as `CounterDeviceComb` and
`CounterDeviceReg`, wearing Wishbone's signal names. AXI is different in kind:
its `ready`/`valid` channels fix the "does the initiator hold?" question at
*yes*, so an AXI channel handshake is registered-style by construction, and what
varies is how many transactions a slave will accept at once.

### Wishbone

[Wishbone](https://en.wikipedia.org/wiki/Wishbone_(computer_bus)) is a
public-domain specification defining a point-to-point connection (not a bus in
the classic shared-wire sense), used by several open-source IP cores, but
still in the spirit of a microcomputer/backplane bus. This is not the best fit
for an SoC interconnect: Wishbone requires the **master** to hold address and
data valid for the *entire* read or write cycle. For a master whose data is
only valid a single cycle (as in the pipelined scheme), that means either registering
address/data *before* the Wishbone connection — costing an extra cycle of
latency — or an expensive multiplexer. A better fix is to register the
address and data **in the slave** instead, so address decoding happens in the
same cycle the address is registered. The mirror issue applies to the
**slave's** output data: since it is only valid for one cycle, a master that
doesn't sample it immediately must register it — so, by convention, the slave
should keep its last valid output held even after the Wishbone strobe
(`wb.stb`) is deasserted (holding data is normally free in hardware — it is
just a specification detail). The classic Wishbone specification has no
pipelined read or write; the newer **B4** specification adds a pipelined mode,
so a Wishbone system may now mix two specifications that are not necessarily
compatible with each other.

Wishbone names its signals from the **master's** point of view: `_O` is an
output of the master, `_I` an input. So `ADR_O` carries the address and `DAT_O`
the write data, while `DAT_I` is the read data coming back from the slave;
`WE_O` is the write enable (low = read, high = write), `CYC_O` indicates a bus
cycle is in progress, `STB_O` strobes the individual transfer, and `ACK_I` is
the slave's acknowledgment.

<p align="center">
  <img src="figures/wishbone.png" alt="Wishbone asynchronous read followed by an asynchronous write" width="600">
</p>

***Figure 12.6** — Wishbone asynchronous read followed by an asynchronous
write.*

The read occupies cycle 2 on its own: the master raises `CYC_O` and `STB_O`
with `WE_O` low and the address on `ADR_O`, and the slave answers **within that
same cycle** with `ACK_I` high and the read data on `DAT_I`. After two idle
cycles, the write in cycle 5 works the same way — `WE_O` high, write data on
`DAT_O` — and is again acknowledged combinationally inside the cycle. This is a
Wishbone slave responding asynchronously: one transfer per clock cycle, at the
price of the combinational path described in
[Section 12.3.1](#1231-the-combinational-handshake).

<p align="center">
  <img src="figures/wishbone-sync.png" alt="Wishbone synchronous read followed by a synchronous write" width="620">
</p>

***Figure 12.7** — Wishbone synchronous read followed by a synchronous write.*

With a synchronous (registered) slave the acknowledgment arrives on a clock edge
instead, so each transfer takes two cycles: the read is requested in cycle 2,
`ACK_I` is low there and goes high in cycle 3 together with `DAT_I`; the write
likewise spans cycles 5 and 6. Note what the master must do for this to work —
`ADR_O`, `CYC_O`, `STB_O`, and (on the write) `DAT_O` all stay valid across
**both** cycles, until the acknowledgment arrives. That is exactly the
requirement criticized above: a master whose address and data are valid for only
a single cycle cannot drive this directly, and needs them registered first.
Neither figure shows the B4 pipelined mode — both are the classic
specification, once with a combinational slave and once with a registered one,
and in both the next transfer cannot start until the current one is
acknowledged.

#### A Wishbone slave in Chisel

Building both slaves makes the difference between the two figures concrete. `WishboneIO` carries the signal set exactly as the figures label
it, declared from the **master's** side so that a slave can just flip it:

`src/main/scala/wishbone/Wishbone.scala`
```scala
class WishboneIO(addrWidth: Int) extends Bundle {
  val adr = Output(UInt(addrWidth.W))   // ADR_O
  val datWr = Output(UInt(32.W))        // DAT_O, master -> slave
  val datRd = Input(UInt(32.W))         // DAT_I, slave -> master
  val we = Output(Bool())               // WE_O:  high = write, low = read
  val sel = Output(UInt(4.W))           // SEL_O: active byte lanes
  val cyc = Output(Bool())              // CYC_O: a bus cycle is in progress
  val stb = Output(Bool())              // STB_O: this transfer is valid
  val ack = Input(Bool())               // ACK_I: slave terminates the transfer
}
```

The device behind it is deliberately the *same* four free-running loadable
counters as `CounterDevice` in [Section 12.3.2](#1232-the-pipelined-handshake), so
the protocol is the only thing that changes. The asynchronous slave of Figure
12.6 is almost entirely combinational:

`src/main/scala/wishbone/Wishbone.scala`
```scala
class WishboneCounter extends Module {
  val io = IO(Flipped(new WishboneIO(4)))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.adr(3, 2)                // byte address -> which 32-bit counter

  // A transfer is in progress only when CYC and STB are both asserted: CYC
  // frames the whole bus cycle, STB qualifies this individual transfer.
  val active = io.cyc && io.stb

  io.ack := active                      // combinational: same-cycle ack
  io.datRd := cntRegs(idx)

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  when(active && io.we) {
    cntRegs(idx) := io.datWr
  }
}
```

Two details are worth pausing on. `Flipped` reverses every direction in the
bundle at once, which is why a single `WishboneIO` written from the master's
view serves both sides of the wire — this is the same mechanism as `Flipped`
on a `Decoupled` in [Chapter 9](../ch09-communicating-state-machines/README.md).
And `active` is `cyc && stb` rather than `stb` alone: `CYC_O` frames a whole
bus cycle (which may span several transfers) while `STB_O` qualifies the
individual transfer, so a slave must look at both.

That slave answers immediately, which is the *best* case of the combinational
protocol rather than the one Figure 12.3 draws. To reproduce the figure the
device needs an access time — an ack that is still a wire, but not ready yet:

`src/main/scala/wishbone/Wishbone.scala`
```scala
class WishboneCounterWait(val waitStates: Int = 2) extends Module {
  require(waitStates >= 0, "waitStates cannot be negative")

  val io = IO(Flipped(new WishboneIO(4)))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.adr(3, 2)
  val active = io.cyc && io.stb

  // How many cycles the current transfer has been asking for.
  val waitReg = RegInit(0.U(math.max(1, log2Ceil(waitStates + 1)).W))
  val done = waitReg === waitStates.U

  io.ack := active && done              // combinational in the request
  io.datRd := cntRegs(idx)

  when(!active) {
    waitReg := 0.U                      // no transfer in progress
  }.elsewhen(!done) {
    waitReg := waitReg + 1.U            // still counting out the access time
  }.otherwise {
    waitReg := 0.U                      // acked this cycle; rearm for the next
  }
  ...
}
```

With `waitStates = 2` this is Figure 12.3 exactly. Set `waitStates = 0` and
`done` is always true, collapsing the module back into `WishboneCounter` above.

<p align="center">
  <img src="figures/wishbone-wait.png" alt="Combinational acknowledge with two wait states" width="580">
</p>

***Figure 12.8** — `WishboneCounterWait(2)`, captured from simulation. Read it
against Figure 12.3.*

Every feature of Figure 12.3 is there: the address is valid across cycles
2, 3, and 4; the request (`CYC_O` and `STB_O`, standing in for the figure's
`rd`) is held for all three because the master cannot know when the ack will
come; `ACK_I` rises only in cycle 4; and the read data is meaningful only in
that same cycle. The transaction takes three cycles to move one word.

The one visible difference is that Figure 12.3 draws `ack` as *undefined* until
part-way through cycle 2, whereas the capture shows a clean low. The figure
describes a signal settling combinationally after some propagation delay;
the real device drives `ack` from `active && done`, so with no request asserted
it is deterministically low. The capture is the more literal truth about this
hardware, and the figure is the more general statement about the protocol.

`DAT_I` reads 3 simply because the counters free-run from zero and three cycles
have elapsed.

The crucial point is that `ack` is still **combinational in the request**, even
though a register is involved. `waitReg` decides *when* the device is ready, but
`active` — this cycle's `cyc && stb` — decides whether the ack is asserted at
all. The generated code shows the difference between "a register is in the
module" and "a register is in the ack path":

```systemverilog
  wire             done = waitReg == 2'h2;
  wire             io_ack_0 = active & done;
```

`done` comes out of a flop but `io_ack_0` is a **wire**, so the path from the
master's `cyc`/`stb` to the master's `ack` input never passes through a
flip-flop. A test pins this down without any clock stepping at all:

`src/test/scala/HandshakeStylesTest.scala`
```scala
      // Withdrawing the request withdraws the ack in the *same* cycle, with no
      // clock step in between. A registered ack could not do this -- which is
      // the whole objection to the combinational handshake: this path runs from
      // the master, through address decoding and the slave, and back.
      dut.io.cyc.poke(false.B)
      dut.io.ack.expect(false.B, "ack tracks cyc within the cycle")
      dut.io.cyc.poke(true.B)
      dut.io.ack.expect(true.B, "and comes straight back")
```

The synchronous slave of Figure 12.7 differs only in that `ack` and the read
data are registered:

`src/main/scala/wishbone/Wishbone.scala`
```scala
class WishboneCounterSync extends Module {
  val io = IO(Flipped(new WishboneIO(4)))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.adr(3, 2)
  val active = io.cyc && io.stb

  // `&& !ackReg` keeps the ack a single cycle wide: the master holds cyc/stb
  // valid through the ack cycle too, and without this the still-active request
  // would be acknowledged a second time.
  val ackReg = RegInit(false.B)
  ackReg := active && !ackReg
  io.ack := ackReg
  ...
}
```

That `&& !ackReg` is the part a first attempt usually gets wrong. Because the
master keeps `CYC_O`/`STB_O` asserted *through* the acknowledgment cycle — look
again at Figure 12.7, where they span cycles 2 and 3 — a plain `ackReg :=
active` would see the request still active in cycle 3 and acknowledge it a
second time in cycle 4.

The generated SystemVerilog is the evidence that these three really are
different hardware. In `WishboneCounter.sv` the acknowledgment is a bare wire:

```systemverilog
  assign io_datRd = _GEN[io_adr[3:2]];
  assign io_ack = active;
```

while in `WishboneCounterSync.sv` it is a flip-flop, updated inside the clocked
block and read out through a separate `assign`:

```systemverilog
      ackReg <= active & ~ackReg;
      _GEN_0 = {{cntRegs_3}, {cntRegs_2}, {cntRegs_1}, {cntRegs_0}};
      dataReg <= _GEN_0[io_adr[3:2]];
```
```systemverilog
  assign io_ack = ackReg;
```

*(Generated blocks here have firtool's `// src/…` source-location comments
stripped; nothing else is changed.)*

`src/test/scala/WishboneCounterTest.scala` pins the two timings down against
the figures — it checks `ack` **without stepping the clock** after driving the
request, which passes only for the asynchronous slave, and checks that the
synchronous one answers `false` there and `true` one step later.

#### The cost of putting Wishbone under a pipelined master

The criticism the section opened with — that Wishbone makes the master hold
address and data valid for the whole transfer, so a master whose data lives for
one cycle needs a register, and "a register results in one additional cycle of
latency" — is a claim about cycle counts. A bridge makes it measurable.

`src/main/scala/wishbone/ReqAckToWishbone.scala`
```scala
  switch(state) {
    is(idle) {
      when(io.mem.rd || io.mem.wr) {
        addrReg := io.mem.address
        dataReg := io.mem.wrData
        selReg := io.mem.wrMask
        weReg := io.mem.wr
        state := transfer
      }
    }
    is(transfer) {
      when(io.wb.ack) {
        rdDataReg := io.wb.datRd
        state := respond
      }
    }
    // A separate cycle so the upstream `ack` is registered, not a combinational
    // function of the Wishbone `ack`. Returning it straight from `transfer`
    // would rebuild exactly the combinational path the pipelined scheme avoids --
    // and `rdDataReg` would not be valid yet anyway.
    is(respond) {
      io.mem.ack := true.B
      state := idle
    }
  }
```

`BridgedWishboneCounter` wires that bridge to `WishboneCounter`, giving a module
with the same `ReqAckIO(4)` port as the native `CounterDevice` — so the same
testbench routine can drive both and count the cycles between command and
`ack`:

`src/test/scala/ReqAckToWishboneTest.scala`
```scala
  "The Wishbone bridge" should "cost exactly one extra cycle of latency" in {
    var native = 0
    test(new CounterDevice()) { dut =>
      native = readLatency(dut.io, dut.clock, 0)._2
    }

    var bridged = 0
    test(new BridgedWishboneCounter()) { dut =>
      bridged = readLatency(dut.io, dut.clock, 0)._2
    }

    assert(native == 1, s"a native pipelined slave acks after one cycle, got $native")
    assert(bridged == native + 1,
      s"registering the command for Wishbone costs one cycle: $native -> $bridged")
  }
```

It passes: 1 cycle native, 2 through the bridge. The bridge also gives up
the pipelined scheme's back-to-back requests, since classic Wishbone has no
pipelining and
only one transfer can be in flight — the second cost of the mismatch, and the
reason the chapter prefers registering address and data *in the slave*.

### AXI

The Advanced Microcontroller Bus Architecture (AMBA), from ARM, defines three
buses: Advanced High-performance Bus (AHB), Advanced System Bus (ASB, the
predecessor of AHB, deprecated — it uses both clock phases, unusual for a
modern synchronous design), and Advanced Peripheral Bus (APB). AHB connects
on-chip memory, cache, and external memory to the processor; peripherals hang
off the lower-bandwidth APB via a bridge. An AHB transfer can complete in one
cycle with burst operation; an APB transfer takes **two cycles with no burst
mode**, and APB **v3** adds wait states to peripheral bus cycles.

AMBA AXI (Advanced eXtensible Interface) and **ACE version 4** are the latest
extension to AMBA. AXI adds out-of-order transaction completion via a 4-bit
transaction ID tag; a `ready` signal acknowledges the *start* of the
transaction, and the master must hold the transaction information (e.g. the
address) until the interconnect asserts `ready` — which gives up the elegant
single-cycle address phase of the original AHB. AXI applies ready/valid
handshaking across **all five channels**: read address, read data, write
address, write data, and write response. Decoupling write address from write
data this way requires a more complex slave able to accept the two in any
order.

#### AXI4-Lite in Chisel

**AXI4-Lite** is the subset of AXI4 with no bursts and no transaction IDs: one
data beat per address, one transaction at a time. It keeps all five channels,
so it is enough to show what the two claims above actually mean in hardware.
(The full protocol — bursts, IDs, out-of-order completion — is built and tested
in [the AXI4 appendix](APPENDIX-AXI4.md).)

Each channel is a plain `Decoupled`, which is why the ready/valid discipline
from [Chapter 9](../ch09-communicating-state-machines/README.md) is the whole
foundation of AXI rather than an analogy for it:

`src/main/scala/axilite/AxiLite.scala`
```scala
class AxiLiteIO(addrWidth: Int) extends Bundle {
  val aw = Decoupled(new AxiLiteAddr(addrWidth))    // write address
  val w = Decoupled(new AxiLiteWrData)              // write data
  val b = Flipped(Decoupled(new AxiLiteWrResp))       // write response
  val ar = Decoupled(new AxiLiteAddr(addrWidth))    // read address
  val r = Flipped(Decoupled(new AxiLiteRdData))     // read data
}
```

The three request channels point one way and the two response channels the
other, which is what the `Flipped` on `b` and `r` expresses — a master drives
AW/W/AR and receives B/R.

A read uses two of the five channels and shows the handshake at its simplest:

<p align="center">
  <img src="figures/axilite-read.png" alt="AXI4-Lite read transaction" width="620">
</p>

***Figure 12.9** — An AXI4-Lite read, captured from `AxiLiteCounter`. Grey marks
a don't-care: a channel's payload is only meaningful while its `VALID` is
asserted.*

The master presents `ARADDR` in cycle 2 and the slave accepts it in that same
cycle, since `ARREADY` was already high. The data appears on `RDATA` in cycle 3
— but the master is not ready until cycle 4, so the slave simply *holds* the
beat, and the transfer happens in cycle 4 where both `RVALID` and `RREADY` are
high. That is the entire ready/valid rule: a transfer occurs in exactly the
cycles where both are asserted, and either side may stall the other by
withholding its half. (`RDATA` reads 1 because the counters free-run from zero
and one cycle has elapsed.)

Now the consequence. Because AW and W are independent handshakes with no
ordering between them, a slave may see the address first, the data first, or
both in the same cycle, and must handle all three. It cannot simply wait for AW
and then read W: a master is entitled to present the data beat first, and a
slave that stalls waiting for the address it expects to come first will
deadlock against a master that is waiting to be relieved of its data. So each
channel gets its own holding register, and the write fires once both halves are
in:

`src/main/scala/axilite/AxiLite.scala`
```scala
  io.aw.ready := !awFullReg             // room for one address
  io.w.ready := !wFullReg               // room for one data beat

  when(io.aw.fire) {
    awIdxReg := io.aw.bits.addr(3, 2)
    awFullReg := true.B
  }
  when(io.w.fire) {
    wDataReg := io.w.bits.data
    wFullReg := true.B
  }

  // Both halves present (in whichever order they arrived) and the previous
  // response already taken: perform the write and raise the response.
  when(awFullReg && wFullReg && !bValidReg) {
    cntRegs(awIdxReg) := wDataReg
    awFullReg := false.B
    wFullReg := false.B
    bValidReg := true.B
  }
```

`fire` is `valid && ready` — the cycle a transfer actually happens. Note that
the slave never looks at which channel arrived first; it only asks whether both
are now present, which is exactly why either order works.

Both orders, captured from the same slave:

<p align="center">
  <img src="figures/axilite-write-aw-first.png" alt="AXI4-Lite write, address channel first" width="640">
</p>

***Figure 12.10** — A write with the address first: `AWADDR` in cycle 2, `WDATA`
in cycle 3, response in cycle 5.*

<p align="center">
  <img src="figures/axilite-write-w-first.png" alt="AXI4-Lite write, data channel first" width="660">
</p>

***Figure 12.11** — The same write with the data first: `WDATA` in cycle 2,
`AWADDR` only in cycle 4, response in cycle 6.*

Read the two together and the holding registers become visible as behaviour.
In Figure 12.11 the data beat is taken in cycle 2 and then `WREADY` goes low —
the slave's one data slot is full. For the next two cycles it has a data beat
and nowhere to put it, and crucially `BVALID` stays low: it does **not**
acknowledge a write it cannot yet perform. Only when `AWADDR` arrives in cycle
4 do both halves exist; cycle 5 shows `AWREADY` and `WREADY` both low while the
write happens, and the response follows in cycle 6.

Figure 12.10 is the same transaction with the channels swapped, and the slave
behaves symmetrically — `AWREADY` drops after cycle 2 instead. Neither ordering
is privileged, which is precisely the property the ids-free, two-channel write
path buys and the reason a slave cannot be written as "wait for AW, then read
W": a master presenting data first would deadlock against it.

The test drives the same write twice, once each way round:

`src/test/scala/AxiLiteCounterTest.scala`
```scala
  it should "accept the same write with the data first" in {
    test(new AxiLiteCounter()) { dut =>
      // The data beat arrives with no address in sight; the slave has to park
      // it until AW turns up.
      sendData(dut.io.w, dut.clock, 2000)
      dut.io.b.valid.expect(false.B, "no response until both halves have arrived")
      sendAddr(dut.io.aw, dut.clock, 4)

      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.io.b.bits.resp.expect(AxiResp.okay)
      dut.clock.step()
    }
  }
```

The `expect(false.B)` in the middle is the part that matters: it pins down that
the slave holds the data and stays silent rather than acknowledging a write it
has no address for yet.

### Open Core Protocol

Sonics Inc. defined the Open Core Protocol (OCP) as an open, freely available
standard, now maintained by the OCP International Partnership (OCP-IP). The
Patmos processor and the T-CREST multicore platform use OCP: the Patmos
repository contains memory controllers, peripheral devices, and a
network-on-chip, all with an OCP interface.

### Further Bus Specifications

**Avalon**, from Intel, is a system-on-a-programmable-chip interconnect
specification covering everything from a simple asynchronous static-RAM-style
interface to sophisticated pipelined transfers with variable latency. This
flexibility comes from the *Avalon Switch Fabric*, which translates between
the different interconnection styles and is generated by Intel's SOPC Builder
tool — but the switch fabric itself appears to be Intel-proprietary, tying the
specification to Intel FPGAs.

The **On-Chip Peripheral Bus (OPB)** is an open standard from IBM, used by
Xilinx for several years. It specifies a bus for multiple masters and slaves
without mandating an implementation — a distributed ring, a centralized
multiplexer, or a centralized AND/OR network are all suggested. Xilinx used
the AND/OR approach, which requires every inactive master and slave to drive
its data bus to zero; Xilinx has since moved all its interconnects to AXI.

---

## 12.6 Build, run, and check

```
$ sbt test
```

Expected tail (30 tests across 8 suites):

```
[info] Run completed in 3 seconds, 75 milliseconds.
[info] Total number of tests run: 30
[info] Suites: completed 8, aborted 0
[info] Tests: succeeded 30, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits twelve files into `generated/`:

| File | What it is |
|------|------------|
| `BusDecoder.sv` | address decoder + read mux, no handshaking (Figure 12.2) |
| `CounterDeviceComb.sv` | `ReqAckIO` device, combinational ack, 2 wait states (Section 12.3.1) |
| `CounterDeviceReg.sv` | the four counters, registered ack (Section 12.3.3) |
| `CounterDevice.sv` | the four counters, pipelined handshake (Section 12.3.2) |
| `UseMemMappedRV.sv` | the memory-mapped ready/valid bridge (Section 12.4) |
| `WishboneCounter.sv` | the same counters, asynchronous Wishbone slave (Figure 12.6) |
| `WishboneCounterWait.sv` | the Wishbone equivalent of `CounterDeviceComb` (Figure 12.8) |
| `WishboneCounterSync.sv` | the same counters, synchronous Wishbone slave (Figure 12.7) |
| `AxiLiteCounter.sv` | the same counters, AXI4-Lite slave |
| `BridgedWishboneCounter.sv` | `WishboneCounter` reached through the bridge |
| `Axi4Memory.sv` | burst-capable AXI4 memory ([appendix](APPENDIX-AXI4.md)) |
| `Axi4OooReadMemory.sv` | out-of-order AXI4 read memory ([appendix](APPENDIX-AXI4.md)) |

`BridgedWishboneCounter.sv` holds three modules — `ReqAckToWishbone`,
`WishboneCounter`, and the `BridgedWishboneCounter` that joins them. One
`emitVerilog` writes a whole hierarchy into one file, so the bridge is not
emitted separately.

---

## 12.7 Recap

- A classic microprocessor bus (Z80/6502-style) shares one tri-state data bus,
  needs no clock, and defines timing purely through peripheral access times;
  on-chip interconnect replaces the tri-state data bus with a **read mux**
  driven by the **address decoder**; connections are clocked.
- **Three acknowledgment schemes**, decided by two questions — is `ack` a wire
  or a flop, and does the master hold its request? **Combinational** = wire,
  held: single-cycle transfers, but decoding sits on the critical path.
  **Registered** = flop, still held: the path is gone but the bus is not, so it
  is half a fix. **Pipelined** = flop, released after one cycle: the only one
  that reaches back-to-back requests, at the cost of tracking which command an
  ack belongs to. Measured at 1 transfer per 3, per 2, and per cycle.
- **Two further schemes**, both built elsewhere in the chapter and both
  answering questions `ack` cannot. **Ready/valid** makes stalling symmetric —
  the receiver can refuse a transfer, which req/ack has no wire for — and is
  per-channel flow control rather than a transaction, so AXI needs five channels
  and a state machine to rebuild one transaction from it. **Tagged completion**
  puts an id on the command and the same id on the response, which removes the
  in-order requirement the pipelined scheme imposes and with it head-of-line
  blocking; `Axi4OooReadMemory` answers the later request first. Credit-based
  flow control, clockless 2-/4-phase handshakes, clock-domain crossings, and
  retry/split responses are named in
  [Section 12.3.7](#1237-handshakes-this-chapter-does-not-build) but not built.
- The pipelined scheme generalizes to point-to-point links through a switching
  fabric, with arbitration once there is more than one master; Patmos/OCP and
  `t-crest/soc-comm` use exactly this shape.
- **Memory-mapped** devices live in the shared address space; a status register
  exposes ready/valid flags for polling (mirroring the IBM PC's 8250 UART), and
  a bridge (`MemMappedRV`) maps a bus to a `Decoupled` stream.
- Standards (Wishbone, AXI, OCP, Avalon, OPB) formalize these ideas atop
  ready/valid, each with its own trade-offs around who holds data valid and for
  how long.
- **One device, seven implementations.** The same four counters appear behind
  every scheme and every protocol in the chapter, so the only variable is the
  interconnect: `CounterDeviceComb` / `CounterDeviceReg` / `CounterDevice` on
  `ReqAckIO`, three Wishbone slaves, and `AxiLiteCounter`. The table below lists
  them side by side, and `ReqAckToWishbone` measurably turns a 1-cycle pipelined
  read into a 2-cycle one.
- **AXI4-Lite** is AXI with the bursts and ids removed: five `Decoupled`
  channels, and a write path that must accept AW and W in either order. Full
  AXI4 is in [the appendix](APPENDIX-AXI4.md).

### Every module, side by side

[Section 12.3.4](#1234-the-three-schemes-compared) compares the three schemes on one
port. This table widens that to every module in the chapter, protocol slaves
included, each classified by its actual driver expression:

| Module | `ack` / `ready` driver | Source | Holds request? | Scheme | Throughput |
|---|---|---|---|---|---|
| `WishboneCounter` | `io.ack := active` | **wire** | yes | combinational, no wait states | 1 / cycle |
| `WishboneCounterWait(2)` | `io.ack := active && done` | **wire**, gated by a flop | yes | combinational + wait states | 1 / 3 cycles ✔ |
| `WishboneCounterSync` | `ackReg := active && !ackReg` | flop | yes | registered | 1 / 2 cycles ✔ |
| `CounterDevice` | `ackReg := io.rd \|\| io.wr` | flop | **no** — 1-cycle command | **pipelined** | 1 / cycle ✔ |
| `MemMappedRV` | `ackReg := rd \|\| wr` | flop | **no** | **pipelined** | 1 / cycle |
| `ReqAckToWishbone` (`mem` side) | `io.mem.ack` in the `respond` state | flop (FSM) | **no** | pipelined, 2-cycle latency ✔ | 1 / 3 cycles |
| `ReqAckToWishbone` (`wb` side) | consumes `io.wb.ack` | — | **yes** | a held-request *master* | — |
| `AxiLiteCounter` | `!awFullReg`, `!wFullReg`, `!rValidReg` | flop | yes (`valid` held) | registered-equivalent | 1 / 2 cycles ✔ |
| `Axi4Memory`, single beat | `wState === wIdle`, `rState === rIdle` | flop (FSM) | yes | registered-equivalent | 1 / 2 cycles ✔ |
| `Axi4Memory`, inside a burst | as above | flop (FSM) | yes | burst amortisation | ~1 beat / cycle ✔ |
| `Axi4OooReadMemory` | `hasFree` (from `busyRegs`) | flop | yes | 2 outstanding, out-of-order | 1 / 2 cycles ✔ |

✔ measured by `src/test/scala/HandshakeStylesTest.scala`; the rest follow by
inspection.

Three things the table makes visible that the individual sections do not:

**A register in the module is not a register in the ack path.**
`WishboneCounterWait` contains `waitReg`, but `active` — this cycle's
`cyc && stb` — still gates the ack, so the master→slave→master path never
crosses a flip-flop. Its generated code is a `wire`, not a `reg`.

**AXI slaves land on the registered scheme, for a different reason.** `Decoupled`
fixes one axis: a source always holds `valid` until `ready`. So the AXI question
is not "does the master hold?" but how many transactions the slave accepts at
once — and these accept one, so acceptance and completion serialise onto the
same 1-per-2-cycles the registered Wishbone slave manages. The difference is
that for Wishbone this is the protocol's ceiling, while for AXI it is a property
of *these slaves*: the protocol separates acceptance (`ARREADY`) from completion
(`RVALID`) precisely so a richer slave can overlap them.

**Bursts, not pipelining, are AXI's throughput story here.** One address
handshake amortised over eight beats reaches nearly one beat per cycle — the
only place in the chapter where a ready/valid device beats the request/acknowledge
ones. And `Axi4OooReadMemory`'s two slots buy *ordering freedom, not rate*: it
still measures 1 per 2 cycles, because `servingReg` leaves a dead cycle between
bursts (see [the appendix](APPENDIX-AXI4.md#a6-what-these-models-leave-out)).

---

## 12.8 Exercise

`BusDecoder` in Section 12.2 selects a device and routes its read data, but it
knows nothing about handshaking. Put the two halves together: wire two
`CounterDevice`s behind a `BusDecoder`, give each its own 16-byte window, and add
the missing piece — combining the devices' `ack` signals so the master sees one
acknowledgment from whichever device was selected. Then drive both from a test
through the `read`/`write` helpers and check that each window reaches its own
device.

**Also:** Take `MemMappedRV` with a streaming device connected to its
`rx`/`tx` ports and write a ChiselTest testbench for the memory interface.
Explore what happens if the test ignores the status flags — i.e. it reads
data while the receive channel is invalid, or writes while the transmit
channel isn't ready. Then modify `MemMappedRV` so `ack` is delayed until the
streaming device's `rx`/`tx` are actually ready/valid, and check whether your
testbench still works with the delayed `ack`. If simulating both the
streaming device and the memory interface starts to feel awkward in plain
Scala — needing two software state machines running "in parallel" — that is
exactly the problem multithreaded testing solves; see the
[testing chapter](../ch13-debugging-testing-verification/README.md).

---

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 11 — Example Designs](../ch11-example-designs/README.md)**.
Next: **[Chapter 13 — Debugging, Testing, and Verification](../ch13-debugging-testing-verification/README.md)**.
