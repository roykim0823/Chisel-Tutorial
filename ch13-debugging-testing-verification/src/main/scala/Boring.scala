import chisel3._
import chisel3.util.experimental.BoringUtils

// A tick generator that deliberately exposes ONLY `tick` — the internal counter
// is hidden (good design practice).
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

// A test-only wrapper that uses BoringUtils.bore to "bore" a connection from the
// hidden cntReg out to a new `counter` port — without editing TickGen. This
// lets a test observe internal state; the tool adds the needed ports for us.
class TickGenTestTop extends Module {
  val io = IO(new Bundle {
    val tick = Output(Bool())
    val counter = Output(UInt(8.W))
  })

  val tickGen = Module(new TickGen)
  io.tick := tickGen.io.tick
  io.counter := DontCare
  BoringUtils.bore(tickGen.cntReg, Seq(io.counter))
}
