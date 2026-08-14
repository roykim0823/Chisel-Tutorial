# Chapter 5 — Clock, Reset, and Interfaces

> **Audience**: anyone debugging timing, reset, or connection problems
> **Goal**: read what Chisel's three implicit constructs — port direction, clock, and reset — become in SystemVerilog
> **Time budget**: About 4 days

Chisel hides three things that SystemVerilog makes explicit: **which way a port
points**, **which clock a register uses**, and **how reset works**. You never
write them at the point of use, which is exactly why they are the source of the
most confusing generated code. This part makes all three visible.

*Conventions: paths are relative to this directory, and commands are run from
here. Every SystemVerilog block is real captured output.*

## Build and run the examples

```
$ sbt "runMain Generate"                    # emit all 6 designs into generated/
$ sbt "runMain Generate list"               # show the available names
$ sbt "runMain Generate AsyncResetExample"  # just one
```

**Every code block below is labelled with its path**: `` `src/main/scala/…` ``
for Chisel, `` `generated/…` `` for emitted SystemVerilog.

---

## 1. Reset

### 1.1 Synchronous reset — the Chisel default

A `Module`'s implicit reset is **synchronous and active-high**.

`src/main/scala/Resets.scala`
```scala
class SyncResetExample extends Module {
  val io = IO(new Bundle { val out = Output(UInt(8.W)) })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.out := reg
}
```

`generated/SyncResetExample.sv`
```systemverilog
module SyncResetExample(
  input        clock,
               reset,
  output [7:0] io_out
);

  reg [7:0] reg_0;
  always @(posedge clock) begin
    if (reset)
      reg_0 <= 8'h0;
    else
      reg_0 <= reg_0 + 8'h1;
  end
  assign io_out = reg_0;
```

`reset` is **not in the sensitivity list**, so it can only take effect at a
clock edge. That is what makes it synchronous.

