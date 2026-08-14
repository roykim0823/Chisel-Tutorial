import chisel3._

// Section 2 - multiple clock domains via withClock.
class TwoClocks extends Module {
  val io = IO(new Bundle {
    val clkB = Input(Clock())
    val inA  = Input(UInt(8.W))
    val inB  = Input(UInt(8.W))
    val outA = Output(UInt(8.W))
    val outB = Output(UInt(8.W))
  })
  // Implicit clock domain.
  val regA = RegNext(io.inA)
  io.outA := regA

  // A second domain, explicitly clocked.
  withClock(io.clkB) {
    val regB = RegNext(io.inB)
    io.outB := regB
  }
}
