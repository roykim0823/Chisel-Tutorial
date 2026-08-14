import chisel3._

// Section 2.6 - Mux.
class MuxExample extends Module {
  val io = IO(new Bundle {
    val sel = Input(Bool())
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  io.out := Mux(io.sel, io.a, io.b)
}
