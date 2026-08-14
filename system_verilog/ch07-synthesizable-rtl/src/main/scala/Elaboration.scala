import chisel3._

// Section 4 - SV `generate` vs Chisel elaboration.
// A Scala for-loop builds a chain of registers; the loop itself does not
// survive into the SystemVerilog, only the hardware it produced.
class DelayChain(stages: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val regs = Seq.fill(stages)(RegInit(0.U(8.W)))
  regs.head := io.in
  for (i <- 1 until stages) {
    regs(i) := regs(i - 1)
  }
  io.out := regs.last
}

// Elaboration-time `if` chooses WHAT to build - there is no runtime cost and
// no trace of the condition in the output.
class Configurable(withBypass: Boolean) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  if (withBypass) {
    io.out := io.in                 // no register at all
  } else {
    io.out := RegNext(io.in, 0.U)
  }
}
