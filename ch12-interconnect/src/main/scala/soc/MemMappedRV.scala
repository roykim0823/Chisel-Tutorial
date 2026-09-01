package soc

import chisel3._
import chisel3.util._

// Bridge a memory-mapped bus to a ready/valid (Decoupled) streaming device,
// like a UART.
//
// The device owns a 16-byte window of the address map and lays four 32-bit
// registers into it, addressed exactly as `CounterDevice` addresses its
// counters: `address(3, 2)` picks the word, and the low two bits are the byte
// within it.
//
//   0x0  read: status (RDRF, TDRE)     write: control (rx/tx interrupt enable)
//   0x4  read: receive data, pops rx   write: transmit data, pushes tx
//   0x8  read: words received          write: --
//   0xc  read: 0                       write: --
//
// It speaks the pipelined handshake over the same `ReqAckIO` port as
// `CounterDevice`: a single-cycle `rd`/`wr`, answered one cycle later by
// `ackReg`. `wrMask` goes unused -- this device moves whole words between the
// bus and a byte stream, so there is no sub-word write to mask.
class MemMappedRV[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle() {
    val mem = new ReqAckIO(4)
    val tx = Decoupled(gen)
    val rx = Flipped(Decoupled(gen))
    val irq = Output(Bool())
  })

  // The register map above, as named constants: these are `address(3, 2)`
  // values, so byte offset 0x0 is word 0, 0x4 is word 1 and 0x8 is word 2.
  // They are Chisel literals rather than Scala Ints so they can be used
  // directly in `===` and as `switch`/`is` arms. Being constants they
  // elaborate away -- the generated Verilog compares `idxReg` against 2'h0,
  // 2'h1 and 2'h2, and holds no state for them.
  private val status = 0.U(2.W)
  private val data = 1.U(2.W)
  private val count = 2.U(2.W)

  val statusReg = RegInit(0.U(2.W))
  val ctrlReg = RegInit(0.U(2.W))
  val rxCountReg = RegInit(0.U(32.W))
  val ackReg = RegInit(false.B)
  val idxReg = RegInit(0.U(2.W))
  val rdDlyReg = RegInit(false.B)

  val idx = io.mem.address(3, 2)      // byte address -> which of the four words

  statusReg := io.rx.valid ## io.tx.ready

  ackReg := io.mem.rd || io.mem.wr
  io.mem.ack := ackReg

  when (io.mem.rd) {
    idxReg := idx
  }
  rdDlyReg := io.mem.rd

  // Reading the data word pops one item off rx, in the same cycle the master is
  // handed the value. Reading status or count consumes nothing.
  io.rx.ready := rdDlyReg && idxReg === data
  when (io.rx.fire) {
    rxCountReg := rxCountReg + 1.U
  }

  io.mem.rdData := 0.U
  switch (idxReg) {
    is (status) { io.mem.rdData := statusReg }
    is (data) { io.mem.rdData := io.rx.bits.asUInt }
    is (count) { io.mem.rdData := rxCountReg }
  }

  // Only a write to the data word transmits; a write to the control word stores
  // the interrupt enables instead.
  io.tx.bits := io.mem.wrData.asTypeOf(io.tx.bits)
  io.tx.valid := io.mem.wr && idx === data
  when (io.mem.wr && idx === status) {
    ctrlReg := io.mem.wrData
  }

  // The control bits mask the status bits: bit 0 raises an interrupt when there
  // is room to send, bit 1 when there is data to read.
  io.irq := (statusReg & ctrlReg).orR
}

// Wire the memory-mapped device to a small FIFO whose deq feeds rx and whose
// enq is fed by tx -- a loopback so we can test the bridge. `Queue` is Chisel's
// standard ready/valid FIFO, so the chapter needs no FIFO of its own.
class UseMemMappedRV[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle() {
    val mem = new ReqAckIO(4)
    val irq = Output(Bool())
  })

  val memDevice = Module(new MemMappedRV(gen))
  memDevice.io.rx <> Queue(memDevice.io.tx, 3)  // three-deep FIFO.
  io.mem <> memDevice.io.mem
  io.irq := memDevice.io.irq
}
