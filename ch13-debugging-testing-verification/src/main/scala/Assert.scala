import chisel3._

// Chisel `assert` states an assumption about the hardware. It is checked during
// simulation (the sim stops with a message if it fails) and is ignored during
// hardware generation.
class Assert extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  /* These two are NOT always true: an 8-bit add can overflow, so the sum can be
     smaller than an input. (This is the classic bug formal verification finds.)
  assert(io.sum >= io.a)
  assert(io.sum >= io.b)
   */
  assert(io.sum === io.a + io.b)
}
