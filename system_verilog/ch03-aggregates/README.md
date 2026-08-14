# Chapter 3 — Aggregates, Memory, and Reading Generated Code

> **Audience**: every Chisel user, after [A2](../ch02-core-mappings/README.md)
> **Goal**: read the constructs that do not map one-to-one — `Vec`, `Bundle`, `SInt`, memories, FSMs — and recognize the toolchain artefacts that fill every generated file
> **Time budget**: About 3 days

[A2](../ch02-core-mappings/README.md) covered the constructs with a tidy
one-to-one mapping. These do not have one: an aggregate is flattened, a memory
becomes its own module, an enum loses its names. This part also covers the
scaffolding — temporaries, source locators, randomization blocks — that makes a
first look at generated code intimidating, and closes with the single most
surprising fact about firtool's output.

*Conventions: paths are relative to this directory; commands run from here.*

## Build and run

```
$ sbt "runMain Generate"                  # emit all 7 designs into generated/
$ sbt "runMain Generate list"             # show the available names
$ sbt "runMain Generate VecExample"       # just one
```

**Every code block is labelled with its path**: `` `src/main/scala/…` `` for
Chisel, `` `generated/…` `` for emitted SystemVerilog.

---

### 2.7 Vec (Hardware Array)

`src/main/scala/VecExample.scala`
```scala
class VecExample extends Module {
  val io = IO(new Bundle {
    val idx  = Input(UInt(2.W))
    val data = Input(Vec(4, UInt(8.W)))
    val out  = Output(UInt(8.W))
  })
  io.out := io.data(io.idx)
}
```

**Generated SystemVerilog** (flattened-port form)

`generated/VecExample.sv`
```systemverilog
module VecExample(
  input        clock,
               reset,
  input  [1:0] io_idx,
  input  [7:0] io_data_0,
               io_data_1,
               io_data_2,
               io_data_3,
  output [7:0] io_out
);

  wire [3:0][7:0] _GEN = {{io_data_3}, {io_data_2}, {io_data_1}, {io_data_0}};
  assign io_out = _GEN[io_idx];
endmodule
```

The `Vec` is flattened at the port boundary into `io_data_0` … `io_data_3`, then
immediately **re-packed** inside the module into `_GEN`, a packed 4×8 array that
can be indexed by a signal. This is one of the few places where generated code
genuinely needs SystemVerilog: `wire [3:0][7:0]` is not legal Verilog-2001.

| Chisel | SystemVerilog |
|---|---|
| `Vec(N, T)` as a port | flattened to `io_data_0`, `io_data_1`, ... |
| `vec(idx)` with dynamic index | packed-array index or mux tree |
| `vec(2)` with constant index | direct reference to `io_data_2` |

**Two important notes**

1. A dynamic `vec(idx)` read where `idx` can exceed the Vec bounds is well-defined in Chisel-land but the packed-array indexed read returns X in SV simulation for out-of-range indices. firtool handles the lowering, but keep index widths exactly matched to Vec sizes when you can.
2. The packed array (`wire [3:0][7:0]`) is the default. `--lowering-options=disallowPackedArrays` forces a flattened mux tree instead, which matters for waveform viewing and for tools that predate SystemVerilog — see Levels B and C.

### 2.8 Nested Bundles and Flattening

`src/main/scala/NestedExample.scala`
```scala
class Nested extends Bundle {
  val x = UInt(8.W)
  val y = UInt(8.W)
}

class NestedExample extends Module {
  val io = IO(new Bundle {
    val in  = Input(new Nested)
    val out = Output(new Nested)
  })
  io.out.x := io.in.x + 1.U
  io.out.y := io.in.y - 1.U
}
```

`generated/NestedExample.sv`
```systemverilog
module NestedExample(
  input        clock,
               reset,
  input  [7:0] io_in_x,
               io_in_y,
  output [7:0] io_out_x,
               io_out_y
);

  assign io_out_x = io_in_x + 8'h1;
  assign io_out_y = io_in_y - 8'h1;
endmodule
```

| Chisel | SystemVerilog |
|---|---|
| Bundle hierarchy (`io.in.x`) | flattened with underscores (`io_in_x`) |

