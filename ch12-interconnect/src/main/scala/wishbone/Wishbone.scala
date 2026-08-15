package wishbone

import chisel3._
import chisel3.util.log2Ceil

// Wishbone signals, named as in the specification and in Figures 12.5 and 12.6:
// from the *master's* point of view, `_O` is an output of the master and `_I` an
// input. A slave therefore declares `Flipped(new WishboneIO(n))`.
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

// The same four free-running loadable counters as `CounterDevice`, but behind a
// classic Wishbone slave that answers *asynchronously* (Figure 12.5): `ack` and
// the read data are combinational functions of the request, so a transfer takes
// a single clock cycle -- and the address decoding sits on the critical path.
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

// The device Figure 12.3 actually draws: a combinational acknowledgment that is
// not ready straight away.
//
// `ack` is still a wire from the request -- no flip-flop stands between
// `cyc`/`stb` and `ack` -- but it only rises once the device has had
// `waitStates` cycles to do its work. So the master has to keep the request
// asserted until it sees the ack, which is why `address`/`rd` stay high across
// cycles 2, 3, and 4 in that figure.
//
// With `waitStates = 0` the `done` term is always true and this collapses
// exactly into `WishboneCounter` above: same-cycle ack, single-cycle transfer.
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

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  // The transfer completes in the ack cycle, so that is when the write lands.
  when(io.ack && io.we) {
    cntRegs(idx) := io.datWr
  }
}

// The same device with a *synchronous* (registered) slave, Figure 12.6: `ack`
// is a register, so it rises one cycle after the request and every transfer
// takes two cycles. The combinational path from `cyc`/`stb`/`adr` back to the
// master is gone, which is the whole point -- the price is the extra cycle.
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

  // Read data is registered alongside the ack, so it is valid in the same cycle
  // the master samples the ack.
  val dataReg = RegInit(0.U(32.W))
  dataReg := cntRegs(idx)
  io.datRd := dataReg

  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }
  // The write lands in the first cycle of the transfer, the ack follows in the
  // second; `!ackReg` stops the held request from writing twice.
  when(active && !ackReg && io.we) {
    cntRegs(idx) := io.datWr
  }
}
