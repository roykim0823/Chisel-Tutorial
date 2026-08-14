import chisel3._
import chisel3.experimental.Analog

// Section 5 - the only way to get an `inout` port out of Chisel.
// Analog has no value semantics: you cannot read or drive it in Chisel,
// only pass it through to a BlackBox that knows what to do with it.
class AnalogPort extends Module {
  val io = IO(new Bundle {
    val pad = Analog(1.W)
    val obs = Output(Bool())
  })
  io.obs := false.B
}
