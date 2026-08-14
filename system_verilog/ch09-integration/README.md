# Chapter 9 — Integration and Physical Design

> **Audience**: anyone whose Chisel has to coexist with other people's RTL, or reach a real fab or FPGA
> **Goal**: instantiate existing SystemVerilog, get memories replaced by macros, keep the signal names your constraints depend on, and lint the output in CI
> **Time budget**: About 1 week

Everything so far has treated the generated `.sv` as an endpoint. It is not — it
is an input to a flow full of other people's tools and other people's RTL. This
part is about the seams.

*Conventions: paths are relative to this directory; commands run from here.
Every SystemVerilog block is real captured output.*

## Build and run

```
$ sbt "runMain Generate"       # emit the designs into generated/
$ sbt "runMain SramMacro"      # the same memory, extracted for macro replacement
```

---

## 1. BlackBox: integrating existing SystemVerilog

A `BlackBox` is a Chisel module with **no Chisel body** — you declare its ports
and Chisel instantiates it, trusting you to supply the Verilog.

`src/main/resources/ExtAnd.sv`
```systemverilog
module ExtAnd #(
  parameter WIDTH = 8
) (
  input  wire [WIDTH-1:0] a,
  input  wire [WIDTH-1:0] b,
  output wire [WIDTH-1:0] y
);
  assign y = a & b;
endmodule
```

`src/main/scala/BlackBoxes.scala`
```scala
class ExtAnd(width: Int) extends BlackBox(Map("WIDTH" -> width))
    with HasBlackBoxResource {
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val y = Output(UInt(width.W))
  })
  addResource("/ExtAnd.sv")
}
```

`generated/UseExtAnd.sv`
```systemverilog
module UseExtAnd(
  input        clock,
               reset,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_out
);

  ExtAnd #(
    .WIDTH(8)
  ) ext (
    .a (io_a),
    .b (io_b),
    .y (io_out)
  );
endmodule
module ExtAnd #(
  parameter WIDTH = 8
) (
  input  wire [WIDTH-1:0] a,
  input  wire [WIDTH-1:0] b,
  output wire [WIDTH-1:0] y
);
  assign y = a & b;
endmodule
```

Four things to notice.

**No `io_` prefix on the BlackBox ports.** Inside a `BlackBox`, the bundle field
names are used verbatim — `a`, `b`, `y`, not `io_a`. They must match the external
module's port names **exactly**, and a mismatch is a link-time failure in the
simulator or synthesizer, not a Chisel error. This is the most common BlackBox
mistake.

