import chisel3._
import chisel3.util._
import fifo._
import soc._

// An IO device with four free-running loadable counters, speaking the PipeCon
// pipelined interface. A read result arrives the cycle AFTER the command, so we
// register the address (addrReg) and delay the ack (ackReg).
class CounterDevice extends Module {
  val io = IO(new PipeCon(4))

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

// A memory-mapped register interface (simpler than PipeCon here).
class MemoryMappedIO extends Bundle {
  val address = Input(UInt(4.W))
  val rd = Input(Bool())
  val wr = Input(Bool())
  val rdData = Output(UInt(32.W))
  val wrData = Input(UInt(32.W))
  val ack = Output(Bool())
}

// Bridge a memory-mapped bus to a ready/valid (Decoupled) streaming device,
// like a UART. Address 0 = status register (tx-ready | rx-valid), address 1 =
// data (read = receive, write = transmit). Classic PC serial-port style.
class MemMappedRV[T <: Data](gen: T, block: Boolean = false) extends Module {
  val io = IO(new Bundle() {
    val mem = new MemoryMappedIO()
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
    val mem = new MemoryMappedIO()
  })

  val memDevice = Module(new MemMappedRV(gen))
  val fifo = Module(new RegFifo(gen, 3))
  memDevice.io.tx <> fifo.io.enq
  memDevice.io.rx <> fifo.io.deq
  io.mem <> memDevice.io.mem
}
