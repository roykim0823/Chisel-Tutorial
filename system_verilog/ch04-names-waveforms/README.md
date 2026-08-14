# Chapter 4 — Names, Signals, and Waveforms

> **Audience**: anyone who debugs a Chisel design
> **Goal**: find any Chisel signal in the generated SystemVerilog and in a waveform — and understand why some of them are not there at all
> **Time budget**: About 4 days

The first thing that goes wrong when you debug generated RTL is not the logic.
It is that you cannot **find** anything. A synthesis warning names a signal you
never wrote; a waveform lists names that do not match your Chisel; the register
you want to inspect has vanished. This part is about closing that gap.

*Conventions: paths are relative to this directory, and commands are run from
here. Every SystemVerilog block is real captured output — see below.*

## Build and run the examples

```
$ sbt "runMain Generate"          # emit all 5 designs into generated/
$ sbt "runMain Generate list"     # show the available names
$ sbt "runMain Generate Naming"   # just one
```

**Every code block below is labelled with its path**: `` `src/main/scala/…` ``
for Chisel you wrote, `` `generated/…` `` for SystemVerilog firtool emitted.
`generated/` is git-ignored and created by `runMain Generate`.

---

## 1. What survives, and what does not

Before the naming rules, the blunt fact: **a name can only survive if the signal
survives**, and many do not.

`src/main/scala/Naming.scala`
```scala
class Naming extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  val namedWire = io.a & io.b        // a named val
  val namedReg  = RegNext(namedWire) // a named register

  // An anonymous intermediate: nothing names (a|b), so it has no name to keep.
  io.out := namedReg | (io.a | io.b)
}
```

`generated/Naming.sv`
```systemverilog
module Naming(
  input        clock,
               reset,
  input  [7:0] io_a,
               io_b,
  output [7:0] io_out
);

  reg [7:0] namedReg;
  always @(posedge clock)
    namedReg <= io_a & io_b;
  assign io_out = namedReg | io_a | io_b;
endmodule
```

Two named `val`s went in; one came out.

- **`namedReg` survived.** Registers are state — there is nothing to inline them
  into, so a `Reg` essentially always appears, carrying its `val` name.
- **`namedWire` did not.** It fed exactly one consumer, so firtool substituted
  the expression (`io_a & io_b`) directly into the `always` block. The name had
  nothing to attach to.

