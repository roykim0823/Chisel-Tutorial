import chisel3._

// Section 2.4 - Wire.
class WireExample extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val mid = Wire(UInt(8.W))
  mid := io.a + io.b
  io.out := mid << 1
}
