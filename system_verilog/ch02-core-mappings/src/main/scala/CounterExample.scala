import chisel3._

// Section 2.2 - Register.
// Named CounterExample here so it cannot be confused with chisel3.util.Counter;
// the emitted Verilog module name follows the class name.
class CounterExample extends Module {
  val io = IO(new Bundle {
    val count = Output(UInt(8.W))
  })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.count := reg
}
