import chisel3._
import chisel3.util._

// The same rising-edge detector as a Moore machine. It needs a THIRD state
// (puls) to emit the one-cycle pulse, because a Moore output depends only on
// the state. The output rises one cycle after the input edge and is exactly one
// clock wide.
class RisingMooreFsm extends Module {
  val io = IO(new Bundle {
    val din = Input(Bool())
    val risingEdge = Output(Bool())
  })

  object State extends ChiselEnum {
    val zero, puls, one = Value
  }
  import State._

  val stateReg = RegInit(zero)

  // Next state logic
  switch (stateReg) {
    is(zero) {
      when(io.din) {
        stateReg := puls
      }
    }
    is(puls) {
      when(io.din) {
        stateReg := one
      } .otherwise {
        stateReg := zero
      }
    }
    is(one) {
      when(!io.din) {
        stateReg := zero
      }
    }
  }

  // Output logic: Moore output depends only on the state.
  io.risingEdge := stateReg === puls
}
