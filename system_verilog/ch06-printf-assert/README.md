# Chapter 6 — printf, assert, and the Toolchain

> **Audience**: anyone instrumenting a design or debugging the build itself
> **Goal**: understand the simulation-only SystemVerilog Chisel emits, and the FIRRTL/CIRCT pipeline that produces every `.sv` you have been reading
> **Time budget**: About 5 days

`printf`, `assert`, and `stop` are the three Chisel constructs that describe **no
hardware at all**. They emit SystemVerilog that exists only during simulation and
vanishes at synthesis — and the exact shape of that code explains several
debugging mysteries, most notably why your prints are silent during reset.

This part closes with the pipeline that turns Chisel into `.sv`, because once
you are debugging the toolchain rather than the design, you need to know which
stage to look at.

*Conventions: paths are relative to this directory, and commands are run from
here. Every SystemVerilog block is real captured output.*

## Build and run the examples

```
$ sbt "runMain Generate"                # emit all 3 designs into generated/
$ sbt "runMain Generate list"           # show the available names
$ sbt "runMain Generate PrintfExample"  # just one
```

---

## 1. `printf` → `$fwrite`

`src/main/scala/Printf.scala`
```scala
val cnt = RegInit(0.U(8.W))
cnt := cnt + 1.U
io.out := cnt + io.in

printf("cnt=%d in=%d\n", cnt, io.in)          // every cycle
when(cnt === 3.U) {
  printf(p"reached three: cnt=$cnt\n")        // guarded by a when
}
```

