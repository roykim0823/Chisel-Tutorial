import chisel3._

// A free-running counter: register + adder, wraps 0..9 (Mux form).
class Count100 extends Module {
  val io = IO(new Bundle {
    val cnt = Output(UInt(8.W))
  })

  val cntReg = RegInit(0.U(8.W))
  cntReg := Mux(cntReg === 9.U, 0.U, cntReg + 1.U)

  io.cnt := cntReg
}

// A common interface for the counter variants below (counts 0..n-1, ticks at top).
abstract class Counter(n: Int) extends Module {
  val io = IO(new Bundle {
    val cnt = Output(UInt(8.W))
    val tick = Output(Bool())
  })
}

// Count up, reset with a `when` at the maximum.
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

// Same behavior, expressed with a multiplexer.
class MuxCounter(n: Int) extends Counter(n) {
  val N = (n - 1).U

  val cntReg = RegInit(0.U(8.W))
  cntReg := Mux(cntReg === N, 0.U, cntReg + 1.U)

  io.tick := cntReg === N
  io.cnt := cntReg
}

// Count down from N to 0 and reload.
class DownCounter(n: Int) extends Counter(n) {
  val N = (n - 1).U

  val cntReg = RegInit(N)
  cntReg := cntReg - 1.U
  when(cntReg === 0.U) {
    cntReg := N
  }

  io.tick := cntReg === 0.U
  io.cnt := cntReg
}

// A function that *returns* a counter — a lightweight generator.
class FunctionCounter(n: Int) extends Counter(n) {

  // This function returns a counter
  def genCounter(n: Int) = {
    val cntReg = RegInit(0.U(8.W))
    cntReg := Mux(cntReg === n.U, 0.U, cntReg + 1.U)
    cntReg
  }

  // now we can easily create many counters
  val count10 = genCounter(10)
  val count99 = genCounter(99)

  // and one more for testing
  val testCounter = genCounter(n - 1)
  io.tick := testCounter === (n - 1).U
  io.cnt := testCounter
}

// The "nerd" counter: count from N-2 down to -1 so only the sign bit is checked.
class NerdCounter(n: Int) extends Counter(n) {
  val N = n

  val MAX = (N - 2).S(8.W)
  val cntReg = RegInit(MAX)
  io.tick := false.B

  cntReg := cntReg - 1.S
  when(cntReg(7)) {   // sign bit set => reached -1
    cntReg := MAX
    io.tick := true.B
  }

  io.cnt := cntReg.asUInt
}
