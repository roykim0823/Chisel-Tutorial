import chisel3._
import chisel3.util._

// Section 2.11 - ChiselEnum and state machines.
object State extends ChiselEnum {
  val sIdle, sRun, sDone = Value
}

class Fsm extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
  })
  import State._
  val state = RegInit(sIdle)
  switch(state) {
    is(sIdle) { when(io.start) { state := sRun } }
    is(sRun)  { state := sDone }
    is(sDone) { state := sIdle }
  }
  io.done := state === sDone
}
