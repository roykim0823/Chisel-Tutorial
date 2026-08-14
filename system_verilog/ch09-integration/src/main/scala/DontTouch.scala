import chisel3._

// Section 3 - keeping a signal alive for probing and constraints.
class Probed extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val vanishes = io.a & io.b            // inlined away
  val survives = dontTouch(WireInit(io.a | io.b))  // kept
  io.out := vanishes | survives
}
