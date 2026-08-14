import chisel3._

// Section 2.5 - when / .elsewhen / .otherwise.
class WhenExample extends Module {
  val io = IO(new Bundle {
    val sel = Input(UInt(2.W))
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val result = Wire(UInt(8.W))
  when(io.sel === 0.U) {
    result := io.a
  }.elsewhen(io.sel === 1.U) {
    result := io.b
  }.otherwise {
    result := 0.U
  }
  io.out := result
}
