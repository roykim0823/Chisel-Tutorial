package soc

import chisel3._

// PipeCon: a simple pipelined on-chip interconnect interface (as seen from the
// slave). `rd`/`wr` are single-cycle commands; `ack` is asserted one or more
// cycles later, which decouples the slave from a same-cycle combinational path
// and allows back-to-back (pipelined) requests.
class PipeCon(private val addrWidth: Int) extends Bundle {
  val address = Input(UInt(addrWidth.W))
  val rd = Input(Bool())
  val wr = Input(Bool())
  val rdData = Output(UInt(32.W))
  val wrData = Input(UInt(32.W))
  val wrMask = Input(UInt(4.W))
  val ack = Output(Bool())
}
