import chisel3._
import chisel3.util._

// One module showing three shift-register uses.
class ShiftRegister extends Module {
  val io = IO(new Bundle {
    val din = Input(UInt(1.W))
    val dout = Output(UInt(1.W))
    val serIn = Input(UInt(1.W))
    val paraOut = Output(UInt(4.W))
    val d = Input(UInt(4.W))
    val load = Input(Bool())
    val serOut = Output(UInt(1.W))
  })

  val din = io.din

  // (1) Simple shift register: a 4-cycle delay from din to dout.
  val shiftReg = Reg(UInt(4.W))
  shiftReg := shiftReg(2, 0) ## din
  val dout = shiftReg(3)

  val serIn = io.serIn

  // (2) Serial-in, parallel-out: shift in from the MSB, read the whole word.
  val outReg = RegInit(0.U(4.W))
  outReg := serIn ## outReg(3, 1)
  val q = outReg

  val d = io.d
  val load = io.load

  // (3) Parallel-in, serial-out: load a word, then shift it out bit by bit.
  val loadReg = RegInit(0.U(4.W))
  when (load) {
    loadReg := d
  } otherwise {
    loadReg := 0.U ## loadReg(3, 1)
  }
  val serOut = loadReg(0)

  io.serOut := serOut
  io.paraOut := q
  io.dout := dout
}