**A real SystemVerilog `parameter`.** `BlackBox(Map("WIDTH" -> width))` emits
`ExtAnd #(.WIDTH(8))`. This is the *only* place a parameterized module
instantiation appears in Chisel output — everywhere else parameters are resolved
at elaboration and produce distinct modules
([Ch 2 §2.1](../ch02-core-mappings/README.md#21-module-and-bundle)). Here the parameter
belongs to somebody else's code, so it survives.

**`addResource` inlines the file.** The `.sv` from `src/main/resources/` is
appended to the output, so the emitted file is self-contained. `HasBlackBoxInline`
does the same with a string literal in the Scala source, which is convenient for
short wrappers.

**Clock and reset are not connected automatically.** `ExtAnd` is combinational so
it does not matter here, but a sequential BlackBox needs `clock` and `reset`
declared as ports and wired explicitly. Forgetting is a classic bug — the module
simply never runs.

---

## 2. Memories and SRAM macros

### 2.1 The default

A `SyncReadMem` already emits as its own module with generic port names
([Ch 3 §2.10](../ch03-aggregates/README.md#210-memories-mem-and-syncreadmem-new-section)).
That isolation is deliberate: it is what makes substitution possible.

### 2.2 Extracting it: `--repl-seq-mem`

For a real ASIC flow you do not want an inferred `reg` array — you want a
compiled SRAM from the foundry. firtool's `--repl-seq-mem` turns the memory into
an **undefined external module** plus a description file:

`src/main/scala/Generate.scala`
```scala
object SramMacro extends App {
  println(ChiselStage.emitSystemVerilog(new Sram,
    firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization",
                        "--repl-seq-mem", "--repl-seq-mem-file=generated/sram.conf")))
}
```

Running `sbt "runMain SramMacro"` gives a wrapper hierarchy ending in a module
that is **instantiated but never defined**:

```systemverilog
module mem(
  input  [3:0] R0_addr,
  input        R0_clk,
  output [7:0] R0_data,
  input  [3:0] W0_addr,
  input        W0_en,
               W0_clk,
  input  [7:0] W0_data
);

  mem_ext mem_ext (
    .R0_addr (R0_addr),
    .R0_en   (1'h1),
    .R0_clk  (R0_clk),
    .R0_data (R0_data),
    .W0_addr (W0_addr),
    .W0_en   (W0_en),
    .W0_clk  (W0_clk),
    .W0_data (W0_data)
  );
```

`mem_ext` has no body. That is the hole a memory compiler fills — the `.conf`
file describes the geometry and ports so a script can generate or select the
right macro and drop it in.

**Verify the read-under-write semantics.** A macro's behaviour when reading and
writing the same address in one cycle may differ from what `SyncReadMem`
specified. This is a real source of silicon bugs, and neither tool will warn you.

### 2.3 Initialization

An SRAM macro cannot be initialized by an `initial` block the way an inferred
array can. Designs that rely on preloaded memory contents need a loader, a ROM,
or a separate initialization path.

---

## 3. Names, `dontTouch`, and physical design

Everything downstream addresses your design **by name**: SDC constraints, UPF
power intent, `bind` statements, and physical-design scripts all use hierarchical
paths. Chapter 4 showed those names are not guaranteed. Here is the tool for when
you need one.

`src/main/scala/DontTouch.scala`
```scala
val vanishes = io.a & io.b            // inlined away
val survives = dontTouch(WireInit(io.a | io.b))  // kept
io.out := vanishes | survives
```

`generated/Probed.sv`
```systemverilog
  wire [7:0] survives = io_a | io_b;
  assign io_out = io_a & io_b | survives;
```

`vanishes` is gone — inlined into the `assign`, exactly as
[Ch 4 §1](../ch04-names-waveforms/README.md#1-what-survives-and-what-does-not)
predicted. `survives` is still there as a named wire, because `dontTouch` tells
every optimization stage to leave it alone.

Use it for signals a testbench probes, a constraint references, or a debug port
observes. **Do not spray it around**: each one blocks optimization, and a design
full of `dontTouch` is a design that synthesizes badly.

Related controls: `override def desiredName` for module names
([Ch 4 §1.3](../ch04-names-waveforms/README.md#13-name-control-apis)), and
`.suggestName` for signals.

---

## 4. SDC in one page

Timing constraints are the contract between your RTL and the timing tools. A
minimal starting set:

*illustrative — SDC is an input to synthesis, not something Chisel emits*
```tcl
# Clocks
create_clock -name clk -period 10.0 [get_ports clock]

# Clock quality
set_clock_uncertainty 0.1 [get_clocks clk]

# I/O timing, relative to the clock
set_input_delay  -clock clk 2.0 [remove_from_collection [all_inputs] [get_ports clock]]
set_output_delay -clock clk 2.0 [all_outputs]

# Asynchronous domains (see C2 section 2.4)
set_clock_groups -asynchronous -group {clk_a} -group {clk_b}
```

Two traps worth knowing early. **`set_multicycle_path -setup N` without a
matching `-hold N-1`** moves the hold check in a way that is almost never what
you meant. And **any constraint naming an internal path** depends on a generated
name surviving; regenerate and re-check whenever the design changes.

---

## 5. Reading synthesis results

You will get back area, timing (worst negative slack and total negative slack),
and power. When timing fails, the fixes that work from the Chisel side are:

- **Pipeline it** — insert a register stage; usually the biggest single win.
- **Shorten the critical path** — restructure a long `when`/`elsewhen` priority
  chain into a balanced mux tree, or a `reduce` into a `reduceTree`
  ([Chapter 10](../../ch10-hardware-generators/README.md) measures exactly this).
- **Narrow the arithmetic** — firtool already narrows what it can prove
  ([Ch 2 §2.4](../ch02-core-mappings/README.md#24-wire)), but it cannot know your value
  ranges.
- **Check you did not `dontTouch` the critical path.**

Read the timing report's path back through the source locators to find the
Chisel line responsible.

---

## 6. Lint and CI for generated SV

Generated code should still be linted — not because Chisel emits bad Verilog, but
because lint catches integration problems and toolchain drift. Verilator does
this without a testbench:

```
$ verilator --lint-only -Wall generated/UseExtAnd.sv
```

Run against this tutorial's own output, `-Wall` immediately produces two classes
of warning you need to know about:

```
%Warning-UNUSEDSIGNAL: generated/Arbiter3Loop.sv:3:16: Signal is not used: 'clock'
%Warning-UNUSEDSIGNAL: generated/Arbiter3Loop.sv:4:16: Signal is not used: 'reset'
```

```
%Warning-UNUSEDSIGNAL: generated/WhenCounter.sv:75:30: Signal is not driven, nor used: '_RANDOM'
```

Neither is a defect. The first is the unavoidable consequence of `Module` always
emitting `clock` and `reset` even for combinational logic
([Ch 2 §2.1](../ch02-core-mappings/README.md#21-module-and-bundle)) — use `RawModule` if
you care. The second is the randomization scaffolding
([Ch 3 §3.4](../ch03-aggregates/README.md#34-register-randomization-blocks-new-section)),
removable with `--disable-all-randomization`.

**The lesson for CI: baseline your warnings.** Lint generated code with a fixed
waiver list rather than chasing zero, and treat *new* warnings after a toolchain
bump as the signal. A firtool upgrade that changes emission style will show up
here first, which is exactly what you want.

---

## 7. Pitfalls

**BlackBox port names must match exactly** — and a mismatch fails at link time,
not in Chisel.

**BlackBox clock/reset left dangling** — a sequential BlackBox needs them wired
explicitly.

**`dontTouch` sprawl** — each one blocks optimization.

**Memory macro semantics mismatch** — verify read-under-write against the macro's
datasheet, not against `SyncReadMem`'s documentation.

**Name drift breaking SDC/UPF/bind silently** — nothing errors; the constraint
just stops applying.

---

## 8. Exercises

1. Break the `ExtAnd` BlackBox deliberately: rename a bundle field so it no
   longer matches the SV. Where does the failure appear — Chisel, firtool, or
   Verilator? Use `verilator --lint-only` on the output to find out.
2. Run `sbt "runMain SramMacro"` and find the module with no body. Write the port
   list a memory compiler would need to fill it.
3. Add `dontTouch` to a signal on a critical path, then remove it, and compare
   the generated SystemVerilog. What did the optimizer do differently?
4. Lint every `.sv` this appendix generates and build a waiver list. How many
   distinct warning classes are there, and how many are genuine?

---

## Where next

- [**Ch 7 — Writing Synthesizable RTL**](../ch07-synthesizable-rtl/README.md)
- [**Ch 8 — Clock Gating, CDC, and Reset**](../ch08-cdc/README.md)
- [**Ch 10 — Verification at Scale**](../ch10-verification/README.md)
  takes integration further: SVA, coverage, formal, and UVM.
- Back to the [appendix index](../README.md).
