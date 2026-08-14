# Chapter 7 — Writing Synthesizable RTL

> **Audience**: anyone targeting an FPGA or an ASIC
> **Goal**: know which SystemVerilog constructs synthesis accepts, why Chisel cannot emit the dangerous ones, and where elaboration replaces SV's own generation features
> **Time budget**: About 1 week

Simulation accepts far more SystemVerilog than synthesis does. A hand-written
design can pass every test and still be unbuildable — or worse, build into
something subtly different from what simulated. Chisel removes most of that risk
by construction, and this part shows exactly how.

*Conventions: paths are relative to this directory; commands run from here.
Every SystemVerilog block is real captured output.*

## Build and run

```
$ sbt "runMain Generate"            # emit all 6 designs into generated/
$ sbt "runMain Generate list"       # show the available names
```

---

## 1. The synthesizable subset

Synthesis accepts roughly: `module`/`endmodule`, port and signal declarations,
continuous `assign`, `always_comb`/`always_ff`/`always @`, `if`/`case`,
operators, module instantiation, `parameter`/`localparam`, `generate`, and
memory arrays.

It **rejects or ignores**: `initial` blocks, `#` delays, `$display`/`$fwrite`
and other system tasks, `fork`/`join`, `real`, dynamic arrays, classes,
constrained-random constructs, and most of the assertion machinery.

"The synthesizer ignores it" is the important phrase, and it is a trap: ignoring
is not the same as erroring. A construct the tool silently drops changes
behaviour between simulation and hardware, and nothing tells you.

**Chisel's position here is unusually strong.** A Chisel description has no way
to express simulation time — there is no `#` delay, no `initial` you write
yourself. The only non-synthesizable code in a generated file is `printf`,
`assert`, and `stop`, and firtool wraps every one of them in `` `ifndef
SYNTHESIS `` so the boundary is explicit rather than implicit. See
[B3](../ch06-printf-assert/README.md).

---

## 2. Latch inference — the bug Chisel cannot express

### 2.1 What it is

In SystemVerilog, a combinational block that fails to assign its target on every
path infers a **latch** — a level-sensitive storage element. It is almost never
what you meant, it wrecks static timing analysis, and the tool reports it as a
warning you may not read.

*illustrative — hand-written SystemVerilog with an inferred latch*
```systemverilog
always_comb begin
  if (sel)
    y = a;        // no else: y holds its previous value -> LATCH
end
```

### 2.2 Chisel refuses to build it

The equivalent Chisel is a hard error. In
`src/main/scala/Latches.scala`, uncomment the body of `BrokenLatch`:

`src/main/scala/Latches.scala`
```scala
  // val w = Wire(UInt(8.W))
  // when(io.cond) { w := 3.U }     // no default, no .otherwise -> error
  // io.out := w
```

and generation stops with:

```
error: sink "w" not fully initialized in "BrokenLatch"
  val w = Wire(UInt(8.W))
              ^
```

Note this comes from **firtool**, not the Scala compiler — it surfaces when you
emit, not when you compile. A whole category of RTL bug is simply unavailable.

### 2.3 Three ways to be complete — and they are the same circuit

`src/main/scala/Latches.scala`
```scala
  val w = Wire(UInt(8.W))
  w := 0.U                      // default first
  when(io.cond) { w := 3.U }
  io.out := w
```

`src/main/scala/Latches.scala`
```scala
  val w = WireDefault(0.U(8.W)) // default folded into the declaration
  when(io.cond) { w := 3.U }
  io.out := w
```

`src/main/scala/Latches.scala`
```scala
  val w = Wire(UInt(8.W))
  when(io.cond) { w := 3.U } .otherwise { w := 0.U }   // every path assigns
  io.out := w
```

All three emit **byte-identical** SystemVerilog (verified by normalizing and
diffing `DefaultFirst.sv`, `WireDefaultForm.sv`, and `OtherwiseForm.sv`):

`generated/DefaultFirst.sv`
```systemverilog
module DefaultFirst(
  input        clock,
               reset,
               io_cond,
  output [7:0] io_out
);

  assign io_out = io_cond ? 8'h3 : 8'h0;
