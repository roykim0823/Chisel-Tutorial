import chisel3._
import chisel3.util.experimental.BoringUtils

// Identical to the chapter's TickGen: only `tick` is exposed.
class TickGen extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
  })

  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  io.tick := cntReg === 9.U
  when(io.tick) {
    cntReg := 0.U
  }
}

// The test-only wrapper, using the CURRENT BoringUtils API: `bore(source)`
// returns the bored-out value, so it reads like an ordinary connection. The
// chapter's Chisel 6 version calls the older `bore(source, Seq(sink))` form,
// which still exists in Chisel 7 but is deprecated.
class TickGenTestTop extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
    val counter = Output(UInt(8.W))
  })

  val tickGen = Module(new TickGen)
  io.tick := tickGen.io.tick
  io.counter := BoringUtils.bore(tickGen.cntReg)
}
