import chisel3._

// Section 2.7 - Vec (hardware array) with a dynamic index.
class VecExample extends Module {
  val io = IO(new Bundle {
    val idx  = Input(UInt(2.W))
    val data = Input(Vec(4, UInt(8.W)))
    val out  = Output(UInt(8.W))
  })
  io.out := io.data(io.idx)
}
