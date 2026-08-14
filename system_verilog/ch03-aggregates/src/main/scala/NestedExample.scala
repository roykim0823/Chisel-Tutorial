import chisel3._

// Section 2.8 - nested Bundles and flattening.
class Nested extends Bundle {
  val x = UInt(8.W)
  val y = UInt(8.W)
}

class NestedExample extends Module {
  val io = IO(new Bundle {
    val in  = Input(new Nested)
    val out = Output(new Nested)
  })
  io.out.x := io.in.x + 1.U
  io.out.y := io.in.y - 1.U
}
