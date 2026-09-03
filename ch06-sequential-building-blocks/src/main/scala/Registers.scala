import chisel3._
import chisel3.util._

// Every register form of Section 6.1, each one wired to an output port so a
// test (and the generated SystemVerilog) can observe it.
class Registers extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val delayIn = Input(UInt(4.W))
    val inVal = Input(UInt(4.W))
    val enable = Input(Bool())
    val din = Input(Bool())
    val out = Output(UInt(8.W))
    val delayOut = Output(UInt(4.W))
    val enableOut = Output(UInt(4.W))
    val enableOut2 = Output(UInt(4.W))
    val resetEnableOut = Output(UInt(4.W))
    val resetEnableOut2 = Output(UInt(4.W))
    val risingEdgeOut = Output(Bool())
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

  val delayIn = io.delayIn
  val inVal = io.inVal
  val enable = io.enable
  val din = io.din

  // --- Defined and connected in two steps, with no initial value ---
  val delayReg = Reg(UInt(4.W))
  delayReg := delayIn

  io.delayOut := delayReg

  // --- A register with an enable, spelled out with when ---
  val enableReg = Reg(UInt(4.W))
  when(enable) {
    enableReg := inVal
  }

  // The same thing in one line: RegEnable's second parameter is the enable.
  val enableReg2 = RegEnable(inVal, enable)

  io.enableOut := enableReg
  io.enableOut2 := enableReg2

  // --- Enable plus reset: RegInit with the same when ---
  val resetEnableReg = RegInit(0.U(4.W))
  when(enable) {
    resetEnableReg := inVal
  }

  // The three-parameter RegEnable does both: input, init value, enable.
  val resetEnableReg2 = RegEnable(inVal, 0.U(4.W), enable)

  io.resetEnableOut := resetEnableReg
  io.resetEnableOut2 := resetEnableReg2

  // --- An anonymous register inside an expression: rising-edge detection ---
  val risingEdge = din & !RegNext(din)

  io.risingEdgeOut := risingEdge
}
