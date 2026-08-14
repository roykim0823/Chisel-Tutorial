# Chapter 1 — SystemVerilog Syntax

> **Audience**: every Chisel user; no prior Verilog assumed
> **Goal**: recognize every SystemVerilog construct you will meet in generated code — and know which ones you will *never* meet
> **Time budget**: About 2 days

This part is a **SystemVerilog language primer**. It teaches the syntax as a
person writes it, so that the generated code in
[A2](../ch02-core-mappings/README.md) and
[A3](../ch03-aggregates/README.md) is readable rather than
cryptic.

> **Everything in this part is hand-written SystemVerilog.** None of it is
> generated output. That distinction matters more than it sounds: two constructs
> taught below as the modern way to write RTL — `always_comb` and `always_ff` —
> **never appear** in Chisel-generated code. Learn them to read other people's
> SV; do not go looking for them in your own output. See
> [Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb).

*Conventions: paths are relative to this directory. There is no sbt project
here — this part has no generated output to reproduce.*

---

## The syntax set

This is the smallest set of SV constructs you must recognize to read Chisel-generated output fluently.

> Reminder: **every code block in this part is hand-written SystemVerilog.** In
> [A2](../ch02-core-mappings/README.md) and
> [A3](../ch03-aggregates/README.md) every block instead carries
> its file path — `src/main/scala/…` for Chisel sources and `generated/…` for
> real emitted SystemVerilog you can reproduce with `sbt "runMain Generate"`.

### 1.1 Module and Port Declarations

