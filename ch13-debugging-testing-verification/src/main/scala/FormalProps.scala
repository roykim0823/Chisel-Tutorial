import chisel3._
import chiseltest.formal.past

// §13.4: a property that spans cycles, and a bug that only a DEEP check finds.
// The counter must never decrease, which holds for 255 cycles and then fails
// when it wraps - so a shallow bounded check passes and a deep one refutes.
class MonotonicCounter extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
  })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.out := reg

  assert(io.out >= past(io.out), "the counter must never decrease")
}
