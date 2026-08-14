import chisel3._

// Section 2 - assert and stop.
class AssertExample extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val sum = Output(UInt(9.W))
  })
  io.sum := io.a +& io.b

  // An immediate assertion: checked every cycle in simulation.
  assert(io.sum >= io.a, "widening add must not lose the carry")
}

class StopExample extends Module {
  val io = IO(new Bundle { val done = Output(Bool()) })
  val cnt = RegInit(0.U(8.W))
  cnt := cnt + 1.U
  io.done := cnt === 10.U
  when(cnt === 10.U) { stop() }
}
