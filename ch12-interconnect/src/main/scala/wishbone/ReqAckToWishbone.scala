package wishbone

import chisel3._
import chisel3.util._
import soc.ReqAckIO

// Bridge a pipelined ReqAckIO master (the processor side) to a Wishbone slave.
//
// This is the mismatch the chapter's Wishbone section describes. A pipelined
// master drives
// `address`/`wrData` for exactly one clock cycle; Wishbone requires the master
// to hold ADR_O/DAT_O/CYC_O/STB_O valid for the *whole* transfer, until ACK_I.
// The only way to reconcile them is to capture the command in registers -- and
// that register is what costs the extra cycle of latency.
//
// Only one transfer is in flight at a time: classic Wishbone has no pipelining,
// so the bridge also gives up the pipelined scheme's back-to-back requests. It
// ignores a command arriving while a transfer is still running, which is safe
// here because the master must wait for `ack` before it reuses the bus.
class ReqAckToWishbone(addrWidth: Int) extends Module {
  val io = IO(new Bundle {
    val mem = new ReqAckIO(addrWidth)        // slave side, faces the processor
    val wb = new WishboneIO(addrWidth)      // master side, faces the device
  })

  val idle :: transfer :: respond :: Nil = Enum(3)
  val state = RegInit(idle)

  val addrReg = RegInit(0.U(addrWidth.W))
  val dataReg = RegInit(0.U(32.W))
  val selReg = RegInit(0.U(4.W))
  val weReg = RegInit(false.B)
  val rdDataReg = RegInit(0.U(32.W))

  io.wb.adr := addrReg
  io.wb.datWr := dataReg
  io.wb.sel := selReg
  io.wb.we := weReg
  io.wb.cyc := state === transfer
  io.wb.stb := state === transfer

  io.mem.rdData := rdDataReg
  io.mem.ack := false.B

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
}

// The bridge wired to the asynchronous Wishbone counter device: the same four
// counters as `WishboneCounter`, but reached over a `ReqAckIO` port instead of
// a Wishbone one. Lets the whole path be driven from one testbench, and emitted
// as one design.
class BridgedWishboneCounter extends Module {
  val io = IO(new ReqAckIO(4))

  val bridge = Module(new ReqAckToWishbone(4))
  val device = Module(new WishboneCounter())

  bridge.io.wb <> device.io
  io <> bridge.io.mem
}