The Bundle exists only at elaboration time. SV has grouping constructs (`struct`, `interface`), but Chisel does not use them for ports — see [Ch 7 §3](../ch07-synthesizable-rtl/README.md#3-sv-interface-vs-chisel-bundle) for why.

### 2.9 SInt and Signed Arithmetic (New Section)

Chisel's `SInt` carries signedness in the type; SV `logic` vectors are unsigned by default and signedness is applied per-expression with `$signed`.

`src/main/scala/SignedExample.scala`
```scala
class SignedExample extends Module {
  val io = IO(new Bundle {
    val a   = Input(SInt(8.W))
    val b   = Input(SInt(8.W))
    val gt  = Output(Bool())
    val shr = Output(SInt(8.W))
  })
  io.gt  := io.a > io.b
  io.shr := io.a >> 2
}
```

`generated/SignedExample.sv`
```systemverilog
module SignedExample(
  input        clock,
               reset,
  input  [7:0] io_a,       // note: port type is a plain vector!
               io_b,
  output       io_gt,
  output [7:0] io_shr
);

  assign io_gt = $signed(io_a) > $signed(io_b);
  assign io_shr = {{2{io_a[7]}}, io_a[7:2]};
endmodule
```

**Key points**

- The **ports carry no signedness** — an `SInt(8.W)` port is emitted as a plain `input [7:0]`. Signedness lives only in the operators, and it is Chisel's type system that remembers which is which.
- The **comparison** does use a `$signed(...)` cast, so `io_gt` reads the way you would expect.
- The **shift** does not use `>>>` at all. Because the shift amount is a constant, firtool implements the arithmetic shift structurally: `{{2{io_a[7]}}, io_a[7:2]}` takes the top six bits and prepends two copies of the sign bit. That *is* an arithmetic shift right by 2 — sign replication plus a slice — just spelled as wiring rather than as an operator. Expect `>>>` only when the shift amount is a signal.
- The same idiom appears for sign extension to a wider type: `{{8{io_a[7]}}, io_a}`.
- If you're comparing waveform values, remember the viewer will show the raw unsigned bit pattern unless you tell it the signal is signed (e.g., `8'hFF` for -1).

### 2.10 Memories: `Mem` and `SyncReadMem` (New Section)

Memories are the biggest Chisel construct not covered by the sections above.

`src/main/scala/MemExample.scala`
```scala
class MemExample extends Module {
  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val raddr = Input(UInt(4.W))
    val rdata = Output(UInt(8.W))
  })
  val mem = SyncReadMem(16, UInt(8.W))   // 16 entries, synchronous read
  when(io.wen) {
    mem.write(io.waddr, io.wdata)
  }
  io.rdata := mem.read(io.raddr)
}
```

The memory is **always** factored into its own module, named for its geometry,
and your module just instantiates it:

`generated/MemExample.sv`
```systemverilog
module mem_16x8(
  input  [3:0] R0_addr,
  input        R0_en,
               R0_clk,
  output [7:0] R0_data,
  input  [3:0] W0_addr,
  input        W0_en,
               W0_clk,
  input  [7:0] W0_data
);

  reg [7:0] Memory[0:15];
  reg       _R0_en_d0;
  reg [3:0] _R0_addr_d0;
  always @(posedge R0_clk) begin
    _R0_en_d0 <= R0_en;
    _R0_addr_d0 <= R0_addr;      // SyncReadMem: the ADDRESS is registered
  end
  always @(posedge W0_clk) begin
    if (W0_en & 1'h1)
      Memory[W0_addr] <= W0_data;
  end
  assign R0_data = _R0_en_d0 ? Memory[_R0_addr_d0] : 8'bx;
endmodule

module MemExample(
  input        clock,
               reset,
               io_wen,
  input  [3:0] io_waddr,
  input  [7:0] io_wdata,
  input  [3:0] io_raddr,
  output [7:0] io_rdata
);

  mem_16x8 mem_ext (
    .R0_addr (io_raddr),
    .R0_en   (1'h1),
    .R0_clk  (clock),
    .R0_data (io_rdata),
    .W0_addr (io_waddr),
    .W0_en   (io_wen),
    .W0_clk  (clock),
    .W0_data (io_wdata)
  );
endmodule
```

`Mem` (combinational read) produces the *same module shape* — only the read path
changes, losing the address register:

`generated/AsyncMemExample.sv`
```systemverilog
  assign R0_data = R0_en ? Memory[R0_addr] : 8'bx;
```

**Key points**

- `reg [7:0] Memory[0:15]` is an **unpacked array** — the `[0:15]` after the name declares 16 separate words, unlike the packed `[7:0]` before the name which declares bits within a word. This is the standard memory idiom that FPGA and ASIC tools recognize for RAM inference.
- **The separate module is the point.** The generic `R0_*` / `W0_*` naming (read port 0, write port 0) exists so the whole module can be swapped for a technology macro. FPGA and ASIC flows want their own block-RAM or compiled-SRAM cells rather than an inferred array, and isolating the memory behind a regular interface makes that substitution mechanical ([Ch 9 §2](../ch09-integration/README.md#2-memories-and-sram-macros)).
- `SyncReadMem` registers the read address (`_R0_addr_d0`) — that is where the one-cycle read latency comes from. `Mem` does not, so its read is combinational; it is suitable for register files and small lookups, but maps to flip-flops + mux rather than SRAM in ASIC synthesis.
- The `8'bx` on a disabled read is why reading with `en` low gives undefined data rather than zero.
- Each memory module also carries a randomization block that fills the array with random values at time 0 (a `for` loop over `Memory[i]`).
- Chisel guarantees defined read-during-write behavior only if you follow the documented `SyncReadMem` semantics; the emitted SV encodes whichever behavior was chosen (read-first shown above).

### 2.11 ChiselEnum and State Machines (New Section)

`src/main/scala/Fsm.scala`
```scala
import chisel3.util._

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
```

**Generated SystemVerilog** (essence)

`generated/Fsm.sv`
```systemverilog
  reg [1:0] state;
  always @(posedge clock) begin
    if (reset)
      state <= 2'h0;                              // sIdle = 0
    else begin
      automatic logic [3:0][1:0] _GEN;
      _GEN = {{state}, {2'h0}, {2'h2}, {io_start ? 2'h1 : state}};
      state <= _GEN[state];
    end
  end
  assign io_done = state == 2'h2;
```

**There is no `case` statement.** firtool turned the whole `switch` into a
four-entry packed lookup table indexed by the state register. Read `_GEN` right
to left to recover the transitions: entry 0 (`sIdle`) goes to `2'h1` when
`start`, else holds; entry 1 (`sRun`) goes to `2'h2`; entry 2 (`sDone`) goes to
`2'h0`; entry 3 is unreachable and holds.

This is also one of the few places `logic` appears in real RTL rather than in
simulation scaffolding — `automatic logic` declares a local variable inside the
`always` block.

**Key points**

- ChiselEnum values become sequential unsigned encodings (0, 1, 2, ...) in a vector just wide enough. The symbolic names are *gone* in the SV — waveforms show `2'h1`, not `sRun`. Keep your enum ordering handy when debugging, or use simulator/waveform features that re-map values.
- SV has its own `enum` typedef feature which preserves names in simulation, but Chisel does not emit it.
- Chisel `switch`/`is` may lower to a `case`, an if-else chain, **or** an indexed lookup table as above — the shape depends on what firtool can prove about the values, so do not go looking for a particular one.

### 2.12 `DontCare` (New Section)

`src/main/scala/DontCareExample.scala`
```scala
io.used := io.in
io.out  := DontCare
```

Chisel's `DontCare` marks a signal as intentionally unspecified. The lowering is
free to pick any value for it:

`generated/DontCareExample.sv`
```systemverilog
  assign io_used = io_in;
  assign io_out = 8'h0;
```

**In this default configuration `DontCare` became `8'h0`, not `8'bx`.** That is
worth knowing before you go hunting for X's: firtool is free to pick any value
for a don't-care, and picking a constant is the friendlier choice for 2-state
simulators and for synthesis.

Where X *does* survive is a disabled memory read — `MemExample` above emits
`assign R0_data = _R0_en_d0 ? Memory[_R0_addr_d0] : 8'bx;`. So the rule is not
"`DontCare` gives you X"; it is "the tool may emit X where a value is
unspecified, and may equally emit a constant."

Either way the design rule is unchanged: a `DontCare` value that is ever actually
consumed is a bug, and you cannot rely on it reading as either X or zero.

---

## 3. Reading generated code

These patterns don't come from any single Chisel construct — they are artifacts of the toolchain that you must learn to recognize.

### 3.1 `_GEN_*` and `_T_*` Temporaries

FIRRTL/CIRCT splits complex expressions into named intermediate signals:

`generated/VecExample.sv`
```systemverilog
  wire [3:0][7:0] _GEN = {{io_data_3}, {io_data_2}, {io_data_1}, {io_data_0}};
  assign io_out = _GEN[io_idx];
```

- `_GEN*`: signals the compiler synthesized itself — here the packed array it
  built to make a `Vec` dynamically indexable.
- `_T_*`: intermediate expression results, numbered in elaboration order.

**Calibrate your expectations: modern firtool emits far fewer of these than older
FIRRTL did.** Across the seven designs in this chapter there are exactly **five**
`_GEN` occurrences and **zero** `_T_*`. Older tutorials and Stack Overflow
answers show output littered with `_T_37`; that is not what 1.62 produces.
Expression results are inlined into the consuming `assign` instead, which is why
[Ch 2 §2.4](../ch02-core-mappings/README.md#24-wire) lost its `mid` wire entirely.

When one of these shows up in a waveform, use the source locator comment (next section) to find its origin, or restructure the Chisel code with named `val`s / `.suggestName` if you need it human-readable.

### 3.2 Source Locator Comments

Chisel records the originating Scala source position of every statement:

`generated/VecExample.sv` (emitted **without** `-strip-debug-info`)
```systemverilog
module VecExample(	// src/main/scala/VecExample.scala:4:7
  input        clock,	// src/main/scala/VecExample.scala:4:7
               reset,	// src/main/scala/VecExample.scala:4:7
  input  [1:0] io_idx,	// src/main/scala/VecExample.scala:5:14
  input  [7:0] io_data_0,	// src/main/scala/VecExample.scala:5:14
               io_data_1,	// src/main/scala/VecExample.scala:5:14
               io_data_2,	// src/main/scala/VecExample.scala:5:14
               io_data_3,	// src/main/scala/VecExample.scala:5:14
  output [7:0] io_out	// src/main/scala/VecExample.scala:5:14
);

  wire [3:0][7:0] _GEN = {{io_data_3}, {io_data_2}, {io_data_1}, {io_data_0}};	// src/main/scala/VecExample.scala:10:10
  assign io_out = _GEN[io_idx];	// src/main/scala/VecExample.scala:4:7, :10:10
```

The format is `path:line:column`. (Older FIRRTL wrote `@[Adder.scala 12:15]`;
you will still see that spelling in older documentation.) A line can carry
**several** locators when firtool merged expressions from different source lines
— `:4:7, :10:18` above means "the module declaration and line 10 both
contributed", with the leading path elided on repeats. **This is your primary navigation tool** when mapping SV (or an error message quoting SV) back to Chisel. Synthesis warnings, lint messages, and simulator errors that point at a line of SV can be traced to Chisel source in seconds using these comments. They can be suppressed for production output (`locationInfoStyle=none`, [Ch 11 §3.2](../ch11-hierarchy/README.md#32-firtool-as-the-contract-point)).

### 3.3 Automatic Name Suffixes

When names collide, uniquifying suffixes `_0`, `_1`, ... are appended:

*illustrative — the worked example lives in [Ch 2 §2.2](../ch02-core-mappings/README.md#22-register)*
```scala
val reg = RegInit(0.U(8.W))       // appears as reg_0 in SV ("reg" is a Verilog keyword!)
```

Keyword collisions are one non-obvious source of renaming: Scala `val reg`, `val wire`, `val module`, `val logic` are all legal Chisel but collide with SV keywords, so firtool renames them (`reg_0` etc.). Instance and module name collisions get similar suffixes (`Adder`, `Adder_1`). To keep control of names, use meaningful `val` names, `.suggestName(...)` for signals, and `desiredName` for modules ([Ch 4 §1.3](../ch04-names-waveforms/README.md#13-name-control-apis)).

### 3.4 Register Randomization Blocks (New Section)

Near the bottom of almost every firtool-generated module you will find something like:

`generated/Fsm.sv`
```systemverilog
  `ifdef ENABLE_INITIAL_REG_
    `ifdef FIRRTL_BEFORE_INITIAL
      `FIRRTL_BEFORE_INITIAL
    `endif
    initial begin
      automatic logic [31:0] _RANDOM[0:0];
      `ifdef INIT_RANDOM_PROLOG_
        `INIT_RANDOM_PROLOG_
      `endif
      `ifdef RANDOMIZE_REG_INIT
        _RANDOM[/*Zero width*/ 1'b0] = `RANDOM;
        state = _RANDOM[/*Zero width*/ 1'b0][1:0];
      `endif
    end
    `ifdef FIRRTL_AFTER_INITIAL
      `FIRRTL_AFTER_INITIAL
    `endif
  `endif
```

Each module also opens with ~40 lines of `` `define `` scaffolding that sets up
`RANDOMIZE`, `RANDOM`, `INIT_RANDOM_PROLOG_` and friends. The `ENABLE_INITIAL_REG_`
guard is itself defined under `` `ifndef SYNTHESIS ``, so the whole thing
disappears in synthesis. (Macro names vary across firtool versions.)

You can suppress all of it with `--disable-all-randomization`, which is the
quickest way to make a generated file readable:

```
$ sbt "runMain Generate Fsm"    # with the scaffolding
```
vs. passing `--disable-all-randomization` through `firtoolOpts` ([Ch 6 §5](../ch06-printf-assert/README.md#5-the-firrtlcirct-pipeline)).

**What it is**: simulation-only scaffolding that can initialize every register to a random value at time 0, activated by defining the corresponding macros at compile time.

**Why it exists**: real silicon powers up with unpredictable register contents. In 4-state simulators, uninitialized registers are X, which is pessimistic-but-visible; in 2-state simulators like Verilator they'd silently be 0, which can mask reset bugs. Randomizing initial state flushes out designs that accidentally rely on registers powering up as zero.

**What you need to know now**: these blocks are *not your logic*; ignore them when reading functionality. Never rely on the "everything starts at 0" behavior you might observe in a simulator with randomization off — if a register needs a known start value, give it one with `RegInit`.

### 3.5 `` `ifndef SYNTHESIS `` Blocks

Chisel's `printf`, `assert`, and `stop` emit simulation-only SV wrapped in:

*illustrative — the general shape; [Ch 6](../ch06-printf-assert/README.md) shows real `$fwrite`/`$error` output*
```systemverilog
`ifndef SYNTHESIS
  // $fwrite / $error / $fatal calls here
`endif
```

(None of this chapter's designs uses `printf` or `assert`, so this shape
does not appear in `generated/` — Chapter 13 of the tutorial and [Ch 6](../ch06-printf-assert/README.md) of this
appendix both cover it with real output.)

Synthesis flows define the `SYNTHESIS` macro, so these vanish from the netlist. Full details in [Ch 6](../ch06-printf-assert/README.md).

### 3.6 Why there is no `always_ff` or `always_comb`

[A1](../ch01-syntax/README.md) taught you `always_comb` and
`always_ff` because they are how you *write* SystemVerilog. You will not find either one in Chisel-generated code.
Counting across the seven designs in this project:

| construct | occurrences |
|---|---|
| `always @(posedge …)` | 8 |
| `always_ff` | **0** |
| `always_comb` | **0** |

Registers come out as `reg` in a plain `always @(posedge clock)`; combinational
logic comes out as continuous `assign`, not as a procedural block. This is not a
quirk of one design, and it is not something you can switch on: the output is
unchanged under `--disable-all-randomization`, `--emit-separate-always-blocks`,
`--lowering-options=disallowLocalVariables`, `--lowering-options=noAlwaysComb`,
and `--lowering-options=verifLabels`.

It is not a missing feature either — the strings `always_ff`, `always_comb`, and
`always_latch` are all present in the firtool binary. They belong to CIRCT's `sv`
dialect, which a hand-built design can target; the Chisel → FIRRTL → SV lowering
path simply does not produce them.

**Why this is reasonable.** `always_ff` and `always_comb` are *authoring*
constructs. Their value is making the tool check a human's intent — "you said
flip-flop, did you write one?", and for `always_comb`, "did you forget a branch
and infer a latch?" Both checks are redundant here: the generator guarantees the
intent, and Chisel catches the incomplete-assignment case earlier and with a
better message, at generation time ([Ch 7 §2](../ch07-synthesizable-rtl/README.md#2-latch-inference--the-bug-chisel-cannot-express)). What remains is
`always_ff`'s cost — it imposes SystemVerilog's single-driver restriction on
every variable it writes, constraining what the emitter may do elsewhere in the
same file.

*(That last paragraph is design rationale, not measurement. What is measured is
that firtool never emits these on the FIRRTL path and that no flag changes it.)*

**Is the output Verilog-2001, then?** Partly, and the split is worth knowing.
The register and logic idiom is Verilog-2001 style — `always @(posedge)`, `reg`,
`wire`, `assign` — and simple designs like `Adder`, `CounterExample`, and
`WhenExample` are genuinely Verilog-2001-clean. But packed arrays
(`wire [3:0][7:0]` in [§2.7](#27-vec-hardware-array)), `automatic logic`
(the FSM in [§2.11](#211-chiselenum-and-state-machines-new-section)), and
the randomization scaffolding all require SystemVerilog.

The accurate description is **"SystemVerilog with a Verilog-2001-flavored
register idiom."** Note this also rules out "the emitter targets old tools" as
the explanation for avoiding `always_ff` — if that were the goal, it would not
emit packed arrays either. The consistent reading is narrower: firtool avoids the
construct that *restricts* it, while freely using constructs that merely
*express* something.

---

---

## Where next

- [**Ch 4 — Names, Signals, and Waveforms**](../ch04-names-waveforms/README.md)
  — finding these signals when you need to debug.
- Back to the [appendix index](../README.md).

## References

- [Chisel documentation](https://www.chisel-lang.org/docs)
- [CIRCT / firtool](https://circt.llvm.org/docs/)
