import chisel3._

// The four basic register forms.
class Registers extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  // A register that resets to 0.
  val reg = RegInit(0.U(8.W))

  val d = io.in
  // Connect an input and read the output just by name.
  reg := d
  val q = reg

  // RegNext: a register whose input is d (defined and connected in one step).
  val nextReg = RegNext(d)

  // RegNext with a reset value.
  val bothReg = RegNext(d, 0.U)

  io.out := reg
}
