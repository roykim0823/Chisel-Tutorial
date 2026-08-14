import chisel3._

// Section 1.1 - Chisel vals whose names collide with Verilog keywords.
// All of these are legal Scala/Chisel and all get renamed by firtool.
class Keywords extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val reg    = RegNext(io.in)      // `reg` is a Verilog keyword
  val wire   = io.in ^ 0xFF.U      // `wire` is a Verilog keyword
  val output = RegNext(wire)       // `output` is a Verilog keyword
  io.out := reg | output
}