`generated/PrintfExample.sv`
```systemverilog
  `ifndef SYNTHESIS
    always @(posedge clock) begin
      if ((`PRINTF_COND_) & ~reset)
        $fwrite(32'h80000002, "cnt=%d in=%d\n", cnt, io_in);
      if ((`PRINTF_COND_) & cnt == 8'h3 & ~reset)
        $fwrite(32'h80000002, "reached three: cnt=%d\n", cnt);
    end // always @(posedge)
  `endif // not def SYNTHESIS
```

There is a lot packed into those six lines.

**`$fwrite(32'h80000002, ...)`** — not `$display`. The magic descriptor
`32'h80000002` is standard Verilog for **stderr** (`80000001` is stdout,
`80000000` is the log file). Using `$fwrite` with an explicit descriptor rather
than `$display` gives the toolchain control over where output goes.

**`` `ifndef SYNTHESIS ``** wraps the whole block, so synthesis never sees it.
This is why `printf` costs no hardware.

**`` `PRINTF_COND_ `` ** is a macro hook letting a testbench disable all printing
globally without recompiling the design.

**`& ~reset` — this is the one that bites.** Chisel automatically gates every
`printf` with "not in reset". A print that should happen on cycle 0 will not
appear if reset is still asserted, and this is the usual explanation for "my
`printf` prints nothing". Check your reset duration before you suspect the
`printf`.

**Both prints merged into one `always` block**, and the `when` guard became an
extra `&` term in the condition rather than a nested `if`. The Chisel `when`
around a `printf` is not control flow — it is just another term in the enable.

`p"..."` interpolation and `"%d"` formatting both end up as an ordinary Verilog
format string; there is no trace of which Chisel spelling you used.

---

## 2. `assert` → `$error` / `$fatal`

`src/main/scala/Assert.scala`
```scala
io.sum := io.a +& io.b

// An immediate assertion: checked every cycle in simulation.
assert(io.sum >= io.a, "widening add must not lose the carry")
```

`generated/AssertExample.sv`
```systemverilog
  wire [8:0] _GEN = {1'h0, io_a};
  wire [8:0] io_sum_0 = _GEN + {1'h0, io_b};
  `ifndef SYNTHESIS
    always @(posedge clock) begin
      if (~reset & io_sum_0 < _GEN) begin
        if (`ASSERT_VERBOSE_COND_)
          $error("Assertion failed: widening add must not lose the carry\n    at Assert.scala:13 assert(io.sum >= io.a, \"widening add must not lose the carry\")\n");
        if (`STOP_COND_)
          $fatal;
      end
    end // always @(posedge)
  `endif // not def SYNTHESIS
```

**The condition is inverted.** You assert `sum >= a`; the emitted code fires when
`io_sum_0 < _GEN`. An assertion is a check for the *failure*, so expect the
negation when you read it.

**It carries your source location and the original expression text** into the
`$error` string. When an assertion fires in a simulation log you get the Chisel
line, not just the Verilog line — the single most useful thing in this output.

**`$error` then `$fatal`, separately gated.** `` `ASSERT_VERBOSE_COND_ ``
controls the message and `` `STOP_COND_ `` controls whether simulation dies, so a
testbench can log failures without aborting.

**It is reset-gated too** (`~reset &`), for the same reason as `printf`: a design
in reset is not expected to hold its invariants.

Note also `+&` (width-expanding add) produced explicit zero-extensions
`{1'h0, io_a}` — that is where the extra bit comes from.

### 2.1 Immediate assertions vs. SVA

What Chisel emits is an **immediate assertion** — a condition checked at one
clock edge. SystemVerilog also has **concurrent assertions** (SVA), which
describe behaviour *over time*:

*illustrative — SVA, which Chisel does not emit*
```systemverilog
// "every request is granted within 1 to 3 cycles, ignore during reset"
property req_granted;
  @(posedge clock) disable iff (reset)
    req |-> ##[1:3] grant;
endproperty
assert property (req_granted);
```

The operators to recognize: `|->` (implication — if the left side holds, the
right must), `##[1:3]` (a delay range), `disable iff` (ignore while reset is
asserted), and `throughout`. You cannot write these as a Chisel `assert`; a
multi-cycle property needs either hand-written SVA bound to the generated module
([Ch 12](../ch12-silicon/README.md) covers `bind`) or Chisel's formal-facing API — see [Ch 10 §3](../ch10-verification/README.md#3-assertions-assumptions-and-real-sva).

**`disable iff (reset)` is not optional.** SVA without it will fire spuriously
during reset — which is precisely the gating Chisel does for you automatically on
immediate assertions.

---

## 3. `stop` → `$finish`

`src/main/scala/Assert.scala`
```scala
val cnt = RegInit(0.U(8.W))
cnt := cnt + 1.U
io.done := cnt === 10.U
when(cnt === 10.U) { stop() }
```

`generated/StopExample.sv`
```systemverilog
  `ifndef SYNTHESIS
    always @(posedge clock) begin
      if ((`STOP_COND_) & io_done_0 & ~reset)
        $finish;
    end // always @(posedge)
  `endif // not def SYNTHESIS
```

`$finish` ends the simulation cleanly (`$fatal` ends it as a failure). Same
pattern throughout: `` `ifndef SYNTHESIS ``, a macro hook, reset gating, and the
`when` folded into the condition.

Notice the shared subexpression: `io_done_0` is computed once and used both for
the output port and for the stop condition.

---

## 4. Simulation constructs you will meet

Reading someone else's testbench, these are the constructs worth recognizing:

| construct | what it does |
|---|---|
| `initial begin ... end` | runs once at time 0 — testbenches and the randomization scaffolding |
| `final begin ... end` | runs once at the end of simulation |
| `#10` | advance 10 time units — **not synthesizable**, and absent from Chisel output |
| `$display` / `$write` | print (with / without newline) |
| `$fwrite(fd, ...)` | print to a descriptor — what Chisel emits |
| `$finish` / `$fatal` | end simulation, cleanly / as a failure |
| `$error` / `$warning` | report with severity |
| `$random` | random value — used by the register randomization blocks |
| `$dumpfile` / `$dumpvars` | turn on VCD dumping |
| `$time` | current simulation time |

Chisel emits only `$fwrite`, `$error`, `$fatal`, `$finish`, and `$random`. There
is **no notion of simulation time** in a Chisel description — no `#` delays —
which is why generated code is always synthesizable apart from the explicitly
guarded blocks.

---

## 5. The FIRRTL/CIRCT pipeline

Everything you have read in Levels A and B came out of this chain:

```
Scala/Chisel  --elaboration-->  FIRRTL (.fir)  --firtool-->  SystemVerilog (.sv)
```

1. **Elaboration** — your Scala *runs*. Loops unroll, parameters resolve,
   `if`/`for` choose what to build. The result is a circuit graph.
2. **FIRRTL** — a textual intermediate representation of that graph. Still
   hierarchical, still typed, no Verilog-isms.
3. **firtool** (CIRCT) — lowers FIRRTL through a long pass pipeline: inlining,
   constant folding, width narrowing, dead-code elimination, `when` → mux
   conversion, memory extraction, and finally Verilog emission.

Step 3 is where every surprise in [Ch 2](../ch02-core-mappings/README.md) and [Ch 3](../ch03-aggregates/README.md) came from — the disappearing `Wire`,
the 7-bit adder, the packed lookup tables.

### 5.1 Seeing each stage

```
$ sbt "runMain Generate"                       # the .sv
```

To capture the FIRRTL instead of the Verilog, emit it explicitly:

*illustrative*
```scala
import _root_.circt.stage.ChiselStage
println(ChiselStage.emitCHIRRTL(new PrintfExample))   // high-level FIRRTL
println(ChiselStage.emitFIRRTL(new PrintfExample))    // after Chisel-side lowering
```

> **Import gotcha**: if `chisel3.util._` is in scope it shadows the top-level
> `circt` package. Write `import _root_.circt.stage.ChiselStage`.

You have already seen FIRRTL without realizing it: with the default Treadle
backend, `sbt test` writes `.lo.fir` files into `test_run_dir/` and simulates
*those* — the Verilog is never built. Chapter 13 of the tutorial covers what that
means for what your tests actually verify.

### 5.2 Running firtool directly

Useful options, all verified against the pinned firtool 1.62.0:

| option | effect |
|---|---|
| `--strip-debug-info` | drop the `path:line:col` source locators |
| `--disable-all-randomization` | drop the register/memory randomization scaffolding |
| `--split-verilog -o=<dir>` | one file per module |
| `--lowering-options=<flags>` | style control (`disallowPackedArrays`, `noAlwaysComb`, …) |
| `--repl-seq-mem` | extract memories for macro replacement ([Ch 9](../ch09-integration/README.md)) |

Pass them from Scala via `firtoolOpts`:

*illustrative*
```scala
ChiselStage.emitSystemVerilog(new PrintfExample,
  firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization"))
```

Note these are **firtool** options, not Chisel ones — passing
`--strip-debug-info` to `emitVerilog` fails with `Unknown option`.

---

## 6. Pitfalls

**Reset gating hides early prints and assertion failures.** Both `printf` and
`assert` are automatically `& ~reset`. If nothing prints, check reset first.

**`printf` prints nothing in synthesis — by design.** The `` `ifndef SYNTHESIS ``
guard is not a bug to work around.

**SVA without `disable iff (reset)`** fires spuriously during reset. Chisel does
this gating for you; hand-written SVA does not.

**2-state vs 4-state simulators disagree.** Verilator (2-state) starts registers
at 0; a 4-state simulator starts them at X. A design relying on an unreset
register can pass on one and fail on the other — see
[Ch 5 §1.3](../ch05-clock-reset/README.md#13-a-register-with-no-reset).

---

## 7. Exercises

**1. printf/assert instrumentation.** Add a `printf` to Chapter 6's counter that
prints only on the wrap-around cycle. Emit the design and find the guard term in
the generated condition. Then add an `assert` that the counter never exceeds its
maximum and confirm the emitted condition is the *negation* of what you wrote.

**2. Reset gating, demonstrated.** Add `printf("cycle 0\n")` unguarded to
`PrintfExample` and run it under a test with a long reset. Explain, from the
generated SystemVerilog, why nothing prints.

**3. Read a page of SVA.** Find the assertions in an open-source SystemVerilog
core (OpenTitan and CVA6 both have plenty) and identify `|->`, `##`,
`disable iff`, and `throughout` in real use. Which of them could you express as a
Chisel `assert`?

**4. Pipeline archaeology.** Emit `emitCHIRRTL`, `emitFIRRTL`, and the final
`.sv` for `AssertExample`. At which stage does the assertion condition get
inverted? At which stage does `+&` become explicit zero-extension?

---

## Where next

- [**Ch 4 — Names, Signals, and Waveforms**](../ch04-names-waveforms/README.md)
- [**Ch 5 — Clock, Reset, and Interfaces**](../ch05-clock-reset/README.md)
- [**Ch 7 — Synthesizable RTL**](../ch07-synthesizable-rtl/README.md)
- Back to the [appendix index](../README.md).

## References

- Tutorial [Chapter 13](../../ch13-debugging-testing-verification/README.md) —
  assertions, formal verification, backends, and what each backend simulates
- [CIRCT / firtool documentation](https://circt.llvm.org/docs/)
- [FIRRTL specification](https://github.com/chipsalliance/firrtl-spec)
