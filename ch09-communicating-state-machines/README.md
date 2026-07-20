# Chapter 9 — Communicating State Machines

Some problems are too complex for a single FSM. The fix is to **factor** the
design into several smaller FSMs that communicate through signals — one FSM's
output is another's input. This chapter builds a light flasher two ways
(factoring a 27-state machine down to three states), then combines an FSM with a
**datapath** (an FSMD) to compute a popcount, and finishes with the
**ready/valid** handshake interface (`DecoupledIO`) used to move data between
subsystems.

*Conventions: every file path is relative to
`tutorial/ch09-communicating-state-machines/`, and every command is run from
that folder.*

## What's in this project

```
ch09-communicating-state-machines/
├── build.sbt · project/build.properties
├── figures/
├── src/main/scala/
│   ├── Flasher.scala           Flasher (master + timer) and Flasher2 (+ counter)
│   ├── PopulationCount.scala   FSMD: PopCountFSM + PopCountDataPath + top level
│   ├── ReadyValidBuffer.scala  a one-word buffer with DecoupledIO
│   └── Generate.scala
└── src/test/scala/
    ├── FlasherTest.scala · PopulationCountTest.scala · ReadyValidBufferTest.scala
```

---

## 9.1 A light flasher

Spec: on a one-cycle `start`, flash the light **three times** — on for 6 cycles,
off for 4 cycles between flashes — then wait. A direct FSM needs **27 states**
(1 idle + 3×6 on + 2×4 off). Instead, factor it: a **master FSM** does the
flashing logic, a **timer FSM** does the waiting.

<p align="center">
  <img src="figures/flasher.png" alt="Flasher split into a master FSM and a timer FSM" width="360">
</p>

***Figure 9.1** — The flasher as two communicating FSMs. The master drives
`timerLoad`/`timerSelect`; the timer reports `timerDone`.*

The timer is a down-counter loaded with 5 or 3 (for the 6- or 4-cycle
intervals), asserting `timerDone` at 0:

`src/main/scala/Flasher.scala`
```scala
val timerReg = RegInit(0.U)
timerDone := timerReg === 0.U

when(!timerDone) { timerReg := timerReg - 1.U }
when (timerLoad) {
  when (timerSelect) { timerReg := 5.U } .otherwise { timerReg := 3.U }
}
```

The master FSM (`Flasher`) still has six states — `flash1/2/3` and `space1/2`
are near-duplicates. Factoring the *flash count* into a **second counter FSM**
reduces the master to three states (`off`, `flash`, `space`):

<p align="center">
  <img src="figures/flasher2.png" alt="Flasher split into master, timer, and counter FSMs" width="420">
</p>

***Figure 9.2** — Adding a flash-count FSM shrinks the master to three states.*

`src/main/scala/Flasher.scala`
```scala
switch(stateReg) {
  is(off) {
    timerLoad := true.B; timerSelect := true.B; cntLoad := true.B
    when (start) { stateReg := flash }
  }
  is (flash) {
    timerSelect := false.B
    light := true.B
    when (timerDone & !cntDone) { stateReg := space }
    when (timerDone & cntDone)  { stateReg := off }
  }
  is (space) {
    cntDecr := timerDone
    when (timerDone) { stateReg := flash }
  }
}
```

`Flasher2` is more configurable — changing the interval lengths or the number of
flashes needs no FSM changes, only the load constants. Both versions share
`FlasherBase`, so one test drives them identically.

---

## 9.2 A state machine with a datapath (FSMD)

A very common pattern: an FSM **controls** a datapath that does the
**computation**. Here we compute a **popcount** (number of set bits).

<p align="center">
  <img src="figures/popcnt-fsmd.png" alt="A state machine with a datapath" width="420">
</p>

***Figure 9.3** — FSMD: the FSM handles control + ready/valid handshakes; the
datapath does the work; control/status signals connect them.*

<p align="center">
  <img src="figures/popcnt-states.png" alt="State diagram for the popcount FSM" width="420">
</p>

***Figure 9.4** — The control FSM: `idle → count → done`.*

The FSM only sequences and handshakes; it never touches data:

`src/main/scala/PopulationCount.scala`
```scala
switch(stateReg) {
  is(idle) {
    io.dinReady := true.B
    when(io.dinValid) { io.load := true.B; stateReg := count }
  }
  is(count) { when(io.done) { stateReg := done } }
  is(done)  {
    io.popCntValid := true.B
    when(io.popCntReady) { stateReg := idle }
  }
}
```

