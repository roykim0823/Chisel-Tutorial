package soc

import chisel3._
import chisel3.util._
import fifo._

// Bridge a memory-mapped bus to a ready/valid (Decoupled) streaming device,
// like a UART. Address 0 = status register (tx-ready | rx-valid), address 1 =
// data (read = receive, write = transmit). Classic PC serial-port style.
//
// It speaks the pipelined handshake over the same `ReqAckIO` port as
// `CounterDevice`: a single-cycle `rd`/`wr`, answered one cycle later by
// `ackReg`. `wrMask` goes unused -- this device moves whole words between the
// bus and a byte stream, so there is no sub-word write to mask.
class MemMappedRV[T <: Data](gen: T, block: Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val mem = new ReqAckIO(4)
    val tx = Decoupled(gen)
    val rx = Flipped(Decoupled(gen))
  })

  val statusReg = RegInit(0.U(2.W))
  val ackReg = RegInit(false.B)
  val addrReg = RegInit(0.U(1.W))
  val rdDlyReg = RegInit(false.B)

  statusReg := io.rx.valid ## io.tx.ready

  ackReg := io.mem.rd || io.mem.wr
  io.mem.ack := ackReg

  when (io.mem.rd) {
    addrReg := io.mem.address
  }
  rdDlyReg := io.mem.rd
  io.rx.ready := false.B
  when (addrReg === 1.U && rdDlyReg) {
    io.rx.ready := true.B
  }
  io.mem.rdData := Mux(addrReg === 0.U, statusReg, io.rx.bits)

  io.tx.bits := io.mem.wrData
  io.tx.valid := io.mem.wr
}

// Wire the memory-mapped device to a small FIFO whose deq feeds rx and whose
// enq is fed by tx — a loopback so we can test the bridge.
class UseMemMappedRV[T <: Data](gen: T) extends Module {
  val io = IO(new Bundle() {
    val mem = new ReqAckIO(4)
  })

  val memDevice = Module(new MemMappedRV(gen))
  val fifo = Module(new RegFifo(gen, 3))
  memDevice.io.tx <> fifo.io.enq
  memDevice.io.rx <> fifo.io.deq
  io.mem <> memDevice.io.mem
}