*Hand-written SystemVerilog — this is the style you would type. The same module
as firtool actually emits it looks different; compare with
[Ch 2 §2.1](../ch02-core-mappings/README.md#21-module-and-bundle).*

```systemverilog
module Adder(
  input  logic        clock,
  input  logic        reset,
  input  logic [7:0]  io_a,
  input  logic [7:0]  io_b,
  output logic [7:0]  io_sum
);
  // ...
endmodule
```

**Key points**

- `module ... endmodule` is the fundamental unit of hierarchy in SV. A Chisel `Module` becomes exactly one SV `module`.
- `input` / `output` / `inout` declare port direction. Chisel `Input(...)` and `Output(...)` map to `input` and `output`; `inout` only appears when you use Chisel's `Analog` type (rare — see [Ch 7 §5](../ch07-synthesizable-rtl/README.md#5-tri-state-inout-and-analog)).
- `logic [7:0]` declares an 8-bit signal. The range is written `[MSB:LSB]`, and Chisel-generated code always uses the descending form `[N-1:0]`, where bit 0 is the least significant bit.
- A signal declared without a range (e.g., `input logic io_valid`) is a single bit — this is what Chisel `Bool()` and `UInt(1.W)` become.
- Chisel's implicit `clock` and `reset` appear as explicit, ordinary ports in SV. There is nothing special about them at the SV level — they are just 1-bit inputs that happen to be used in sensitivity lists and reset conditions.
- The style above, with directions and types inside the port list, is called an **ANSI-style header** (the modern form). Legacy code sometimes uses non-ANSI style where the port list only contains names and directions are declared separately in the body; you may encounter this in vendor IP but never in Chisel output.

### 1.2 `wire` vs `reg` vs `logic`

This is the single most confusing piece of Verilog history, so it's worth getting right once.

**Old Verilog (pre-SystemVerilog)** had two data types with misleading names:

- `wire` — a *net*: it must be continuously driven, typically by an `assign` statement or a module output. Used for combinational connections.
- `reg` — a *variable*: it can be assigned inside procedural blocks (`always`, `initial`). **Despite the name, `reg` does NOT mean a register/flip-flop.** A `reg` assigned in a combinational `always @(*)` block synthesizes to pure combinational logic. Whether something becomes a flip-flop is determined entirely by *how* it is assigned (edge-triggered block or not), never by its declared type.

**SystemVerilog** introduced `logic`, which unifies both: a `logic` signal can be driven either by a continuous `assign` or by procedural blocks (but not both at once — that's a multiple-driver error). In modern hand-written code, `logic` is used almost everywhere.

> **But not in firtool's output.** Generated code uses the legacy `wire` and
> `reg` keywords; `logic` appears only for local variables inside procedural
> blocks (`automatic logic`) and in the simulation scaffolding. Do not expect to
> see `logic [7:0] foo;` at module scope in a Chisel-generated file — see
> [Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb).

```systemverilog
logic [7:0] data;         // SV style (what firtool emits)
wire  [7:0] data_wire;    // legacy Verilog style (still legal)
reg   [7:0] data_reg;     // legacy Verilog style (still legal, still not necessarily a flip-flop)
```

**Rule of thumb when reading generated SV**: ignore the declared keyword and look at how the signal is *assigned*. `assign x = ...` or assignment in a combinational block means combinational logic; assignment inside an edge-sensitive block (`always_ff @(posedge clock)` or `always @(posedge clock)`) means a flip-flop.

**Also note**: `logic` (and `reg`) are **4-state** types — each bit can be `0`, `1`, `X` (unknown), or `Z` (high-impedance). Chisel's type system has no notion of X or Z, but the simulation of the generated SV does. See the 4-state section below.

### 1.3 `always_comb` (Combinational Logic)

```systemverilog
logic [7:0] sum;
always_comb begin
  sum = io_a + io_b;
end
```

**Key points**

- `always_comb` automatically computes its sensitivity list — the block re-executes whenever any signal read inside it changes. It replaces the older `always @(*)`, with some extra guarantees: tools must check that the block really is combinational and will warn if it would infer a latch or if a signal is driven from more than one place.
- Use **blocking assignment (`=`)** inside `always_comb`.
- Every path through the block should assign every output signal; otherwise the signal "remembers" its old value and the tool infers a latch (details and prevention in [Ch 7 §2](../ch07-synthesizable-rtl/README.md#2-latch-inference--the-bug-chisel-cannot-express)).

> **Reality check on firtool output**: Chisel-generated code contains **no**
> `always_comb` and **no** `always_ff` — not "sometimes", not "depending on
> options". Combinational logic becomes continuous `assign`; registers become
> plain `always @(posedge clock)`. Read `always @(posedge clock)` exactly as you
> would `always_ff @(posedge clock)`. The measurements, the flags that do *not*
> change it, and why the choice is reasonable are in
> [Ch 3 §3.6](../ch03-aggregates/README.md#36-why-there-is-no-always_ff-or-always_comb).

### 1.4 `always_ff` (Sequential Logic)

```systemverilog
logic [7:0] counter;
always_ff @(posedge clock) begin
  if (reset)
    counter <= 8'h00;
  else
    counter <= counter + 1'b1;
end
```

**Key points**

- This describes a bank of D flip-flops: `counter` only changes at the rising edge of `clock`.
- Use **non-blocking assignment (`<=`)** inside `always_ff`.
- `@(posedge clock)` means "trigger on the rising edge of clock". A falling-edge design would use `negedge`.
- The `if (reset)` at the top of the block, with no `reset` in the sensitivity list, is the standard pattern for a **synchronous** reset. Async reset adds `or posedge reset` to the sensitivity list ([Ch 5 §1.2](../ch05-clock-reset/README.md#12-asynchronous-reset)).

### 1.5 Blocking (`=`) vs Non-blocking (`<=`) Assignment

This distinction does not exist in Chisel (there is only `:=`), so it deserves careful attention.

```systemverilog
// Combinational logic: blocking (=)
always_comb begin
  temp   = a + b;
  result = temp * 2;   // sees the value of temp computed on the line above
end

// Sequential logic: non-blocking (<=)
always_ff @(posedge clock) begin
  a <= b;
  b <= a;              // values swap! Both right-hand sides are sampled first,
end                    // then both updates happen "simultaneously" at the end of the time step
```

**Why the difference exists**: blocking assignments execute immediately, in order, like ordinary software statements — which is what you want when describing a cascade of combinational operations. Non-blocking assignments sample all right-hand sides first and perform all updates together at the end of the simulation time step — which models how a bank of flip-flops all capture their D inputs on the same clock edge. If you used blocking assignments for flip-flops, the simulation result would depend on the (arbitrary) order in which the simulator executes `always` blocks, causing races.

**The golden rule** (which Chisel/firtool always follows, and you should too if you ever write SV by hand):

- `always_comb` → blocking `=`
- `always_ff` → non-blocking `<=`
- Never mix the two styles for the same signal.

**Mapping to Chisel**: a Chisel `:=` to a `Wire` becomes a blocking/continuous assignment; a `:=` to a `Reg` becomes a non-blocking assignment inside a clocked block. Chisel's semantics of "last connection wins" corresponds to the fact that within one SV procedural block, a later assignment to the same variable overrides an earlier one.

### 1.6 Bit Vectors, Literals, and Slicing

```systemverilog
logic [7:0]   byte_val;     // 8-bit
logic [31:0]  word_val;     // 32-bit
logic [N-1:0] param_val;    // parameterized width

// Literals: <width>'<base><value>
8'h0F           // 8-bit hex: 0000_1111
8'b0000_1111    // 8-bit binary (underscores are ignored, use them freely for readability)
8'd15           // 8-bit decimal
-8'sd1          // 8-bit signed -1 (note: the sign goes OUTSIDE, 's' marks signedness)
'0              // "all zeros" of whatever width the context needs (SV shorthand)
'1              // "all ones" of whatever width the context needs
8'hxx           // 8 bits of X (unknown) — simulation-only concept
8'hzz           // 8 bits of Z (high-impedance)
32'd42          // what Chisel's 42.U(32.W) becomes

// Concatenation and replication
{byte_val, byte_val}     // 16-bit: two copies side by side — Chisel Cat(a, b)
{4{1'b0}}                // replication: 4'b0000 — Chisel Fill(4, 0.U)
{24'h0, byte_val}        // zero-extension to 32 bits, made explicit

// Slicing (part-select)
byte_val[3:0]            // low nibble  — Chisel x(3, 0)
byte_val[7:4]            // high nibble — Chisel x(7, 4)
byte_val[0]              // single bit  — Chisel x(0)
```

**Key points**

- A literal without a width, like `42`, is a 32-bit signed integer by default. Chisel-generated code always emits explicitly sized literals, which is best practice.
- Chisel bit-extract `x(hi, lo)` maps directly to `x[hi:lo]`. Both are inclusive on both ends and the result width is `hi - lo + 1`.
- `{a, b, c}` concatenation places `a` in the most significant position — same convention as Chisel's `Cat(a, b, c)`.

### 1.7 Operators and Their Chisel Equivalents (New Section)

Chisel-generated SV uses a rich set of operators. Most are intuitive, but a few deserve attention.

| Chisel | SystemVerilog | Notes |
|---|---|---|
| `a + b`, `a - b` | `a + b`, `a - b` | See width rules in 1.12 — SV silently truncates; Chisel `+` truncates, `+&` grows |
| `a * b` | `a * b` | Chisel result width = sum of operand widths; SV width = max of operands (truncates!) unless the context is wider |
| `a / b`, `a % b` | `a / b`, `a % b` | Rarely what you want in synthesis for non-power-of-2 divisors |
| `a & b`, `a \| b`, `a ^ b` | `a & b`, `a \| b`, `a ^ b` | Bitwise |
| `!a` (Bool), `~a` | `!a`, `~a` | `!` is logical (1-bit result), `~` is bitwise complement |
| `a && b` (Bool) | `a && b` | Logical AND on 1-bit values |
| `a === b` in Chisel is written `a === b`? No — Chisel equality is `===` | `a == b` | **Careful**: Chisel's `===` is ordinary equality and becomes SV `==`. SV *also* has a `===` operator, which is a *4-state* ("case") equality that treats X and Z as literal values. Simulation-only; Chisel never generates it |
| `a =/= b` | `a != b` | |
| `a < b`, `a >= b`, ... | `a < b`, `a >= b`, ... | Unsigned comparison for `UInt`, signed (via `$signed`) for `SInt` |
| `a << n`, `a >> n` | `a << n`, `a >> n` | `>>` on `$signed` values becomes arithmetic shift `>>>` |
| `Cat(a, b)` | `{a, b}` | |
| `Fill(n, x)` | `{n{x}}` | |
| `x.andR` | `&x` | **Reduction operator**: AND of all bits of `x`, 1-bit result |
| `x.orR` | `\|x` | Reduction OR — "is x nonzero?" |
| `x.xorR` | `^x` | Reduction XOR — parity |
| `Mux(sel, a, b)` | `sel ? a : b` | Ternary/conditional operator |
| `x.asSInt` / signed ops | `$signed(x)` | See [Ch 3 §2.9](../ch03-aggregates/README.md#29-sint-and-signed-arithmetic-new-section) |
| `x.asUInt` | `$unsigned(x)` or plain reinterpretation | |

**Reduction operators** are worth memorizing — a lone `&`, `|`, or `^` *in front of* a vector collapses it to one bit. `|x` ("is any bit set?") appears constantly in generated code, e.g., for `x =/= 0.U`.

### 1.8 `case` / `if`

```systemverilog
// case
always_comb begin
  case (op)
    2'b00:   result = a + b;
    2'b01:   result = a - b;
    2'b10:   result = a & b;
    default: result = a | b;   // default catches everything else (incl. X/Z in simulation)
  endcase
end

// if / else if / else
always_comb begin
  if (enable)
    result = a;
  else
    result = 8'h00;
end
```

**Key points**

- A `case` without a `default` (and without covering all values) can infer a latch in combinational blocks — one more reason Chisel forces you to think about the `otherwise` branch.
- SV also has `casez` (treats `Z`/`?` bits in the case items as wildcards) and `casex` (treats both `X` and `Z` as wildcards — widely considered dangerous and best avoided). Chisel's `BitPat` matching in `switch`/`is` or in decoders can produce `casez`-style patterns or equivalent masked comparisons like `(op & 4'b1100) == 4'b1000`.
- SV-2012 adds `unique case` / `priority case` qualifiers that tell both simulator and synthesizer about mutual exclusivity — see [Ch 7](../ch07-synthesizable-rtl/README.md).

### 1.9 Module Instantiation

```systemverilog
module Top(
  input  logic        clock,
  input  logic        reset,
  input  logic [7:0]  a,
  input  logic [7:0]  b,
  output logic [7:0]  sum
);
  Adder adder_inst (
    .clock  (clock),
    .reset  (reset),
    .io_a   (a),
    .io_b   (b),
    .io_sum (sum)
  );
endmodule
```

**Key points**

- `.port_name(signal)` is a **named connection** — order doesn't matter, and it's the only style Chisel emits (and the only style you should ever write). Positional connections (`Adder adder_inst (clock, reset, a, b, sum);`) exist but are fragile.
- `.clock(clock)` reads as "connect the instance's port `clock` to the local signal `clock`".
- A Chisel `val adder_inst = Module(new Adder)` becomes exactly this: module name from the class (or `desiredName`), instance name from the `val`.

### 1.10 `parameter` and `localparam`

```systemverilog
module ParamAdder #(
  parameter int WIDTH = 8
) (
  input  logic [WIDTH-1:0] a,
  input  logic [WIDTH-1:0] b,
  output logic [WIDTH-1:0] sum
);
  localparam int MAX = (1 << WIDTH) - 1;  // internal constant, not overridable
  assign sum = a + b;
endmodule

// Instantiation with a parameter override
ParamAdder #(.WIDTH(16)) adder16 (
  .a(a16), .b(b16), .sum(sum16)
);
```

**Important difference from Chisel**: Chisel parameters are ordinary Scala constructor arguments, resolved during **elaboration** — before any SV exists. So `new Adder(width = 8)` and `new Adder(width = 16)` produce two *separate, fully specialized* SV modules (e.g., `Adder` and `Adder_1`), each with hard-coded widths, rather than one parameterized SV module. You will essentially never see `parameter` in Chisel-emitted module definitions; you *will* see parameter overrides when Chisel instantiates a parameterized `BlackBox` ([Ch 9 §1](../ch09-integration/README.md#1-blackbox-integrating-existing-systemverilog)).

This trade-off is worth understanding: SV parameters keep one generic module (nice for humans, resolved by the synthesis tool), while Chisel specialization gives full Scala programmability at the cost of duplicated module definitions in the output.

### 1.11 The 4-State Value System: `X` and `Z` (New Section)

Every bit of a SystemVerilog `logic` can hold one of **four** values:

| Value | Meaning | Where you'll see it |
|---|---|---|
| `0` | logic low | everywhere |
| `1` | logic high | everywhere |
| `X` | **unknown** | uninitialized registers, bus conflicts, out-of-range reads, propagated unknowns |
| `Z` | **high-impedance** (undriven) | tri-state buses, unconnected inputs |

Chisel's semantics are strictly 2-state — a `UInt` is always some concrete number. But the moment your design is simulated as SV in a 4-state simulator (VCS, Xcelium, Questa), X and Z become real:

- A register declared without an initial value starts as `X` until the first reset or write.
- `X` propagates: `X + 1 = X`, `if (x_valued_signal)` takes an unpredictable branch.
- In waveforms, X is typically drawn in red and Z in orange/yellow.

**Why you must know this now**: the most common "my Chisel design works in one simulator but not another" report traces back to X-propagation. Verilator (the most common open-source simulator for Chisel) is 2-state — it silently turns X into 0/1 — so designs that "work in Verilator" can fail in a 4-state simulator or in post-synthesis gate-level simulation. Levels B and D return to this repeatedly.

### 1.12 Implicit Width Rules: Extension and Truncation (New Section)

Chisel and SystemVerilog have *very different* width philosophies, and this is a top source of confusion when comparing your Chisel expression to the emitted SV.

**Chisel**: widths are part of the type; the compiler infers result widths conservatively (e.g., `a + b` where both are 8-bit yields 8 bits with `+`, but `a +& b` yields 9 bits; `a * b` yields 16 bits). Mismatched connections error out or are explicitly truncated/extended by you.

**SystemVerilog**: widths adjust *silently* according to context rules:

```systemverilog
logic [7:0]  a, b;
logic [8:0]  wide_sum;
logic [3:0]  narrow;

assign wide_sum = a + b;   // operands are extended to 9 bits FIRST, so the carry is kept
assign narrow  = a;        // silent truncation: only a[3:0] survives, no warning required
assign a = narrow;         // silent zero-extension of narrow to 8 bits
```

The rule: in an expression, all operands are first extended to the width of the *widest* operand or the assignment target (whichever the context dictates), the operation is performed at that width, and the result is truncated to the target width on assignment.

**Why this matters for reading generated SV**: firtool knows these rules and exploits them, so a Chisel `(a +& b)` (9-bit add of two 8-bit values) may be emitted simply as `assign wide_sum = a + b;` with a 9-bit target — the SV context rules provide the extra bit. Conversely you may see explicit slices like `_GEN_3[7:0]` where firtool documents a truncation. If widths in the SV look "wrong" at first glance, apply the context rules before assuming a bug.

---

## Pitfalls carried into the next parts

### Pitfall 1 — SV `reg` does not mean "register", and Chisel `Reg` always does

In SV, the declaration keyword tells you nothing; only the assignment context does. In Chisel, `Reg*` constructs are always flip-flops and `Wire` is always combinational. When auditing generated SV, confirm each Chisel `Reg` landed inside an edge-triggered `always` block.

### Pitfall 2 — `Bool()` is a 1-bit `logic`, with 4-state caveats

Chisel `Bool()` emits as a bare `logic` (1 bit). It is bit-identical to `UInt(1.W)` in SV. But in 4-state simulation that one bit can also be X or Z — `if (x)` where `x` is X does not take the branch you expect.

### Pitfall 3 — Bundle/Vec hierarchy is flattened with underscores

`io.a.b.c` → `io_a_b_c`, `io.data(2)` → `io_data_2`. When searching in a waveform viewer or grepping the SV, always translate dots and indices into underscores first. Ambiguity alert: `io_a_b` could be Bundle `io.a.b` or a field literally named `a_b` — one more reason to avoid underscores in Chisel field names.

### Pitfall 4 — Unnamed instances and signals get machine names

`Module(new SomeModule)` without binding to a `val` produces auto-named instances; intermediate expressions produce `_T`/`_GEN`. Both make waveform debugging painful. Bind everything meaningful to a named `val`; reach for `.suggestName` when the automatic name still isn't right.

### Pitfall 5 — Chisel's `===` is SV's `==`, and SV's `===` is something else entirely

Chisel uses `===`/`=/=` for hardware equality simply because Scala reserves `==`/`!=`. The generated SV uses `==`/`!=`. SV's own `===`/`!==` operators do 4-state literal matching (X compares equal to X) and are simulation-only — if you ever hand-write SV, don't use them in RTL.

### Pitfall 6 — Silent truncation on the SV side vs. checked widths on the Chisel side

Chisel errors out (or forces you to `.tail`/`.pad`) on width mismatches; SV silently truncates or zero-extends on assignment. When you paste Chisel-generated expressions into hand-written SV testbenches, the compile-time safety you're used to is gone — double-check widths yourself.

### Pitfall 7 — Utility idioms expand into unfamiliar shapes

`Mux1H`, `PriorityMux`, `PriorityEncoder`, and friends do not map to a single obvious SV construct. `Mux1H` may become an AND-OR network (`(sel0 ? a : 0) | (sel1 ? b : 0)`...), `PriorityEncoder` a chain of ternaries. If the SV looks nothing like a mux, don't panic — verify against the utility's documented semantics, and use the source locator comments.

---

---

## Where next

- [**Ch 2 — Core Mappings**](../ch02-core-mappings/README.md) — what `Module`,
  `Reg`, `Wire`, `when`, and `Mux` actually become.
- [**Ch 3 — Aggregates, Memory, and Reading Generated Code**](../ch03-aggregates/README.md)
  — `Vec`, `Bundle`, `SInt`, memories, FSMs, and the toolchain artefacts.
- Back to the [appendix index](../README.md).

## References

- [SystemVerilog LRM (IEEE 1800)](https://ieeexplore.ieee.org/document/10458102)
- [Verilator](https://www.veripool.org/verilator/) — lints and simulates the SV
  you generate; see [Ch 9 §6](../ch09-integration/README.md#6-lint-and-ci-for-generated-sv)
