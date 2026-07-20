import chisel3._

// A one-shot timer: load a count with `load`, then count down to 0 and assert
// `done`. `load` has priority; at 0 the counter holds (the default 0 wins).
class Timer extends Module {
  val io = IO(new Bundle {
    val din = Input(UInt(8.W))
    val load = Input(Bool())
    val done = Output(Bool())
  })

  val din = io.din
  val load = io.load

  val cntReg = RegInit(0.U(8.W))
  val done = cntReg === 0.U

  val next = WireDefault(0.U)
  when (load) {
    next := din
  } .elsewhen (!done) {
    next := cntReg - 1.U
  }
  cntReg := next

  io.done := done
}
