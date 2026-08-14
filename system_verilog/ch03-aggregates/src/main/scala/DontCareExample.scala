import chisel3._

// Section 2.12 - DontCare.
class DontCareExample extends Module {
  val io = IO(new Bundle {
    val in   = Input(UInt(8.W))
    val used = Output(UInt(8.W))
    val out  = Output(UInt(8.W))
  })
  io.used := io.in
  io.out  := DontCare
}