<p align="center">
  <img src="figures/popcnt-data.png" alt="Datapath for the popcount circuit" width="420">
</p>

***Figure 9.5** — The datapath: shift right, add the LSB to an accumulator, and
count down until all bits are processed.*

`src/main/scala/PopulationCount.scala`
```scala
dataReg := 0.U ## dataReg(7, 1)          // shift right
popCntReg := popCntReg + dataReg(0)      // add the LSB
val done = counterReg === 0.U
when (!done) { counterReg := counterReg - 1.U }
when(io.load) { dataReg := io.din; popCntReg := 0.U; counterReg := 8.U }
```

The top-level `PopulationCount` instantiates both and wires control to status.
(The datapath includes a `printf` "debug output" — you'll see it print each
cycle during the test.)

---

## 9.3 The ready/valid interface

To move data between subsystems, the **ready/valid** handshake is the standard:
the sender asserts `valid` when data is available, the receiver asserts `ready`
when it can accept, and the transfer happens on the cycle **both** are high.

<p align="center">
  <img src="figures/readyvalid.png" alt="The ready/valid flow control" width="420">
</p>

***Figure 9.6** — Ready/valid: `data` + `valid` from the sender, `ready` from
the receiver; transfer when both are asserted.*

Chisel packages this as **`DecoupledIO`** (in `chisel3.util`), parameterized by
the data type, with the data in a field called `bits`:

*illustrative — the shape of DecoupledIO*
```scala
class DecoupledIO[T <: Data](gen: T) extends Bundle {
  val ready = Input(Bool())
  val valid = Output(Bool())
  val bits  = Output(gen)
}
```

> To stay composable, neither `ready` nor `valid` may depend combinationally on
> the other. `DecoupledIO` places no ordering rules; `IrrevocableIO` adds the
> convention (used by AXI) that once `valid` is raised it stays until the
> transfer, and `bits` doesn't change.

A one-word buffer uses a `DecoupledIO` on each side. A single `emptyReg` is a
two-state Moore FSM (empty/full); `in.ready`/`out.valid` come only from that
state, so there's no combinational input→output path. The input side is
`Flipped` because `DecoupledIO` is defined from the sender's viewpoint:

`src/main/scala/ReadyValidBuffer.scala`
```scala
val io = IO(new Bundle {
  val in = Flipped(new DecoupledIO(UInt(8.W)))
  val out = new DecoupledIO(UInt(8.W))
})

val dataReg = Reg(UInt(8.W))
val emptyReg = RegInit(true.B)

io.in.ready := emptyReg
io.out.valid := !emptyReg
io.out.bits := dataReg

when (emptyReg & io.in.valid)  { dataReg := io.in.bits; emptyReg := false.B }
when (!emptyReg & io.out.ready) { emptyReg := true.B }
```

---

## 9.4 Build, run, and check

```
$ sbt test
```

Expected (3 tests; the popcount test also prints the datapath's debug lines):

```
[info] - should accept, hold, and release one word
[info] - should count the set bits
[info] - should flash three times (both versions)
[info] Tests: succeeded 3, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

writes `Flasher.sv`, `Flasher2.sv`, `PopulationCount.sv`, and
`ReadyValidBuffer.sv`.

---

## 9.5 Recap

- **Factor** a big FSM into communicating FSMs that exchange control signals
  (flasher: master + timer, then + a flash counter → 3 states, configurable).
- An **FSMD** pairs a control FSM with a datapath; the FSM sequences and
  handshakes, the datapath computes (popcount).
- The **ready/valid** handshake (`DecoupledIO`) moves data safely; transfer on
  the cycle both `ready` and `valid` are high. Use `Flipped` for the input side.
- Keep FSM interfaces Moore-like (no combinational input→output path) so they
  compose without combinational loops (see Chapter 8).

## 9.6 Exercise

Extend the popcount FSMD, or build your own FSMD (e.g. a serial multiplier or a
GCD unit) with a ready/valid input and output. Then chain two
`ReadyValidBuffer`s and confirm data flows through with the handshake.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 8 — Finite-State Machines](../ch08-finite-state-machines/README.md)**.
Next: **[Chapter 10 — Hardware Generators](../ch10-hardware-generators/README.md)**.
