# Chapter 10 — Verification at Scale

> **Audience**: anyone whose design is verified by more than its own unit tests
> **Goal**: emit real SystemVerilog assertions, cover points, and multi-cycle SVA properties from Chisel, and know where UVM and formal tools attach
> **Time budget**: ongoing

Levels A–C were about reading and building the generated RTL. This one is about
what a verification organization does *to* it. The good news, and the thing most
Chisel documentation undersells: **Chisel can emit genuine SVA** — `assert
property`, `cover property`, `assume property`, with `|->`, `##[1:2]`, and
`disable iff`. You do not have to hand-write properties to get formal or
coverage tooling working.

*Conventions: paths are relative to this directory; commands run from here.*

## Build and run

```
$ sbt "runMain Generate"    # prints the SystemVerilog to the console
```

This part's `Generate` prints rather than writing files, because the interesting
output depends on a firtool flag (`--emit-chisel-asserts-as-sva`) that you will
want to vary.

---

## 1. The coverage taxonomy

Three kinds, and they answer different questions:

| kind | question | who produces it |
|---|---|---|
| **Line / toggle / branch** | did the stimulus reach this code? | the simulator, automatically |
| **Functional** | did we exercise this *scenario*? | you, via cover points |
| **Assertion** | did this property ever get checked meaningfully? | your assertions |

Structural coverage on **generated** RTL needs care. Line coverage is measured
against the emitted SystemVerilog, and firtool has already inlined, merged, and
optimized — so "100% line coverage" on generated code means less than it does on
hand-written code, and a line you never wrote can show as uncovered. Functional
coverage, which you express deliberately, is the more meaningful metric for a
Chisel design.

---

## 2. Cover points from Chisel

`src/main/scala/Formal.scala`
```scala
  // A cover point: did we ever actually grant?
  cover(io.grant)
```

emits a labelled SystemVerilog cover property:

```systemverilog
  cover__cover: cover property (@(posedge clock) ~reset & busy);
```

Note it is **reset-gated** (`~reset &`) like `printf` and `assert`
([B3](../ch06-printf-assert/README.md)) — a cover point should not
count hits while the design is in reset.

The label (`cover__cover:`) is what coverage tools report against, so name your
cover points meaningfully.

---

## 3. Assertions, assumptions, and real SVA

This is the part worth knowing about. Chisel 6 ships `chisel3.ltl`, which
expresses **temporal properties** — the multi-cycle behaviour that a plain
`assert` cannot describe.

`src/main/scala/Formal.scala`
```scala
  // An immediate assertion: checked every cycle.
  assert(!(io.grant && !busy), "grant only while busy")

  // A cover point: did we ever actually grant?
  cover(io.grant)

  // A multi-cycle SVA property: a request implies a grant 1 to 2 cycles later.
  AssertProperty(io.req.implication(io.grant.delayRange(1, 2)))

  // An assumption constrains the environment rather than checking the design.
  AssumeProperty(io.req | !io.req)
```

Emitted with `--emit-chisel-asserts-as-sva`:

```systemverilog
module ReqGrant(
  input  clock,
         reset,
         io_req,
  output io_grant
);

  reg  busy;
  cover__cover: cover property (@(posedge clock) ~reset & busy);
  reg  hasBeenResetReg;
  initial
    hasBeenResetReg = 1'bx;
  wire hasBeenReset = hasBeenResetReg === 1'h1 & reset === 1'h0;
  assert property (@(posedge clock) disable iff (~hasBeenReset) io_req |-> ##[1:2] busy);
  assume property (@(posedge clock) disable iff (~hasBeenReset) 1'h1);
  always @(posedge clock) begin
    if (reset) begin
      hasBeenResetReg <= 1'h1;
      busy <= 1'h0;
    end
    else
      busy <= io_req & ~busy;
  end
  assign io_grant = busy;
endmodule
```

That is real SVA, generated from Scala. Reading it:

