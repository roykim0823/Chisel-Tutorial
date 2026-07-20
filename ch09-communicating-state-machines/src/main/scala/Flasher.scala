import chisel3._
import chisel3.util._

// A light flasher: on `start`, flash the light 3 times (on 6 cycles, off 4
// cycles between flashes), then wait. A single 27-state FSM would be unwieldy,
// so we FACTOR it into communicating FSMs.

// Shared interface so one test can drive both versions.
class FlasherBase extends Module {
  val io = IO(new Bundle {
    val start = Input(UInt(1.W))
    val light = Output(UInt(1.W))
  })
}

// Version 1: a master FSM (6 states) communicating with a timer FSM.
class Flasher extends FlasherBase {

  val start = io.start.asBool

  object State extends ChiselEnum {
    val off, flash1, space1, flash2, space2, flash3 = Value
  }
  import State._

  val stateReg = RegInit(off)
  val light = WireDefault(false.B)          // FSM output

  // Signals connecting the master FSM to the timer FSM.
  val timerLoad = WireDefault(false.B)      // start timer
  val timerSelect = WireDefault(true.B)     // 6 or 4 cycles
  val timerDone = Wire(Bool())

  timerLoad := timerDone

  // Master FSM
  switch(stateReg) {
    is(off) {
      timerLoad := true.B
      timerSelect := true.B
      when (start) { stateReg := flash1 }
    }
    is (flash1) {
      timerSelect := false.B
      light := true.B
      when (timerDone) { stateReg := space1 }
    }
    is (space1) {
      when (timerDone) { stateReg := flash2 }
    }
    is (flash2) {
      timerSelect := false.B
      light := true.B
      when (timerDone) { stateReg := space2 }
    }
    is (space2) {
      when (timerDone) { stateReg := flash3 }
    }
    is (flash3) {
      timerSelect := false.B
      light := true.B
      when (timerDone) { stateReg := off }
    }
  }

  // Timer FSM: a down counter loaded with 5 or 3.
  val timerReg = RegInit(0.U)
  timerDone := timerReg === 0.U

  when(!timerDone) {
    timerReg := timerReg - 1.U
  }
  when (timerLoad) {
    when (timerSelect) {
      timerReg := 5.U
    } .otherwise {
      timerReg := 3.U
    }
  }

  io.light := light
}

// Version 2: factor the repeated flash/space states into a SECOND counter FSM,
// reducing the master FSM to three states (off, flash, space).
class Flasher2 extends FlasherBase {

  val start = io.start.asBool

  object State extends ChiselEnum {
    val off, flash, space = Value
  }
  import State._

  val stateReg = RegInit(off)
  val light = WireDefault(false.B)

  val timerLoad = WireDefault(false.B)
  val timerSelect = WireDefault(true.B)
  val timerDone = Wire(Bool())
  // Flash-count FSM connection
  val cntLoad = WireDefault(false.B)
  val cntDecr = WireDefault(false.B)
  val cntDone = Wire(Bool())

  timerLoad := timerDone

  switch(stateReg) {
    is(off) {
      timerLoad := true.B
      timerSelect := true.B
      cntLoad := true.B
      when (start) { stateReg := flash }
    }
    is (flash) {
      timerSelect := false.B
      light := true.B
      when (timerDone & !cntDone) { stateReg := space }
      when (timerDone & cntDone) { stateReg := off }
    }
    is (space) {
      cntDecr := timerDone
      when (timerDone) { stateReg := flash }
    }
  }

  // Down-counter FSM for the remaining flashes (loaded with 2 for 3 flashes).
  val cntReg = RegInit(0.U)
  cntDone := cntReg === 0.U

  when(cntLoad) { cntReg := 2.U }
  when(cntDecr) { cntReg := cntReg - 1.U }

  // Timer FSM (unchanged from version 1).
  val timerReg = RegInit(0.U)
  timerDone := timerReg === 0.U

  when(!timerDone) {
    timerReg := timerReg - 1.U
  }
  when (timerLoad) {
    when (timerSelect) {
      timerReg := 5.U
    } .otherwise {
      timerReg := 3.U
    }
  }

  io.light := light
}
