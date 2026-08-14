import chisel3._
import chisel3.util.RegEnable

// Section 1 - an enable flop. Synthesis tools recognize this shape and may
// convert it to a clock-gated register automatically.
class EnableFlop extends Module {
  val io = IO(new Bundle {
    val en  = Input(Bool())
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  io.out := RegEnable(io.in, 0.U(8.W), io.en)
}

// Explicit gating via a technology cell is a BlackBox - see Level C3.
// This wrapper shows the shape without depending on a real cell.
class GatedRegister extends Module {
  val io = IO(new Bundle {
    val en  = Input(Bool())
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val enReg = RegInit(0.U(8.W))
  when(io.en) { enReg := io.in }
  io.out := enReg
}