**`io_req |-> ##[1:2] busy`** — the implication operator and the delay range,
exactly the syntax [Ch 6 §2.1](../ch06-printf-assert/README.md#21-immediate-assertions-vs-sva)
showed as hand-written SVA. `.implication(...)` produces `|->` and
`.delayRange(1, 2)` produces `##[1:2]`.

**`disable iff (~hasBeenReset)`** — and note firtool built a `hasBeenReset`
tracker to produce it. `hasBeenResetReg` starts as `1'bx`, is set on the first
reset, and `hasBeenReset` is true only once reset has been seen *and* released.
This is stricter than `disable iff (reset)`: properties are suppressed not just
during reset but before the design has ever *been* reset, so a property cannot
fire on power-up garbage. It is the kind of detail that is easy to get wrong by
hand.

**The immediate `assert` is absent** — because `io.grant := busy` makes
`!(grant && !busy)` trivially true, so it was optimized away. A property proved
constant at elaboration is not emitted, which is worth remembering before you
conclude an assertion is being checked.

**`AssumeProperty`** constrains the environment: in formal it tells the solver
which inputs are legal. Mine is a tautology (`req | !req`) purely to show the
emission; a real one would say something like "req is never asserted while
`full`". **Formal without assumptions produces false counterexamples** — the
solver will happily drive impossible inputs.

### 3.1 The `--emit-chisel-asserts-as-sva` flag

Without it, `assert` emits the `$error`/`$fatal` form from
[Ch 6 §2](../ch06-printf-assert/README.md#2-assert--error--fatal),
which simulators understand but formal tools do not. With it, assertions become
concurrent SVA that both can consume. `AssertProperty` emits SVA either way,
because a temporal property has no immediate equivalent.

---

## 4. Running a formal check

The open-source flow is **SymbiYosys** (`sby`) driving Yosys and an SMT solver.

*illustrative — sby is not installed in this repo*
```
[options]
mode bmc
depth 20

[engines]
smtbmc z3

[script]
read -sv ReqGrant.sv
prep -top ReqGrant

[files]
ReqGrant.sv
```

A `PASS` means no counterexample within the bound; a `FAIL` writes a `.vcd`
trace you debug with the Chapter 4 waveform skills.

**What is actually available here.** `sby` is not installed, but **Yosys 0.67 and
Z3 4.16 are**, and the underlying path works — Yosys reads the SystemVerilog and
writes SMT2 for the solver:

```
$ yosys -p "read_verilog -sv ReqGrant.sv; prep -top ReqGrant; write_smt2 rg.smt2"
```

Yosys's SVA support is partial, so complex properties may need `read_verilog
-formal` and simplification. For serious work, install `sby` (it orchestrates
this correctly) or use a commercial tool.

### 4.1 A practical strategy

Formal is strongest on **small, control-dominated blocks with clear invariants**:
arbiters, FIFOs, FSMs, decoders. It is weakest on wide datapaths, where state
explosion makes exhaustive proof impractical.

Sensible order: prove the FIFO never overflows or underflows, prove the arbiter
never grants two requesters at once, prove the FSM never reaches an illegal
state — then stop and use simulation for the datapath.

---

## 5. UVM integration

**UVM cannot be demonstrated from this repo** — it needs a commercial simulator
and a testbench infrastructure that does not exist here. What follows is
orientation, not reproducible output.

UVM is a SystemVerilog class library for constrained-random, coverage-driven
verification. The pieces: a **sequence** generates stimulus, a **driver** puts it
on the interface, a **monitor** observes, a **scoreboard** checks against a
reference, and **coverage** measures what was exercised.

Your Chisel design enters as an ordinary DUT. The integration points are:

- **The flat port list.** UVM connects through a SystemVerilog `interface`, so a
  thin shim wraps your flattened `io_*` ports into one
  ([Ch 7 §3](../ch07-synthesizable-rtl/README.md#3-sv-interface-vs-chisel-bundle)
  explains why Chisel does not emit interfaces itself).
- **Clocking blocks** in that interface give race-free driving and sampling.
- **Names are the contract.** The shim references generated port names; a rename
  breaks it silently.

The realistic division of labour in a mixed organization: the Chisel team owns
the design and its unit tests, the verification team owns the UVM bench, and the
shim plus a naming agreement is the interface between them.

---

## 6. Pitfalls

**Formal without assumptions.** The solver explores input combinations your
system can never produce, and reports counterexamples you cannot fix. Constrain
the environment first.

**Trusting structural coverage on generated RTL.** Optimized-away lines and
merged expressions make line coverage a poor proxy. Prefer cover points you
wrote.

**Assuming an assertion is being checked.** A property that folds to a constant
is not emitted at all.

**Forgetting `--emit-chisel-asserts-as-sva`** when handing RTL to a formal tool —
your assertions ship as `$error` calls the tool ignores.

---

## 7. Exercises

1. Emit `ReqGrant` with and without `--emit-chisel-asserts-as-sva` and diff. What
   happens to `AssertProperty` versus `assert`?
2. Change the property to `delayRange(1, 1)` and re-emit. Then check the design
   actually satisfies it — does it?
3. Make the immediate `assert` non-trivial (assert something that is *not*
   provably true) and confirm it now appears in the output.
4. Write a real `AssumeProperty` for `ReqGrant` — for example, that `req` is
   never asserted two cycles in a row — and explain what it would rule out in a
   formal run.
5. Run the Yosys SMT2 export above and inspect the generated file. What does it
   contain for `busy`?

---

## Where next

- [**Ch 11 — Hierarchy and the Pipeline**](../ch11-hierarchy/README.md)
- [**Ch 12 — Silicon and Organization**](../ch12-silicon/README.md)
- Tutorial [Chapter 13 §13.4](../../ch13-debugging-testing-verification/README.md)
  covers the book's own take on formal verification.
- Back to the [appendix index](../README.md).
