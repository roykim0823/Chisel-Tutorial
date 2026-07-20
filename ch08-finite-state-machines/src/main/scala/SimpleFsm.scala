import chisel3._
import chisel3.util._

// A Moore-type alarm FSM: green -> orange -> red on bad events, back to green
// on clear. Output (ringBell) depends only on the current state (red).
class SimpleFsm extends Module {
  val io = IO(new Bundle {
    val badEvent = Input(Bool())
    val clear = Input(Bool())
    val ringBell = Output(Bool())
  })

  // The three states (ChiselEnum gives symbolic, binary-encoded state names).
  object State extends ChiselEnum {
    val green, orange, red = Value
  }
  import State._

  // The state register, reset to green.
  val stateReg = RegInit(green)

  // Next state logic: a switch over the state, with input-guarded transitions.
  switch (stateReg) {
    is (green) {
      when(io.badEvent) {
        stateReg := orange
      }
    }
    is (orange) {
      when(io.badEvent) {
        stateReg := red
      } .elsewhen(io.clear) {
        stateReg := green
      }
    }
    is (red) {
      when (io.clear) {
        stateReg := green
      }
    }
  }

  // Output logic: Moore output depends only on the state.
  io.ringBell := stateReg === red
}
