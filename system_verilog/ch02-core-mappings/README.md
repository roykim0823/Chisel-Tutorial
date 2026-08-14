# Chapter 2 — Core Mappings

> **Audience**: every Chisel user, after [A1](../ch01-syntax/README.md)
> **Goal**: know exactly what `Module`, `Reg`, `Wire`, `when`, and `Mux` become in SystemVerilog — and how much of what you wrote survives
> **Time budget**: About 3 days

[A1](../ch01-syntax/README.md) taught SystemVerilog as a person
writes it. This part shows what firtool actually emits for the everyday Chisel
constructs, and the two do not match as closely as you would expect.

*Conventions: paths are relative to this directory; commands run from here.*

## Build and run

```
$ sbt "runMain Generate"                  # emit all 7 designs into generated/
$ sbt "runMain Generate list"             # show the available names
$ sbt "runMain Generate WireExample"      # just one
```

**Every code block is labelled with its path**: `` `src/main/scala/…` `` for the
Chisel you wrote, `` `generated/…` `` for the SystemVerilog firtool emitted.
`generated/` is git-ignored and created by `runMain Generate`.

---

## 2. Core correspondences

This section walks through each fundamental Chisel construct and its generated SV, side by side.

### 2.1 Module and Bundle

`src/main/scala/Adder.scala`
```scala
import chisel3._

class Adder(width: Int) extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(width.W))
    val b   = Input(UInt(width.W))
    val sum = Output(UInt(width.W))
  })
  io.sum := io.a + io.b
}
```

**Generated SystemVerilog** (for `width = 8`)

`generated/Adder.sv`
```systemverilog
module Adder(
  input        clock,
               reset,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_sum
);

  assign io_sum = io_a + io_b;
endmodule
```

Note what is *not* there: no `logic` keyword on the ports, and same-direction
same-width ports are collapsed onto shared declarations. firtool emits ports in
the terse Verilog-2001 style — see [Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb).

**Correspondence table**

| Chisel | SystemVerilog |
|---|---|
| `class Adder extends Module` | `module Adder ... endmodule` |
| `io = IO(new Bundle { ... })` | flattened port list |
| Bundle field `a` | port name `io_a` (hierarchy flattened with underscores) |
| `Input(UInt(width.W))` | `input [width-1:0]` (no `logic` keyword) |
| `Output(UInt(width.W))` | `output [width-1:0]` |
| `:=` (combinational) | `assign` |
| implicit clock and reset | explicit `clock`, `reset` ports |
| Scala parameter `width` | baked-in constant (no SV `parameter`) |

Note that even though this module uses neither clock nor reset, the ports still appear (a `Module` always has them; `RawModule` — a Chisel module with no implicit clock/reset — omits them).

### 2.2 Register

`src/main/scala/CounterExample.scala`
```scala
class CounterExample extends Module {
  val io = IO(new Bundle {
    val count = Output(UInt(8.W))
  })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.count := reg
}
```

`generated/CounterExample.sv`
```systemverilog
module CounterExample(
  input        clock,
               reset,
  output [7:0] io_count
);

  reg [7:0] reg_0;
  always @(posedge clock) begin
    if (reset)
      reg_0 <= 8'h0;
    else
      reg_0 <= reg_0 + 8'h1;
  end
  assign io_count = reg_0;
endmodule
```

