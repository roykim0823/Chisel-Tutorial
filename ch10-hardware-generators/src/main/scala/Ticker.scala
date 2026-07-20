import chisel3._

// Inheritance: an abstract base fixes the interface; subclasses implement it
// different ways. One generic tester can then drive them all (see TickerTest).
abstract class Ticker(n: Int) extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
  })
}

// Tick generation by counting up.
class UpTicker(n: Int) extends Ticker(n) {
  val N = (n - 1).U
  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  val tick = cntReg === N
  when(tick) {
    cntReg := 0.U
  }
  io.tick := tick
}

// Tick generation by counting down to 0.
class DownTicker(n: Int) extends Ticker(n) {
  val N = (n - 1).U
  val cntReg = RegInit(N)
  cntReg := cntReg - 1.U
  when(cntReg === 0.U) {
    cntReg := N
  }
  io.tick := cntReg === N
}

// The "nerd" version: count down to -1 to avoid a comparator.
class NerdTicker(n: Int) extends Ticker(n) {
  val N = n
  val MAX = (N - 2).S(8.W)
  val cntReg = RegInit(MAX)
  io.tick := false.B
  cntReg := cntReg - 1.S
  when(cntReg(7)) {
    cntReg := MAX
    io.tick := true.B
  }
}
