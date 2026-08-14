# Chapter 8 — Clock Gating, CDC, and Reset

> **Audience**: anyone whose design has more than one clock, or a power budget
> **Goal**: build the three structures that RTL simulation cannot verify for you — enable flops, clock-domain crossings, and reset synchronizers — and know why simulation cannot verify them
> **Time budget**: About 1 week

This part covers the corner of hardware design where **a passing simulation
proves nothing**. Metastability, reset recovery, and clock gating all live in the
analogue gap between "the RTL says this" and "the silicon does this". You cannot
test your way to confidence here; you have to build the right structure and then
tell the timing tools about it.

*Conventions: paths are relative to this directory; commands run from here.
Every SystemVerilog block is real captured output.*

## Build and run

```
$ sbt "runMain Generate"       # emit all 5 designs into generated/
$ sbt "runMain Generate list"  # show the available names
```

---

## 1. Clock gating

### 1.1 Why

A flip-flop burns dynamic power on every clock edge whether or not its value
changes. Gating the clock off when a register is not being updated is one of the
largest power savings available, and on a big design it is routine.

### 1.2 Start with an enable flop

The portable way to ask for it is not to gate anything yourself — it is to
describe an **enable flop** and let synthesis do the conversion:

`src/main/scala/ClockGate.scala`
```scala
io.out := RegEnable(io.in, 0.U(8.W), io.en)
```

`generated/EnableFlop.sv`
```systemverilog
  reg [7:0] io_out_r;
  always @(posedge clock) begin
    if (reset)
      io_out_r <= 8'h0;
    else if (io_en)
      io_out_r <= io_in;
  end
  assign io_out = io_out_r;
```

**`else if (io_en)` with no final `else` is the shape synthesis looks for.** In a
*combinational* block that pattern would infer a latch (Ch 7 §2); in a *clocked*
block it means "hold", which is exactly a flip-flop with an enable. Tools
recognize it and can insert an integrated clock-gating cell automatically.

`RegEnable` and a hand-written `when` produce the same circuit — the only
difference in the output is the register's name:

```
$ diff <(norm EnableFlop) <(norm GatedRegister)
<   reg [7:0] io_out_r;
>   reg [7:0] enReg;
```

(`RegEnable` names the register after the signal it drives; the explicit `when`
version keeps your `val` name. Prefer the explicit form when the name matters
for constraints or debugging.)

### 1.3 Explicit gating