This is the rule to internalize: **naming a combinational value in Chisel is a
hint, not a guarantee.** If you need a specific combinational signal to exist in
the output — to probe it, constrain it, or watch it — naming it is not enough;
see `dontTouch` in [Ch 9 §3](../ch09-integration/README.md#3-names-donttouch-and-physical-design).

### 1.1 Signal name mapping

| Chisel | SystemVerilog |
|---|---|
| `io.a` | `io_a` |
| `io.data.x` | `io_data_x` |
| `io.vec(3)` | `io_vec_3` |
| `val myReg = RegInit(...)` | `myReg` (val name preserved) |
| a combinational `val` used once | **often gone** — inlined into its consumer |
| anonymous intermediate expression | inlined, or `_GEN` if it must be materialized |
| `.suggestName("foo")` | `foo` |
| val name colliding with a Verilog keyword | renamed, e.g. `reg_0` |

Name preservation is much better in current Chisel/firtool than the `_T_37`-era
output older documentation shows — but never *assume* a name survived. Check.

### 1.2 Keyword collisions

Verilog's reserved words are not reserved in Scala, so this compiles fine and is
silently renamed on the way out:

`src/main/scala/Keywords.scala`
```scala
val reg    = RegNext(io.in)      // `reg` is a Verilog keyword
val wire   = io.in ^ 0xFF.U      // `wire` is a Verilog keyword
val output = RegNext(wire)       // `output` is a Verilog keyword
io.out := reg | output
```

`generated/Keywords.sv`
```systemverilog
  reg [7:0] reg_0;
  reg [7:0] output_0;
  always @(posedge clock) begin
    reg_0 <= io_in;
    output_0 <= ~io_in;
  end
  assign io_out = reg_0 | output_0;
```

`reg` → `reg_0` and `output` → `output_0`, with no warning. `wire` does not
appear at all — it was combinational and used once, so it was inlined before the
rename could matter (and note firtool folded `io_in ^ 0xFF` into `~io_in`).

Avoid `reg`, `wire`, `module`, `input`, `output`, `always`, `assign`, `initial`,
`begin`, `end`, and the rest of the reserved lists as Chisel `val` names, and
your names reach the output intact.

### 1.3 Name-control APIs

Two overrides give you explicit control:

`src/main/scala/Naming.scala`
```scala
class NameControl extends Module {
  override val desiredName = "RenamedByDesiredName"
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val plain = RegNext(io.in)
  val hinted = RegNext(io.in).suggestName("chosen_name")
  io.out := plain | hinted
}
```

`generated/RenamedByDesiredName.sv`
```systemverilog
module RenamedByDesiredName(
  input        clock,
               reset,
  input  [7:0] io_in,
  output [7:0] io_out
);

  reg [7:0] plain;
  reg [7:0] chosen_name;
  always @(posedge clock) begin
    plain <= io_in;
    chosen_name <= io_in;
  end
  assign io_out = plain | chosen_name;
```

- **`desiredName`** sets the emitted *module* name. The class is `NameControl`;
  the SystemVerilog module is `RenamedByDesiredName`. This matters most for
  parameterized generators — `override def desiredName = s"Fifo_d${depth}"` makes
  variants distinguishable instead of `Fifo`, `Fifo_1`, `Fifo_2`.
- **`.suggestName`** sets a *signal* name. Useful when the `val` name is awkward,
  or when a signal is produced inside a helper function that has no good name.

Note both registers landed in one merged `always` block — firtool merges
registers sharing a clock, so do not look for a block boundary per register.

### 1.4 Module and instance names in a hierarchy

`src/main/scala/Hierarchy.scala`
```scala
class Hierarchy extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(16.W))
    val out = Output(UInt(16.W))
  })
  val small = Module(new Leaf(8))    // instance named `small`
  val big   = Module(new Leaf(16))   // same class, different parameter
  small.io.a := io.a
  big.io.a := io.b
  io.out := big.io.y | small.io.y
}
```

`generated/Hierarchy.sv`
```systemverilog
module Leaf(
  input  [7:0] io_a,
  output [7:0] io_y
);

  assign io_y = ~io_a;
endmodule
module Leaf_1(
  input  [15:0] io_a,
  output [15:0] io_y
);

  assign io_y = ~io_a;
endmodule
module Hierarchy(
  input         clock,
                reset,
  input  [7:0]  io_a,
  input  [15:0] io_b,
  output [15:0] io_out
);

  wire [15:0] _big_io_y;
  wire [7:0]  _small_io_y;
  Leaf small_0 (
    .io_a (io_a),
    .io_y (_small_io_y)
  );
  Leaf_1 big (
    .io_a (io_b),
    .io_y (_big_io_y)
  );
  assign io_out = {_big_io_y[15:8], _big_io_y[7:0] | _small_io_y};
endmodule
```

Three things worth noting.

**One Chisel class became two SystemVerilog modules.** `Leaf(8)` and `Leaf(16)`
are separate elaborations, so they emit `Leaf` and `Leaf_1`. There is no
parameterized module in the output — parameters are resolved at elaboration.

**Instance names mostly survive, but not always.** `big` kept its name; `small`
came out as `small_0`. The `val` name is a strong hint, not a contract.

**Do not hard-code hierarchical paths** anywhere durable on the strength of a
name you saw once. SDC constraints, UPF power intent, and `bind` statements all
address signals by path (`top/small/io_a`), and a rename between builds breaks
them silently. [Ch 9](../ch09-integration/README.md) and [Ch 12](../ch12-silicon/README.md) both return to this; the short version is
that anything referencing a generated path needs regenerating and re-checking
when the design changes.

Notice also the leaf modules have **no `clock` or `reset` port** — `Leaf` uses
neither, and firtool drops unused implicit ports from submodules even though the
top level keeps them.

---

## 2. Vec signals

A `Vec` of registers becomes one register per element, with the index folded into
each element's enable condition:

`src/main/scala/VecNames.scala`
```scala
class VecNames extends Module {
  val io = IO(new Bundle {
    val idx  = Input(UInt(2.W))
    val din  = Input(UInt(8.W))
    val wr   = Input(Bool())
    val dout = Output(UInt(8.W))
  })
  val bank = Reg(Vec(4, UInt(8.W)))
  when(io.wr) { bank(io.idx) := io.din }
  io.dout := bank(io.idx)
}
```

`generated/VecNames.sv`
```systemverilog
  reg  [7:0]      bank_0;
  reg  [7:0]      bank_1;
  reg  [7:0]      bank_2;
  reg  [7:0]      bank_3;
  wire [3:0][7:0] _GEN = {{bank_3}, {bank_2}, {bank_1}, {bank_0}};
  always @(posedge clock) begin
    if (io_wr & io_idx == 2'h0)
      bank_0 <= io_din;
    if (io_wr & io_idx == 2'h1)
      bank_1 <= io_din;
    if (io_wr & io_idx == 2'h2)
      bank_2 <= io_din;
    if (io_wr & (&io_idx))
      bank_3 <= io_din;
  end
  assign io_dout = _GEN[io_idx];
```

**The write side flattens; the read side re-packs.** One `bank(io.idx) := io.din`
became four guarded assignments — the address decode is explicit in the enable
of each register. The read, by contrast, needs a signal-indexable value, so
firtool rebuilds a packed array `_GEN` and indexes that.

For searching a waveform this is the key point: **the elements are named
`bank_0` … `bank_3`, not `bank[0]`.** Search for the underscore form. (The
packed `_GEN` does appear as an array, so both spellings exist in the same file
for the same storage.)

Note `io_idx == 2'h3` was optimized to `&io_idx` — "both bits set". Small
rewrites like this are routine and are a common reason a signal you expect to
see by name is not searchable.

---

## 3. Waveform debugging

### 3.1 Producing a waveform

ChiselTest writes a `.vcd` when you ask for it:

*illustrative — see Chapter 3 and Chapter 13 of the tutorial for runnable versions*
```scala
test(new VecNames).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
  // ... poke / step / expect
}
```

The file lands under `test_run_dir/<test name>/<Module>.vcd`. Open it with
**GTKWave** (open source), **Surfer**, or your simulator's viewer.

**VCD** is the universal format and is plain text — greppable, which is
occasionally the fastest way to answer "does this signal even exist?" It is also
large and slow for big designs; **FST** (GTKWave) and **FSDB** (Verdi, needs a
commercial licence) are the compressed alternatives you will meet in industry.

> **Which artifact is in the waveform?** With the default Treadle backend you are
> looking at a FIRRTL-level simulation, not your SystemVerilog. Signal names are
> usually the same, but signals firtool *later* optimizes away are still present.
> Chapter 13 of the tutorial covers this distinction in detail.

### 3.2 Finding a Chisel signal

Work down this list:

1. **Port?** `io.foo.bar` → search `io_foo_bar`.
2. **Named register?** Search the `val` name directly (`namedReg`, `bank_0`).
3. **Named combinational value?** It may not exist — see
   [Section 1](#1-what-survives-and-what-does-not). Check the `.sv` first with
   `grep`; if it is not there, no waveform will show it.
4. **Keyword collision?** Try the `_0` suffix (`reg_0`, `output_0`).
5. **Still missing?** Find the logic by its *source locator* instead: `grep` the
   `.sv` for your Chisel file and line number, and see what the emitted signal is
   called there.

### 3.3 Tracing a temporary back to source

Generated names such as `_GEN` carry no meaning on their own, but the line that
declares them carries a `path:line:column` comment pointing straight at the
Chisel that produced it. Emit without `-strip-debug-info` and grep:

```
$ grep -n "_GEN" generated/VecNames.sv
```

Then read the locator on that line to find the originating `val`. If you need
the signal to be permanently identifiable, give it a name with `.suggestName`
and, if it is combinational, keep it alive with `dontTouch` ([Ch 9](../ch09-integration/README.md)).

---

## 4. Pitfalls

**Anonymous expressions are unsearchable.** A long chained expression produces
no named signals, so nothing in it can be probed or watched. If a value matters
for debugging, bind it to a `val` — and remember that even that is only a hint
for combinational values.

**Trusting that a `val` name survived.** Keyword collisions, instance-name
uniquification (`small_0`), and inlining all change names silently. Verify in
the `.sv` before you write the name into a constraint file, a `bind`, or a
waveform script.

---

## 5. Exercise: waveform debugging on a FIFO

Use Chapter 11's `RegFifo` (`ch11-example-designs`).

1. Run its test with `WriteVcdAnnotation` and open the `.vcd`.
2. Find the read and write pointers, the full/empty registers, and the storage.
   Which of them kept their Chisel names?
3. Now emit the design (`sbt "runMain Generate"` in that chapter) and `grep` the
   `.sv` for the same names. Is anything present in the waveform but absent from
   the SystemVerilog? Explain why, using
   [Section 1](#1-what-survives-and-what-does-not).
4. Trace one `_GEN` signal back to its Chisel line using the source locators.

---

## Where next

- [**Ch 5 — Clock, Reset, and Interfaces**](../ch05-clock-reset/README.md)
  — what Chisel's implicit direction, clock, and reset become.
- [**Ch 6 — printf, assert, and the Toolchain**](../ch06-printf-assert/README.md)
  — simulation-only constructs and the FIRRTL/CIRCT pipeline.
- Back to the [appendix index](../README.md).

## References

- [Chisel naming cookbook](https://www.chisel-lang.org/docs/cookbooks/naming)
- [GTKWave](https://gtkwave.sourceforge.net/) · [Surfer](https://surfer-project.org/)
- Tutorial [Chapter 3](../../ch03-build-and-testing/README.md) (waveform
  generation) and [Chapter 13](../../ch13-debugging-testing-verification/README.md)
  (debugging, `BoringUtils`, backends)
