import chisel3._

// Generating combinational logic (ROM tables) with VecInit and Scala data.
class GenHardware extends Module {
  val io = IO(new Bundle {
    val data = Output(Vec(12, UInt(8.W)))
    val len = Output(UInt(8.W))
    val squareIn = Input(UInt(8.W))
    val squareOut = Output(UInt(8.W))
  })

  // A Scala String is a Seq[Char]; map each char to a UInt -> a ROM of bytes.
  val msg = "Hello World!"
  val text = VecInit(msg.map(_.U))
  val len = msg.length.U

  // A small square-lookup ROM.
  val n = io.squareIn
  val squareROM = VecInit(0.U, 1.U, 4.U, 9.U, 16.U, 25.U)
  val square = squareROM(n)

  io.data := text
  io.len := len
  io.squareOut := square
}
