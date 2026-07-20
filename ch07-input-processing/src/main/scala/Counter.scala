import chisel3._

// A minimal counter reused from Chapter 6 — SyncReset (below) needs it.
abstract class Counter(n: Int) extends Module {
  val io = IO(new Bundle {
    val cnt = Output(UInt(8.W))
    val tick = Output(Bool())
  })
}

class WhenCounter(n: Int) extends Counter(n) {
  val N = (n - 1).U

  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  when(cntReg === N) {
    cntReg := 0.U
  }

  io.tick := cntReg === N
  io.cnt := cntReg
}