(the randomization block between the `always` and the `assign` is elided here —
[Ch 3 §3.4](../ch03-aggregates/README.md#34-register-randomization-blocks-new-section) covers it)

Two things to register. The register is `reg` in a plain `always @(posedge
clock)` — **not** `logic` in an `always_ff`; firtool never emits `always_ff`, and
[Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb) explains why. And the
Chisel `val reg` came out as `reg_0`, because `reg` is a Verilog keyword
([Ch 3 §3.3](../ch03-aggregates/README.md#33-automatic-name-suffixes)).

**Correspondence table**

| Chisel | SystemVerilog |
|---|---|
| `RegInit(0.U(8.W))` | `reg [7:0]` declaration + `always @(posedge clock)` block + `if (reset)` arm |
| `reg := expr` | `reg_0 <= expr` (non-blocking, in the `else` arm) |
| `Reg(UInt(8.W))` (no init) | same, but **no** `if (reset)` arm — starts as X in 4-state simulation |

Do **not** expect one block per register: firtool merges every register sharing a clock into a single `always` block, as [§2.3](#23-register-variants-regnext-regenable-new-section) shows.

### 2.3 Register Variants: `RegNext`, `RegEnable` (New Section)

These common idioms are just sugar, and their SV makes that obvious:

`src/main/scala/RegVariants.scala`
```scala
val d1 = RegNext(io.in)               // 1-cycle delay, no reset value
val d2 = RegNext(io.in, 0.U)          // 1-cycle delay, reset to 0
val d3 = RegEnable(io.in, io.en)      // update only when en
```

`generated/RegVariants.sv`
```systemverilog
  reg [7:0] d1;
  reg [7:0] d2;
  reg [7:0] d3;
  always @(posedge clock) begin
    d1 <= io_in;              // RegNext: unconditional capture, no reset arm
    if (io_en)
      d3 <= io_in;            // RegEnable: an if with no else - the flop holds
    if (reset)
      d2 <= 8'h0;
    else
      d2 <= io_in;            // RegNext with init: gets a reset arm
  end
```

**All three registers land in one `always` block.** firtool merges registers that
share a clock, so do not expect a block per register — and note the block's
statement order (`d1`, `d3`, `d2`) does not follow the Chisel declaration order.
When you are hunting for one register's update logic, search for the signal name,
not for a block boundary.

The `RegEnable` pattern — an `if` with no `else` inside a clocked block — is **not** a latch (that hazard only exists in combinational blocks). It synthesizes to a flip-flop with an enable, which synthesis tools may later map to clock gating ([Ch 8 §1](../ch08-cdc/README.md#1-clock-gating)).

### 2.4 Wire

`src/main/scala/WireExample.scala`
```scala
class WireExample extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val mid = Wire(UInt(8.W))
  mid := io.a + io.b
  io.out := mid << 1
}
```

`generated/WireExample.sv`
```systemverilog
module WireExample(
  input        clock,
               reset,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_out
);

  assign io_out = {io_a[6:0] + io_b[6:0], 1'h0};
endmodule
```

This one repays a close look, because almost nothing survived.

The `Wire` named `mid` is **gone** — a `Wire` is a name for a value during
elaboration, not necessarily a signal in the output, and firtool inlines it.

More striking is the arithmetic. The source says "add, then shift left by one",
in 8 bits. firtool reasoned that a left shift by one discards the top bit of the
sum, so the top bit never needs computing: it emits a **7-bit** adder
(`io_a[6:0] + io_b[6:0]`) with a constant zero concatenated in the low position.
Same result, one bit of adder cheaper.

This is the general lesson for reading generated code: firtool optimizes on the
*value* your circuit produces, not on the structure you wrote. Do not expect a
one-to-one correspondence between your operators and the emitted ones.

| Chisel | SystemVerilog |
|---|---|
| `Wire(UInt(8.W))` | a `wire [7:0]` declaration, **or nothing** if it can be inlined |
| `wire := expr` | `assign wire = expr;`, or folded into the consumer |
| `WireDefault(init)` | same, with the default folded into the emitted expression |

### 2.5 `when` / `.elsewhen` / `.otherwise`

`src/main/scala/WhenExample.scala`
```scala
class WhenExample extends Module {
  val io = IO(new Bundle {
    val sel = Input(UInt(2.W))
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val result = Wire(UInt(8.W))
  when(io.sel === 0.U) {
    result := io.a
  }.elsewhen(io.sel === 1.U) {
    result := io.b
  }.otherwise {
    result := 0.U
  }
  io.out := result
}
```

`generated/WhenExample.sv`
```systemverilog
module WhenExample(
  input        clock,
               reset,
  input  [1:0] io_sel,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_out
);

  assign io_out = io_sel == 2'h0 ? io_a : io_sel == 2'h1 ? io_b : 8'h0;
endmodule
```

| Chisel | SystemVerilog |
|---|---|
| `when(cond) { ... }` | the true arm of a `?:` |
| `.elsewhen(cond)` | a nested `?:` in the false arm |
| `.otherwise` | the innermost false arm |

The whole construct became **one nested ternary**, with no `always_comb` and no
`result` signal. Reading right to left gives you the priority: `io_sel == 0`
wins, then `io_sel == 1`, then the default. That nesting *is* the priority
multiplexer chain.

You will rarely see `always_comb` in Chisel-generated code — firtool prefers
continuous `assign` even for logic that a human would write procedurally
([Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb)).

**`when` is not software `if`**: all branches exist simultaneously in hardware;
the condition merely selects which value flows through.

### 2.6 Mux

`src/main/scala/MuxExample.scala`
```scala
io.out := Mux(io.sel, io.a, io.b)
```

`generated/MuxExample.sv`
```systemverilog
assign io_out = io_sel ? io_a : io_b;
```

| Chisel | SystemVerilog |
|---|---|
| `Mux(cond, a, b)` | `cond ? a : b` (ternary conditional) |
| `MuxCase(default, Seq(c1 -> v1, ...))` | nested ternaries |
| `MuxLookup(key, default)(Seq(...))` | nested ternaries or `case` |

## 3. Exercises

### Exercise 1: Generate and annotate

`src/main/scala/SimpleALU.scala`
```scala
import chisel3._

class SimpleALU extends Module {
  val io = IO(new Bundle {
    val op     = Input(UInt(2.W))
    val a      = Input(UInt(8.W))
    val b      = Input(UInt(8.W))
    val result = Output(UInt(8.W))
  })

  val res = Wire(UInt(8.W))
  when(io.op === 0.U)      { res := io.a + io.b }
  .elsewhen(io.op === 1.U) { res := io.a - io.b }
  .elsewhen(io.op === 2.U) { res := io.a & io.b }
  .otherwise               { res := io.a | io.b }

  io.result := res
}

object Emit extends App {
  val clean = args.contains("clean")
  val opts = if (clean) Array("-strip-debug-info", "--disable-all-randomization")
             else Array.empty[String]
  println(_root_.circt.stage.ChiselStage.emitSystemVerilog(new SimpleALU, firtoolOpts = opts))
}
```

**Tasks**

1. Emit the clean form first — `sbt "runMain Emit clean"`.
2. Annotate every SV line with the Chisel line that produced it, using the
   `path:line:column` locator comments — `sbt "runMain Emit"` shows them.
3. From the raw form, identify the randomization scaffolding and the
   `` `ifndef SYNTHESIS `` guards.

**What to expect** (peek only after you have predicted):

<details>
<summary>The clean output</summary>

`generated/SimpleALU.sv` (or `sbt "runMain Emit clean"`)
```systemverilog
module SimpleALU(
  input        clock,
               reset,
  input  [1:0] io_op,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_result
);

  wire [3:0][7:0] _GEN = {{io_a | io_b}, {io_a & io_b}, {io_a - io_b}, {io_a + io_b}};
  assign io_result = _GEN[io_op];
endmodule
```

The four-way `when` chain became a packed lookup table, exactly like the FSM in
[Ch 3 §2.11](../ch03-aggregates/README.md#211-chiselenum-and-state-machines-new-section) — not the
if/else chain the source suggests.

This is the best possible illustration of "`when` is not software `if`": the
adder, the subtractor, the AND, and the OR **all exist and all compute every
cycle**. `io_op` only chooses which result is forwarded. Nothing is skipped,
because there is no such thing as skipping work in combinational hardware.
</details>

4. Now count the hardware. How many 8-bit arithmetic units did this design
   build? Compare with your mental model when you wrote the `when` chain.

### Exercise 2: Explore unfamiliar idioms

Emit and study the SV for each of the following. Predict the output first, then check.

- `Mux1H(Seq((a === 0.U) -> x, (a === 1.U) -> y))`
- `PriorityEncoder(bits)`
- `RegNext(x)` vs `RegNext(x, 0.U)` — spot the reset-arm difference
- `ShiftRegister(x, 3)`
- `VecInit.fill(4)(0.U(8.W))`
- An 8-bit `SInt` add and compare — find the `$signed` casts
- A 16-entry `SyncReadMem` — find the unpacked array and the read-address register

### Exercise 3: Width detective

Write a Chisel module using `+`, `+&`, `*`, and slicing, with deliberately mixed widths. In the emitted SV, explain where each extension and truncation happens, using the width rules in [A1](../ch01-syntax/README.md).

---

---

## Where next

- [**Ch 3 — Aggregates, Memory, and Reading Generated Code**](../ch03-aggregates/README.md)
  — `Vec`, `Bundle`, `SInt`, memories, FSMs, and the toolchain artefacts that
  clutter every generated file.
- [**Chapter 4**](../ch04-names-waveforms/README.md) — finding these
  signals in a waveform.
- Back to the [appendix index](../README.md).
