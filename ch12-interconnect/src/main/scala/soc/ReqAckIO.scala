package soc

import chisel3._

// ReqAckIO: a request/acknowledge port for an on-chip interconnect, as seen
// from the slave.
//
// These are only wires -- an address, a read and a write strobe, the two data
// directions, a byte mask, and an acknowledgment -- and nothing about them
// fixes when `ack` may rise. The same seven signals carry a combinational
// handshake (drive `ack` from `rd`/`wr` within the request cycle), a registered
// one (hold the command until `ack` arrives), or a pipelined one equally well.
//
// Which of those a device implements is a property of the device, not of this
// bundle -- Sections 12.2 to 12.4 build the same four counters behind this one
// port under each scheme in turn.
class ReqAckIO(addrWidth: Int) extends Bundle {
  val address = Input(UInt(addrWidth.W))
  val rd = Input(Bool())
  val wr = Input(Bool())
  val rdData = Output(UInt(32.W))
  val wrData = Input(UInt(32.W))
  val wrMask = Input(UInt(4.W))
  val ack = Output(Bool())
}