endmodule
```

Reproduce it yourself:

```
$ cd generated
$ norm() { sed -n '/^module/,/^endmodule/p' $1.sv | sed -e 's|//.*||' -e "s/module $1/module M/" -e '/^$/d'; }
$ diff <(norm DefaultFirst) <(norm WireDefaultForm) && echo IDENTICAL
```

Pick whichever reads best. `WireDefault` is usually clearest because it makes
forgetting the default impossible.

### 2.4 Recognizing a latch in a report

If you ever integrate hand-written SV alongside your Chisel, watch synthesis logs
for `LATCH`, `DLAT`, or "inferred latch". The fix is always the same: assign on
every path, or add the default.

---

## 3. SV `interface` vs Chisel `Bundle`

SystemVerilog's `interface` bundles related signals into a named construct with
`modport`s defining each side's directions — conceptually what a Chisel `Bundle`
plus `Flipped` does.

Chisel does **not** emit interfaces. Bundles are flattened to `_`-joined port
names ([Ch 5 §3](../ch05-clock-reset/README.md#3-interfaces-and-direction)).
The reasons are practical: interface support is uneven across synthesis and
formal tools, they complicate hierarchical constraint paths, and Chisel already
gets the composition benefit at elaboration time — it has nothing left to gain
from the SV feature.

If a team requires interfaces at the boundary, the answer is a thin hand-written
SV shim that instantiates the Chisel module and re-bundles its flat ports.

---

## 4. `generate` vs elaboration

SystemVerilog has `generate`/`genvar` for structural repetition. Chisel does not
need it: your Scala *is* the generator, and the loop runs before any Verilog
exists.

`src/main/scala/Elaboration.scala`
```scala
class DelayChain(stages: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val regs = Seq.fill(stages)(RegInit(0.U(8.W)))
  regs.head := io.in
  for (i <- 1 until stages) {
    regs(i) := regs(i - 1)
  }
  io.out := regs.last
}
```

`generated/DelayChain.sv`
```systemverilog
  reg [7:0] regs_0;
  reg [7:0] regs_1;
  reg [7:0] regs_2;
  reg [7:0] regs_3;
  always @(posedge clock) begin
    if (reset) begin
      regs_0 <= 8'h0;
      regs_1 <= 8'h0;
      regs_2 <= 8'h0;
      regs_3 <= 8'h0;
```

No `generate`, no `genvar`, no loop — just four registers. The Scala `for` ran on
the JVM and left only its result.

The same applies to elaboration-time `if`: `Configurable(withBypass = true)`
emits a wire, `Configurable(false)` emits a register, and neither output
contains any trace of the condition. There is no runtime cost because there is
no runtime.

---

## 5. Tri-state, `inout`, and `Analog`

Chisel has no `Z` value and no tri-state driver, which is why on-chip buses are
built from multiplexers ([Chapter 12](../../ch12-interconnect/README.md)).

The one exception is `Analog`, the escape hatch for chip-level pads:

`src/main/scala/Analog.scala`
```scala
class AnalogPort extends Module {
  val io = IO(new Bundle {
    val pad = Analog(1.W)
    val obs = Output(Bool())
  })
  io.obs := false.B
}
```

`generated/AnalogPort.sv`
```systemverilog
module AnalogPort(
  input  clock,
         reset,
  inout  io_pad,
  output io_obs
);

  assign io_obs = 1'h0;
endmodule
```

A real `inout` port — the only way to get one out of Chisel.

`Analog` has **no value semantics**: you cannot read it, drive it, or do
arithmetic on it. All you can do is pass it through to a `BlackBox` that knows
what to do with it (Chapter 9). That restriction is deliberate — tri-state logic
has no place in synthesizable on-chip RTL, and the type makes misuse impossible
rather than merely discouraged.

---

## 6. Exercises

1. Uncomment `BrokenLatch` and read the exact error. Then fix it three different
   ways and confirm all three emit identical SystemVerilog.
2. Emit `Configurable(true)` and `Configurable(false)` and diff them. How much of
   the difference is "a register" versus "everything else"?
3. Change `DelayChain(4)` to `DelayChain(16)`. What in the output scales, and
   what stays constant?
4. Add a second `Analog` port and try to assign one to the other. Read the error
   and explain why the type forbids it.

---

## Where next

- [**Ch 8 — Clock Gating, CDC, and Reset**](../ch08-cdc/README.md)
- [**Ch 9 — Integration and Physical Design**](../ch09-integration/README.md)
- Back to the [appendix index](../README.md).
