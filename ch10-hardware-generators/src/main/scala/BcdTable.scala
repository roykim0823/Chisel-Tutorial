import chisel3._

// Generate a binary -> binary-coded-decimal (BCD) lookup table at ELABORATION
// time with a Scala for-loop. In VHDL/Verilog you'd write a separate script to
// emit this table; in Chisel the generator IS the hardware description.
class BcdTable extends Module {
  val io = IO(new Bundle {
    val address = Input(UInt(8.W))
    val data = Output(UInt(8.W))
  })

  val table = Wire(Vec(100, UInt(8.W)))

  // Convert binary i to BCD: tens digit in the upper nibble, ones in the lower.
  for (i <- 0 until 100) {
    table(i) := (((i / 10) << 4) + i % 10).U
  }

  io.data := table(io.address)
}
