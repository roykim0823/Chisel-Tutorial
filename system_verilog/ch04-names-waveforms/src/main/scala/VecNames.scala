import chisel3._

// Section 2.3 - how a Vec of registers appears in the SV and in a waveform.
class VecNames extends Module {
  val io = IO(new Bundle {
    val idx  = Input(UInt(2.W))
    val din  = Input(UInt(8.W))
    val wr   = Input(Bool())
    val dout = Output(UInt(8.W))
  })
  val bank = Reg(Vec(4, UInt(8.W)))
  when(io.wr) { bank(io.idx) := io.din }
  io.dout := bank(io.idx)
}
