import chisel3._

// Section 2.1 - Module and Bundle.
class Adder(width: Int) extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(width.W))
    val b   = Input(UInt(width.W))
    val sum = Output(UInt(width.W))
  })
  io.sum := io.a + io.b
}
