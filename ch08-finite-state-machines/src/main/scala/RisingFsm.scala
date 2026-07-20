import chisel3._
import chisel3.util._

// A Mealy-type rising-edge detector: only two states. The output is set inside
// the next-state logic (on the zero->one transition), so it depends on the
// input as well as the state — that combinational input-to-output path is what
// makes it a Mealy machine.
class RisingFsm extends Module {
  val io = IO(new Bundle {
    val din = Input(Bool())
    val risingEdge = Output(Bool())
  })

  object State extends ChiselEnum {
    val zero, one = Value
  }
  import State._

  val stateReg = RegInit(zero)

  // default output value
  io.risingEdge := false.B

  // Next-state AND output logic together.
  switch (stateReg) {
    is(zero) {
      when(io.din) {
        stateReg := one
        io.risingEdge := true.B   // pulse on the 0 -> 1 transition
      }
    }
    is(one) {
      when(!io.din) {
        stateReg := zero
      }
    }
  }
}
