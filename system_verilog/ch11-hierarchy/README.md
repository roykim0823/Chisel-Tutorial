# Chapter 11 — Hierarchy and the Pipeline

> **Audience**: anyone whose design got big enough that elaboration itself is a cost, or who needs to change how firtool emits
> **Goal**: control module deduplication, reach across hierarchy, and drive the Chisel → CIRCT → Verilog pipeline deliberately
> **Time budget**: ongoing

Two topics that only matter at scale: what happens when you instantiate the same
module a thousand times, and how to take control of the toolchain that has been
running invisibly under everything so far.

*Conventions: paths are relative to this directory; commands run from here.
SystemVerilog blocks are real captured output.*

## Build and run

```
$ sbt "runMain Generate"       # emit both designs into generated/
```

> **Build note**: this project adds `scalacOptions += "-Ymacro-annotations"` to
> `build.sbt`. Scala 2.13 requires it for the `@instantiable` / `@public`
> annotations below, and without it you get *"value io is not a member of
> Instance[Leaf]"* — a confusing error with a one-line fix.

---

## 1. The deduplication question

### 1.1 The usual framing

`Module(new Leaf)` runs the constructor **once per instance**. Instantiate it a
thousand times and Chisel elaborates a thousand copies, builds a thousand
subtrees, and hands them all to firtool. On a large design this dominates
elaboration time and memory.

The Definition/Instance API is the answer: elaborate **once**, instantiate many
times.

`src/main/scala/Dedup.scala`
```scala
@instantiable
class Leaf extends Module {
  @public val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val y = Output(UInt(8.W))
  })
  io.y := ~io.a
}
```

`src/main/scala/Dedup.scala`
```scala
  val defn = Definition(new Leaf)
  val leaves = Seq.fill(n)(Instance(defn))
```

`@instantiable` marks the class as usable this way; `@public` exposes a member
through an `Instance` handle (an `Instance[Leaf]` is not a `Leaf`, so members are
not visible by default).

### 1.2 What actually changes — and what does not

Here is the part the usual framing leaves out. Emit both versions at `n = 4` and
compare:

```
$ diff <(norm ManyModules) <(norm ManyInstances)
IDENTICAL output
```

Both produce **one** `Leaf` module declaration and four instantiations:

`generated/ManyModules.sv`
```systemverilog
module Leaf(
```

**firtool already deduplicates identical modules.** The plain `Module(new Leaf)`
version does not emit four copies of `Leaf` — the emitted SystemVerilog from both
approaches is byte-identical after normalization.

So Definition/Instance is **not** an output-size optimization. Its benefit is
entirely on the Chisel side: elaboration time and JVM memory, because the
constructor runs once instead of *n* times. If your build is slow or running out
of heap on a big generator, this is the tool. If your generated Verilog looks
bloated, this is not the cause — go look at whether your modules are genuinely
different (different parameters produce `Leaf`, `Leaf_1`, `Leaf_2`, and those are
*supposed* to be distinct).

**When to use it:** many identical instances, and elaboration is measurably
expensive. **When not to:** small designs, or instances that differ by
parameter — `@instantiable` adds ceremony for nothing.

---

## 2. Reaching across hierarchy

Sometimes verification needs a signal that is buried several levels down, and
threading a debug port through every intermediate module is unacceptable.

