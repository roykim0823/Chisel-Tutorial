package wildcat.pipeline

import chisel3._

// A read-only instruction memory preloaded at hardware-generation time from a
// Scala Array[Int]. It has a registered address (the fetch-stage pipeline
// register), so the instruction appears one clock cycle after the address.
class InstructionROM(code: Array[Int]) extends Module {
  val io = IO(Flipped(new InstrIO()))

  val addrReg = RegInit(0.U(32.W))
  addrReg := io.address
  val instructions = VecInit(code.toIndexedSeq.map(_.S(32.W).asUInt))
  io.data := instructions(addrReg(31, 2))   // word index = byte address / 4
  io.stall := false.B
}
