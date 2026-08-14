import chisel3._

// Section 2 - the latch that Chisel will not let you build.
// Uncomment the body of BrokenLatch to see the generation-time error.
class BrokenLatch extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out  = Output(UInt(8.W))
  })
  // val w = Wire(UInt(8.W))
  // when(io.cond) { w := 3.U }     // no default, no .otherwise -> error
  // io.out := w
  io.out := 0.U
}

// The three ways to make it complete.
class DefaultFirst extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out  = Output(UInt(8.W))
  })
  val w = Wire(UInt(8.W))
  w := 0.U                      // default first
  when(io.cond) { w := 3.U }
  io.out := w
}

class WireDefaultForm extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out  = Output(UInt(8.W))
  })
  val w = WireDefault(0.U(8.W)) // default folded into the declaration
  when(io.cond) { w := 3.U }
  io.out := w
}

class OtherwiseForm extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out  = Output(UInt(8.W))
  })
  val w = Wire(UInt(8.W))
  when(io.cond) { w := 3.U } .otherwise { w := 0.U }   // every path assigns
  io.out := w
}
