# Chapter 12 — Interconnect

Larger systems are built by connecting components, and **interconnect** defines
how. This chapter starts from the classic microprocessor bus, adapts it to an
on-chip "bus" (multiplexers instead of tri-state), adds handshaking for devices
with variable latency (a **pipelined** `PipeCon` interface), builds a memory-
mapped IO device bridging a bus to a ready/valid stream, and surveys the
standards (Wishbone, AXI).

*Conventions: every file path is relative to `tutorial/ch12-interconnect/`, and
every command is run from that folder.*

## What's in this project

```
ch12-interconnect/
├── build.sbt · project/build.properties
├── figures/
├── src/main/scala/
│   ├── soc/PipeCon.scala      the pipelined interconnect interface (Bundle)
│   ├── fifo/fifo.scala        a small RegFifo (dependency of the bridge)
│   ├── interconnect.scala     CounterDevice + memory-mapped RV bridge
│   └── Generate.scala
└── src/test/scala/
    ├── CounterDeviceTest.scala
    └── InterconnectTest.scala
```

---

## 12.1 From a classic bus to an on-chip bus

A classic microcomputer connects the CPU to memory and I/O over shared address,
data, and control buses, using **tri-state** drivers on the bidirectional data
bus and an **address decoder** driving chip-select (CS) lines.

<p align="center">
  <img src="figures/bus.png" alt="A classic computer bus" width="480">
</p>

***Figure 12.1** — A CPU, memory, and I/O on shared address/data/control buses.*

On-chip, tri-state buses are impractical, so we **split** the data bus into
separate write-out and read-in wires and use a **multiplexer** (selected by the
address decoder) for the read path. Connections are clocked.

<p align="center">
  <img src="figures/bus-on-chip.png" alt="The on-chip bus" width="520">
</p>

***Figure 12.2** — On-chip: a read mux replaces the tri-state data bus; the
decoder drives both the chip selects and the mux.*

---

## 12.2 Handshaking: the pipelined PipeCon interface

For devices with varying latency you need a **handshake**: the CPU issues a
`rd`/`wr` command; the device replies with `ack`. A *combinational* (same-cycle)
ack (as in classic Wishbone) allows single-cycle transactions but puts decoding
+ the device on the critical path. A **pipelined** handshake instead lets `ack`
come **one or more cycles later**, so the command is valid for just one cycle and
requests can be issued **back-to-back** (1 word/cycle throughput):

`src/main/scala/soc/PipeCon.scala`
```scala
class PipeCon(private val addrWidth: Int) extends Bundle {
  val address = Input(UInt(addrWidth.W))
  val rd = Input(Bool())
  val wr = Input(Bool())
  val rdData = Output(UInt(32.W))
  val wrData = Input(UInt(32.W))
  val wrMask = Input(UInt(4.W))
  val ack = Output(Bool())
}
```

---

## 12.3 An example IO device

`CounterDevice` implements `PipeCon`: four free-running 32-bit counters you can
read and load. Because the read result arrives the cycle *after* the command
(and the command is valid only that cycle), it **registers the address**
(`addrReg`) and **delays the ack** (`ackReg`):

`src/main/scala/interconnect.scala`
```scala
class CounterDevice extends Module {
  val io = IO(new PipeCon(4))
  val ackReg = RegInit(false.B)
  val addrReg = RegInit(0.U(2.W))
  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))

  ackReg := io.rd || io.wr
  when(io.rd) { addrReg := io.address(3, 2) }
  io.rdData := cntRegs(addrReg)

  for (i <- 0 until 4) { cntRegs(i) := cntRegs(i) + 1.U }
  when (io.wr) { cntRegs(io.address(3, 2)) := io.wrData }
  io.ack := ackReg
}
```

`CounterDeviceTest` wraps the protocol in `read()`/`write()` helpers that poll
`ack` — a clean pattern for driving a pipelined interface from a test.

---

## 12.4 Memory-mapped devices

Devices share the address space; upper address bits are decoded to select one.
An example memory map for a 16-bit microcontroller:

| Address | Device |
|---------|--------|
| 0x0000–0x0fff | ROM |
| 0x1000–0x1fff | RAM |
| 0xf000 | UART |
| 0xf010 | LEDs |
| 0xf020 | Keys |

A streaming device (like a UART) has ready/valid channels rather than
registers. The convention maps them to addresses plus a **status register** for
polling:

| Address | read | write |
|---------|------|-------|
| 0xf000 | status | control |
| 0xf001 | receive buffer | transmit buffer |

| Status bit | Meaning |
|-----------|---------|
| 0 (TDRE) | Transmit data register empty (ok to send) |
| 1 (RDRF) | Receive data register full (data to read) |

`MemMappedRV` bridges a memory-mapped bus to a `Decoupled` (ready/valid) stream:
address 0 reads the status (`rx.valid ## tx.ready`), address 1 reads the receive
data / writes the transmit data:

`src/main/scala/interconnect.scala`
```scala
statusReg := io.rx.valid ## io.tx.ready
ackReg := io.mem.rd || io.mem.wr            // delayed ack (pipelined)
io.mem.rdData := Mux(addrReg === 0.U, statusReg, io.rx.bits)
io.tx.bits := io.mem.wrData
io.tx.valid := io.mem.wr
```

`UseMemMappedRV` connects it to a small `RegFifo` (tx → enq, deq → rx) as a
loopback, so `InterconnectTest` can write a value and read it back through the
status/data registers.

> **Standards (survey):** *Wishbone* is a public-domain point-to-point standard
> (classic same-cycle handshake, plus a newer pipelined mode). *AXI* (ARM AMBA)
> uses independent ready/valid channels for read/write address and data, with
> transaction IDs for out-of-order completion. The ready/valid discipline from
> Chapter 9 underlies all of them.

---

## 12.5 Build, run, and check

```
$ sbt test
```

Expected tail (2 tests):

```
[info] Tests: succeeded 2, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits `CounterDevice.sv` and `UseMemMappedRV.sv`.

---

## 12.6 Recap

- On-chip interconnect replaces the tri-state data bus with a **read mux** driven
  by the **address decoder**; connections are clocked.
- A **pipelined handshake** (`PipeCon`: single-cycle command, delayed `ack`)
  avoids a same-cycle combinational path and allows back-to-back requests.
- **Memory-mapped** devices live in the shared address space; a status register
  exposes ready/valid flags for polling, and a bridge maps a bus to a
  `Decoupled` stream.
- Standards (Wishbone, AXI) formalize these ideas atop ready/valid.

## 12.7 Exercise

Add a second device to the bus with a different address range, an address
decoder that generates chip selects, and a read multiplexer selecting the active
device's `rdData` — then drive both from a test through the `read`/`write`
helpers.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 11 — Example Designs](../ch11-example-designs/README.md)**.
Next: **[Chapter 13 — Debugging, Testing, and Verification](../ch13-debugging-testing-verification/README.md)**.