`BoringUtils.bore` creates the connection and adds the intermediate ports for
you. Tutorial
[Chapter 13](../../ch13-debugging-testing-verification/README.md#accessing-internal-signals-with-boringutils)
has a runnable example that bores a counter out of a submodule:

```scala
BoringUtils.bore(tickGen.cntReg, Seq(io.counter))
```

The generated SystemVerilog shows the ports it added — the abstraction is real
at the Chisel level and completely ordinary in the output.

Two cautions. `BoringUtils` is still marked **experimental**. And bores are
invisible in the source of the modules they pass *through*, so a reader of an
intermediate module sees a port with no local explanation — keep them few and
documented. Chisel also has a newer typed **probe** API for the same job with
tighter rules about direction and lifetime.

---

## 3. Controlling the pipeline

### 3.1 The stages

```
Scala/Chisel  --elaboration-->  CHIRRTL  --Chisel lowering-->  FIRRTL  --firtool-->  SystemVerilog
```

You can print any intermediate stage:

*illustrative*
```scala
import _root_.circt.stage.ChiselStage
println(ChiselStage.emitCHIRRTL(new Leaf))   // straight out of elaboration
println(ChiselStage.emitFIRRTL(new Leaf))    // after Chisel-side lowering
println(ChiselStage.emitSystemVerilog(new Leaf))
```

Comparing CHIRRTL with the final `.sv` is the most direct way to see which
transformation is responsible for a surprise — whether a `Wire` disappeared
during Chisel lowering or during firtool's optimization.

### 3.2 firtool as the contract point

Every option below is verified against the pinned firtool 1.62.0:

| option | effect |
|---|---|
| `--strip-debug-info` | drop `path:line:col` locators |
| `--disable-all-randomization` | drop register/memory randomization scaffolding |
| `--split-verilog -o=<dir>` | one file per module |
| `--emit-chisel-asserts-as-sva` | assertions become concurrent SVA ([D1](../ch10-verification/README.md)) |
| `--repl-seq-mem` | extract memories for macro replacement ([C3](../ch09-integration/README.md)) |
| `--lowering-options=disallowPackedArrays` | flatten packed arrays into mux trees |
| `--lowering-options=locationInfoStyle=none` | suppress locator comments entirely |

`--split-verilog` matters for large designs: one file per module is what most
synthesis and formal flows expect, and it makes diffs between toolchain versions
readable.

**Treat the firtool invocation as a versioned artifact.** It determines what your
whole downstream flow sees. Pin the version, keep the flags in the build, and
diff the generated Verilog when either changes — a toolchain bump that alters
emission style will otherwise show up as mysterious lint or timing noise weeks
later.

### 3.3 Custom passes

CIRCT passes are written in C++/MLIR, which is a serious undertaking. The
historical Scala-FIRRTL transform path is legacy and should not be used for new
work.

**Justify a custom pass carefully.** It becomes maintenance debt tied to a CIRCT
version, and most goals — naming, attributes, extra ports, structural
constraints — are achievable from the Chisel side with `desiredName`,
`suggestName`, `dontTouch`, or a generator. Reach for a pass only when you need
a transformation that cannot be expressed at elaboration time at all.

---

## 4. Advanced debugging

**Across the RTL/netlist boundary.** After synthesis your signal names may be
gone. `dontTouch` ([Ch 9 §3](../ch09-integration/README.md#3-names-donttouch-and-physical-design))
on the handful of signals you need to survive is the practical answer, applied
before you need them rather than after.

**From a formal counterexample.** A failing property gives you a `.vcd` trace.
Debug it exactly like any waveform ([Ch 4 §3](../ch04-names-waveforms/README.md#3-waveform-debugging)) —
the counterexample is usually short, which is what makes formal failures
pleasant to debug compared with a simulation failure a million cycles in.

**X-propagation.** RTL simulation and gate-level simulation disagree about X:
RTL is often optimistic (an X selecting a mux may propagate a defined value)
while gates are pessimistic. A design that relies on unreset state can pass RTL
simulation and fail at gate level — see
[D3](../ch12-silicon/README.md).

---

## 5. Exercises

1. Emit `ManyModules(64)` and `ManyInstances(64)` and time both builds. Does the
   output differ? Does the elaboration time?
2. Give `Leaf` a width parameter and instantiate it with three different widths.
   How many module declarations now, and why is that correct rather than a
   deduplication failure?
3. Emit any design with `--split-verilog -o=split/` and inspect the directory.
   What is in the file list besides the modules?
4. Print `emitCHIRRTL` and `emitSystemVerilog` for `Leaf` side by side. Which
   stage removed what?

---

## Where next

- [**Ch 10 — Verification at Scale**](../ch10-verification/README.md)
- [**Ch 12 — Silicon and Organization**](../ch12-silicon/README.md)
- Back to the [appendix index](../README.md).