(The `val reg` came out as `reg_0` because `reg` is a Verilog keyword — see
[Ch 4 §1.2](../ch04-names-waveforms/README.md#12-keyword-collisions).)

### 1.2 Asynchronous reset

`src/main/scala/Resets.scala`
```scala
class AsyncResetExample extends Module {
  val io = IO(new Bundle { val out = Output(UInt(8.W)) })
  withReset(reset.asAsyncReset) {
    val reg = RegInit(0.U(8.W))
    reg := reg + 1.U
    io.out := reg
  }
}
```

`generated/AsyncResetExample.sv`
```systemverilog
  reg [7:0] reg_0;
  always @(posedge clock or posedge reset) begin
    if (reset)
      reg_0 <= 8'h0;
    else
      reg_0 <= reg_0 + 8'h1;
  end
```

**The whole difference is the sensitivity list.** One extra term, and the
register resets the instant `reset` rises rather than waiting for a clock edge:

| SystemVerilog | meaning |
|---|---|
| `always @(posedge clock)` + `if (reset)` | **synchronous** reset |
| `always @(posedge clock or posedge reset)` | **asynchronous** reset |

This is the single most useful reset check you can do on generated code, and it
takes one `grep`.

`Module with RequireAsyncReset` makes a whole module's implicit reset
asynchronous instead of wrapping in `withReset`. Async reset *assertion* is
glitch-sensitive and its *deassertion* must be synchronized — the reset
synchronizer pattern is [Ch 8 §3](../ch08-cdc/README.md#3-reset-synchronizer).

### 1.3 A register with no reset

`Reg(...)` instead of `RegInit(...)` produces no reset arm at all:

`src/main/scala/Resets.scala`
```scala
val reg = Reg(UInt(8.W))   // no RegInit: no reset arm
reg := io.in
io.out := reg
```

`generated/NoResetExample.sv`
```systemverilog
  reg [7:0] reg_0;
  always @(posedge clock)
    reg_0 <= io_in;
```

No `if (reset)`, and the `begin`/`end` is gone too since there is only one
statement. In simulation this register starts as X (or as a random value, if the
randomization scaffolding is enabled); in silicon it powers up unpredictably.

That is often exactly what you want — a datapath pipeline register does not need
reset, and leaving it out saves the reset routing and area. But it means **you
cannot rely on it reading zero**, and a testbench that passes because a
simulator happened to start it at 0 will fail on hardware.

### 1.4 Reset bugs and how they look

- **A register that should reset does not** → check for the missing `if (reset)`
  arm. You wrote `Reg` where you needed `RegInit`.
- **A design works in simulation but not on the FPGA** → suspect an unreset
  register that simulation initialized helpfully.
- **Reset takes effect a cycle late** → synchronous reset is doing exactly what
  it says; if you need immediate assertion you need async.

---

## 2. Clock domains

### 2.1 The implicit clock

Every `Module` has one, and every `Reg` inside uses it. It appears as an ordinary
`clock` port. Nothing more to say — which is the point.

### 2.2 Multiple clocks

`withClock` switches the implicit clock for a scope:

`src/main/scala/Clocks.scala`
```scala
class TwoClocks extends Module {
  val io = IO(new Bundle {
    val clkB = Input(Clock())
    val inA  = Input(UInt(8.W))
    val inB  = Input(UInt(8.W))
    val outA = Output(UInt(8.W))
    val outB = Output(UInt(8.W))
  })
  // Implicit clock domain.
  val regA = RegNext(io.inA)
  io.outA := regA

  // A second domain, explicitly clocked.
  withClock(io.clkB) {
    val regB = RegNext(io.inB)
    io.outB := regB
  }
}
```

`generated/TwoClocks.sv`
```systemverilog
  reg [7:0] regA;
  reg [7:0] regB;
  always @(posedge clock)
    regA <= io_inA;
  always @(posedge io_clkB)
    regB <= io_inB;
  assign io_outA = regA;
  assign io_outB = regB;
```

Two `always` blocks on two different edges — and note firtool did **not** merge
these two registers, because merging is only legal within one clock. The
sensitivity list is how you identify which domain a register belongs to.

`Input(Clock())` gives you a clock as an ordinary port; there is no special
clock type in the emitted Verilog.

> **A warning this level can only gesture at.** Passing data between these two
> registers would be a **clock domain crossing**, and RTL simulation will happily
> show it working. It is not working — metastability is invisible to a
> cycle-accurate simulator. Never conclude from a passing simulation that a CDC
> is safe. Synchronizers, the constraints that tell timing tools about the
> crossing, and how it actually fails are [Ch 8 §2](../ch08-cdc/README.md#2-clock-domain-crossing).

---

## 3. Interfaces and direction

### 3.1 Direction is from the module's own point of view

`Input`/`Output` in a Chisel `Bundle` describe the port as seen *by the module
that instantiates the bundle*. `Flipped` reverses every direction in the bundle
at once, which is how a producer and a consumer share one interface definition.

`src/main/scala/Interfaces.scala`
```scala
class PlainIO extends Bundle {
  val data  = Output(UInt(8.W))
  val valid = Output(Bool())
  val ready = Input(Bool())
}
```

`src/main/scala/Interfaces.scala`
```scala
val producer = new PlainIO           // as declared
val consumer = Flipped(new PlainIO)  // every direction reversed
```

`generated/FlipDemo.sv`
```systemverilog
module FlipDemo(
  input        clock,
               reset,
  output [7:0] io_producer_data,
  output       io_producer_valid,
  input        io_producer_ready,
  input  [7:0] io_consumer_data,
  input        io_consumer_valid,
  output       io_consumer_ready
);

  assign io_producer_data = io_consumer_data;
  assign io_producer_valid = io_consumer_valid;
  assign io_consumer_ready = io_producer_ready;
endmodule
```

The two halves are exact mirrors: `data` and `valid` are outputs on `producer`
and inputs on `consumer`; `ready` goes the other way on both. One bundle
definition, two opposite port sets, zero duplicated code — and in the
SystemVerilog it is just eight ordinary ports with no trace of the abstraction.

**Debugging direction problems.** Chisel catches these at elaboration, not in
simulation. Getting `Flipped` backwards produces:

```
io.consumer.data in FlipDemo cannot be written from module FlipDemo
```

which means you tried to drive something that `Flipped` made an input. Read the
message as "check which side of the interface you are", not as a type error.

### 3.2 The standard `Decoupled` pattern

`Decoupled(T)` is the library's ready/valid bundle: `bits` and `valid` flowing
one way, `ready` back. A consumer port is `Flipped(Decoupled(...))`.

`src/main/scala/Interfaces.scala`
```scala
class DecoupledDemo extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(UInt(8.W)))
    val out = Decoupled(UInt(8.W))
  })
  val full = RegInit(false.B)
  val data = Reg(UInt(8.W))

  io.in.ready  := !full
  io.out.valid := full
  io.out.bits  := data

  when(io.in.fire)  { data := io.in.bits; full := true.B }
  when(io.out.fire) { full := false.B }
}
```

Run `sbt "runMain Generate DecoupledDemo"` and read `generated/DecoupledDemo.sv`:
the bundle flattens to `io_in_ready`, `io_in_valid`, `io_in_bits`,
`io_out_ready`, `io_out_valid`, `io_out_bits`, with `in` and `out` mirrored
exactly as above. `fire` is not a signal — it is `ready && valid`, computed
inline.

Chapter 9 of the tutorial covers the handshake protocol itself.

---

## 4. Pitfalls

**Believing RTL simulation about CDC.** A cycle-accurate simulator cannot model
metastability. Crossing domains without a synchronizer will simulate perfectly
and fail in silicon.

**The register does not hold what you assigned.** In hardware every `:=` in a
clocked context takes effect at the *next* edge, and Chisel's last-connect-wins
rule means a later conditional assignment overrides an earlier unconditional
one. Read the emitted `if`/`else` chain to confirm the priority you got.

---

## 5. Exercise: reset experiments

1. Emit all three reset variants and `diff` them pairwise. Confirm the *only*
   structural difference between sync and async is the sensitivity list.
2. Add a second `RegInit` to `AsyncResetExample`. Do both registers land in one
   `always` block, or two? Why might async reset change the merging behaviour?
3. Take `NoResetExample`, emit it with and without `--disable-all-randomization`,
   and explain what the randomization block would do to a testbench that assumed
   the register starts at 0.
4. Change `TwoClocks` so `regB` reads `regA`. The design still elaborates and
   still simulates — write down why that is a bug anyway.

---

## Where next

- [**Ch 4 — Names, Signals, and Waveforms**](../ch04-names-waveforms/README.md)
- [**Ch 6 — printf, assert, and the Toolchain**](../ch06-printf-assert/README.md)
- [**Ch 8 — Clocking & CDC**](../ch08-cdc/README.md) picks up
  CDC, reset synchronizers, and clock gating properly.
- Back to the [appendix index](../README.md).

## References

- Tutorial [Chapter 7](../../ch07-input-processing/README.md) (synchronizers,
  reset synchronization) and [Chapter 9](../../ch09-communicating-state-machines/README.md)
  (the ready/valid handshake)
- [Chisel explanations: multiple clock domains](https://www.chisel-lang.org/docs/explanations/multi-clock)
