import chisel3._
import chisel3.util._

// A one-word buffer with a ready/valid (DecoupledIO) interface on each side.
// A single emptyReg is a two-state Moore FSM (empty/full): `in.ready` and
// `out.valid` depend only on that state, so there is no combinational path
// from input to output. The input side is Flipped (DecoupledIO is defined from
// the sender's viewpoint).
class ReadyValidBuffer extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new DecoupledIO(UInt(8.W)))
    val out = new DecoupledIO(UInt(8.W))
  })

  val dataReg = Reg(UInt(8.W))
  val emptyReg = RegInit(true.B)

  io.in.ready := emptyReg
  io.out.valid := !emptyReg
  io.out.bits := dataReg

  when (emptyReg & io.in.valid) {   // accept a word when empty
    dataReg := io.in.bits
    emptyReg := false.B
  }

  when (!emptyReg & io.out.ready) { // word consumed -> empty again
    emptyReg := true.B
  }
}