When you must instantiate a specific technology cell — a foundry ICG — that cell
is a `BlackBox`, covered in
[Ch 9 §1](../ch09-integration/README.md#1-blackbox-integrating-existing-systemverilog).
Do this only when automatic gating is insufficient: a hand-instantiated gate is
one more thing that can be wrong, and it ties your RTL to one library.

**Never gate a clock with ordinary logic** (`gatedClk := clk & en`). That
produces glitches on the clock, which is a correctness failure, not a style
issue. Clock gating cells exist precisely because the enable must be captured on
the correct phase.

---

## 2. Clock domain crossing

### 2.1 The problem

When a signal generated in one clock domain is sampled in another, the source can
change arbitrarily close to the destination's clock edge. Violating the
destination flop's setup/hold window puts it into a **metastable** state —
neither 0 nor 1 — for an unbounded time.

**RTL simulation cannot show you this.** A cycle-accurate simulator has no
notion of setup time or metastability; it will sample a clean 0 or 1 and your
test will pass. This is the single most dangerous false negative in digital
design, and the reason CDC gets its own tools and its own review.

### 2.2 The two-flop synchronizer

For a **single bit**, the standard structure is two back-to-back registers in the
destination domain:

`src/main/scala/Cdc.scala`
```scala
class TwoFlopSync extends Module {
  val io = IO(new Bundle {
    val async = Input(Bool())    // from another clock domain
    val sync  = Output(Bool())   // safe in this domain
  })
  val meta   = RegNext(io.async, false.B)
  val stable = RegNext(meta, false.B)
  io.sync := stable
}
```

`generated/TwoFlopSync.sv`
```systemverilog
  reg meta;
  reg stable;
  always @(posedge clock) begin
    if (reset) begin
      meta <= 1'h0;
      stable <= 1'h0;
    else begin
      meta <= io_async;
      stable <= meta;
  end
  assign io_sync = stable;
```

`meta` may go metastable; it is then given a full clock period to settle before
`stable` samples it. This does not *eliminate* failure — it makes the mean time
between failures astronomically long, which is the best anyone can do.

Name the first register something recognizable. CDC checking tools and reviewers
look for this structure by name and shape.

### 2.3 Why a bus cannot use this

`BadBusSync` applies the same two registers to an 8-bit value:

`src/main/scala/Cdc.scala`
```scala
io.sync := RegNext(RegNext(io.async, 0.U), 0.U)
```

It emits perfectly reasonable-looking SystemVerilog, and it is **wrong**. Each
bit settles independently, so on a cycle where several bits change you can latch
a combination that never existed in the source domain — `0111` → `1000` can be
observed as `1111` or `0000`. Simulation will never show it.

Multi-bit crossings need a different structure: a handshake (transfer only when
both sides agree the data is stable), an asynchronous FIFO with Gray-coded
pointers, or a single-bit "toggle" synchronizer gating a stable bus.

The design is included here precisely so you can see that **the generated code
gives you no warning at all** — correctness of a CDC is not visible in the RTL.

### 2.4 Telling the tools

Structure alone is not enough. Timing tools must be told the crossing is
intentional, or they will try (and fail) to close timing across two unrelated
clocks:

*illustrative — SDC, applied to the synthesized design*
```tcl
# Preferred: declare the clock groups asynchronous
set_clock_groups -asynchronous -group {clk_a} -group {clk_b}

# Or cut the specific path into the synchronizer
set_false_path -to [get_pins {*/meta_reg/D}]
```

Note the path uses the **generated** register name. If a rename moves it, the
constraint silently stops applying — see
[Ch 4 §1.4](../ch04-names-waveforms/README.md#14-module-and-instance-names-in-a-hierarchy).

---

## 3. Reset synchronizer

### 3.1 Assert asynchronously, deassert synchronously

Asynchronous reset has the opposite problem to synchronous reset. Assertion is
fine — it takes effect immediately, which is what you want. **Deassertion** is
the hazard: if reset releases close to a clock edge, different flops can leave
reset on different cycles, and a state machine can start in an illegal state.

The standard fix asserts asynchronously and releases synchronously:

`src/main/scala/ResetSync.scala`
```scala
class ResetSynchronizer extends Module {
  val io = IO(new Bundle {
    val asyncResetIn = Input(AsyncReset())
    val syncResetOut = Output(Bool())
  })
  withReset(io.asyncResetIn) {
    val r1 = RegInit(true.B)
    val r2 = RegInit(true.B)
    r1 := false.B
    r2 := r1
    io.syncResetOut := r2
  }
}
```

`generated/ResetSynchronizer.sv`
```systemverilog
  reg r1;
  reg r2;
  always @(posedge clock or posedge io_asyncResetIn) begin
    if (io_asyncResetIn) begin
      r1 <= 1'h1;
      r2 <= 1'h1;
    else begin
      r1 <= 1'h0;
      r2 <= r1;
  end
  assign io_syncResetOut = r2;
```

Read the sensitivity list: `posedge io_asyncResetIn` means both registers go to 1
**immediately** when reset asserts. On release, `r1` clears on the next edge and
`r2` one edge later — so the output deasserts synchronously, two clocks after the
input released.

This is the same two-register shape as the CDC synchronizer, for the same
reason: reset release is itself an asynchronous event crossing into the clock
domain.

### 3.2 Constraints

Reset is normally a false path for *assertion* (it is asynchronous by design)
while the *release* path through the synchronizer must still meet timing. Getting
this wrong is a classic source of silicon that fails to come out of reset
reliably.

---

## 4. Pitfalls

**Believing RTL simulation about CDC.** Repeated because it is the one that
ships bugs. A passing test says nothing about metastability.

**Multi-bit signals through flop synchronizers.** Individually correct bits,
collectively wrong value. Use a handshake, an async FIFO, or Gray coding.

**CDC paths without constraints.** The structure and the constraints are two
halves of one fix; either alone is incomplete.

**Gating a clock with an AND gate.** Glitches. Use a proper cell, or an enable
flop and let the tool do it.

---

## 5. Exercises

1. Emit `TwoFlopSync` and `BadBusSync` and compare. Write down what in the
   generated SystemVerilog would let a reviewer tell the safe one from the unsafe
   one. (Answer: nothing — which is the point.)
2. Build a two-domain design using `withClock` ([Ch 5 §2.2](../ch05-clock-reset/README.md#22-multiple-clocks))
   and pass a signal across without a synchronizer. Confirm it simulates
   perfectly. Then add `TwoFlopSync` and diff the generated code.
3. Change `ResetSynchronizer` to three stages. What changes in the sensitivity
   list, and what changes in the release latency?
4. Take `EnableFlop` and write the SDC line that would cut the timing path into
   its enable. Which generated name did you have to use, and what would break it?

---

## Where next

- [**Ch 7 — Writing Synthesizable RTL**](../ch07-synthesizable-rtl/README.md)
- [**Ch 9 — Integration and Physical Design**](../ch09-integration/README.md)
- Tutorial [Chapter 7](../../ch07-input-processing/README.md) builds a
  synchronizer and a reset synchronizer in the book's own terms.
- Back to the [appendix index](../README.md).
