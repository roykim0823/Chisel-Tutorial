import chisel3._

// Section 2.9 - SInt and signed arithmetic.
class SignedExample extends Module {
  val io = IO(new Bundle {
    val a   = Input(SInt(8.W))
    val b   = Input(SInt(8.W))
    val gt  = Output(Bool())
    val shr = Output(SInt(8.W))
  })
  io.gt  := io.a > io.b
  io.shr := io.a >> 2
}
