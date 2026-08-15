package soc

import chisel3._
import chisel3.util.log2Ceil

// Four free-running loadable counters behind a *pipelined* handshake: `rd`/`wr`
// are single-cycle commands the master does not hold, and the ack follows one
// cycle later. The read result therefore arrives the cycle AFTER the command, so
// the address is registered (addrReg) and the ack delayed (ackReg).
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

// The same four counters answering with a *combinational* acknowledgment: `ack`
// is a wire off the request, so the transfer ends inside the request cycle and
// the master must hold `rd`/`wr` until it sees the ack. `waitStates` is the
// device's access time; at 0 it answers immediately, and at 2 it reproduces the
// timing of Figure 12.3 on this chapter's own port.
//
// The price of the scheme is visible in the one line that matters: `active` is
// this cycle's `rd || wr`, so the path from the master's request, through
// address decoding, to the master's `ack` input never crosses a flip-flop.
class CounterDeviceComb(val waitStates: Int = 0) extends Module {
  require(waitStates >= 0, "waitStates cannot be negative")

  val io = IO(new ReqAckIO(4))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  val idx = io.address(3, 2)
  val active = io.rd || io.wr

  val waitReg = RegInit(0.U(math.max(1, log2Ceil(waitStates + 1)).W))
  val done = waitReg === waitStates.U

  io.ack := active && done              // combinational in the request
  io.rdData := cntRegs(idx)

  when(!active) {
    waitReg := 0.U                      // no transfer in progress
  }.elsewhen(!done) {
    waitReg := waitReg + 1.U            // still counting out the access time
  }.otherwise {
    waitReg := 0.U                      // acked this cycle; rearm
  }

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  when(io.ack && io.wr) {
    cntRegs(idx) := io.wrData
  }
}

// The same four counters with a *registered* acknowledgment: `ack` comes out of
// a flip-flop, so it cannot land in the request cycle, and the master still has
// to hold `rd`/`wr` until it arrives. That removes the combinational path but
// not the serialisation -- every transfer costs the request cycle plus the ack
// cycle.
//
// `&& !ackReg` keeps the ack one cycle wide. Because the master holds its
// request through the ack cycle too, a plain `ackReg := active` would see the
// same request still asserted and acknowledge it a second time.
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

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  // The write lands in the first cycle of the transfer, the ack in the second.
  when(active && !ackReg && io.wr) {
    cntRegs(idx) := io.wrData
  }
}
