# Chapter 12 — Silicon and Organization

> **Audience**: teams taking Chisel through a real tape-out, or introducing it into a SystemVerilog organization
> **Goal**: know what happens to your design after synthesis, and how a Chisel team fits into an SV-native flow
> **Time budget**: ongoing

> ## Read this first
>
> **Every other part of this appendix backs its SystemVerilog with real captured
> output. This one cannot.** UPF power intent, gate-level netlists, commercial
> synthesis and timing reports, and UVM benches all require tools that are not —
> and mostly cannot be — part of a self-contained tutorial repository.
>
> So the code blocks here are **illustrative**, marked as such, and describe
> shapes rather than reproduce them. Where a claim is checkable with the open
> tools that *are* available (Yosys, Z3, Verilator), the text says so.
>
> Treat this part as a map of territory you will cross with your own flow, not as
> something to run.

*Conventions: paths are relative to this directory.*

---

## 1. Low-power design (UPF)

**Unified Power Format** describes power intent that RTL cannot express: which
logic sits in which power domain, which domains can be switched off, where
isolation cells and retention flops go.

*illustrative — UPF is written alongside the RTL, not emitted by Chisel*
```tcl
create_power_domain PD_TOP
create_power_domain PD_CORE -elements {u_core}

create_supply_port  VDD
create_supply_net   VDD -domain PD_TOP
create_power_switch core_sw -domain PD_CORE ...

set_isolation core_iso -domain PD_CORE \
  -isolation_power_net VDD -clamp_value 0 \
  -applies_to outputs
```

**The Chisel angle is entirely about names.** UPF addresses your design
hierarchically — `u_core`, `u_core/u_fifo`, specific ports. Every one of those
is a *generated* name, and
[Ch 4 §1.4](../ch04-names-waveforms/README.md#14-module-and-instance-names-in-a-hierarchy)
showed they are not guaranteed: `small` became `small_0` with no warning.

The practical rules:

- Pin the names you depend on with `override def desiredName` and meaningful
  instance `val`s.
- Keep the power-domain boundary at a module boundary you control.
- Re-check the UPF against the generated hierarchy on every regeneration. Nothing
  errors when a path stops matching — the intent silently stops applying.

---

## 2. Post-synthesis netlists and gate-level simulation

### 2.1 What a netlist is

Synthesis replaces your RTL with an interconnection of library cells. There is no
`always` block, no `assign` with arithmetic — just instantiated gates and flops:

*illustrative — the shape of a gate-level netlist*
```systemverilog
module Counter (clock, reset, io_count);
  DFFRPQ_X1 cntReg_0 (.CK(clock), .RN(n12), .D(n7), .Q(io_count[0]));
  AND2_X1   U7       (.A1(n3), .A2(n4), .ZN(n7));
  ...
endmodule
```

Your Chisel names may survive on flops (synthesis usually preserves register
names) but the combinational logic becomes `n1`, `n2`, `U7`. This is why
`dontTouch` on the few signals you must be able to find is applied *before* the
flow, not after.

### 2.2 Gate-level simulation and X

Running the same tests on the netlist catches what RTL simulation cannot:

- **X-propagation.** RTL simulation is often *optimistic* — a mux selected by an
  X may still produce a defined output. Gates are *pessimistic* — X spreads.
  A design that relies on an unreset register can pass RTL simulation and fail at
  gate level. This is the most common GLS surprise, and it traces straight back
  to [Ch 5 §1.3](../ch05-clock-reset/README.md#13-a-register-with-no-reset).
- **Timing.** With back-annotated delays (SDF), GLS checks the design at real
  timing rather than at cycle boundaries.

GLS is slow — orders of magnitude slower than RTL — so it runs on a small,
targeted subset: reset sequences, mode changes, and anything involving
uninitialized state.

### 2.3 Equivalence checking

Logical equivalence checking (LEC) proves the netlist implements the same
function as the RTL, without simulation. It is the standard sign-off that
synthesis did not change behaviour, and it is far more complete than GLS for that
specific question.

**The full comparison flow**, in the order most teams run it: RTL simulation →
formal on key blocks → synthesis → LEC (RTL vs netlist) → targeted GLS → timing
sign-off.

---

## 3. Reviewing what synthesis tells you

You get area, timing (worst and total negative slack), and power. The Chisel-side
fixes are covered in
[Ch 9 §5](../ch09-integration/README.md#5-reading-synthesis-results); the
organizational point is different: **the report speaks SystemVerilog**. A timing
path is a list of generated signal names. Reading it back to Chisel is exactly
the Chapter 4 skill, and it is the single most valuable thing a Chisel engineer
can be able to do in a room full of physical-design engineers.

---

## 4. EDA tools you will meet

| vendor | simulation | synthesis | timing / PD | formal |
|---|---|---|---|---|
| Synopsys | VCS | Design Compiler, Fusion Compiler | PrimeTime, IC Compiler | VC Formal |
| Cadence | Xcelium | Genus | Tempus, Innovus | JasperGold |
| Siemens EDA | Questa | — | — | PropCheck |
| AMD/Xilinx | — | Vivado | Vivado | — |
| Intel | — | Quartus | Quartus | — |
| Open source | **Verilator**, Icarus | **Yosys** | OpenSTA | **SymbiYosys** + **Z3** |

The bottom row is the one you can act on today, and three of those tools are
already usable against this repo's output: Verilator lints it
([Ch 9 §6](../ch09-integration/README.md#6-lint-and-ci-for-generated-sv)),
Yosys reads it and exports SMT2, and Z3 solves
([Ch 10 §4](../ch10-verification/README.md#4-running-a-formal-check)).

All of them consume **SystemVerilog**. None of them knows Chisel exists. That is
the whole reason this appendix exists.

---

## 5. Running a Chisel team inside an SV organization

### 5.1 The bridge role

Someone has to be fluent in both. The generated `.sv` is the contract surface,
and the person who can read a synthesis report or a UVM failure back to a line
of Scala is the one who makes the arrangement work. That is the job this
appendix has been training for.

### 5.2 Version control of generated SV

The recurring argument: should the `.sv` be committed?

- **Do not commit it** if the whole flow builds from source — it is a derived
  artifact, and committing it invites edits that get overwritten.
- **Do commit it** (in a separate, clearly-marked location) when a downstream
  team consumes it on a different schedule, or when you need to diff across
  toolchain versions. In that case, regenerate and diff in CI so drift is
  visible.

This tutorial takes the first position: `generated/` is git-ignored everywhere.

### 5.3 Naming contracts

The single most useful agreement between a Chisel team and everyone downstream:
**a written list of names that must not change** — top-level ports, power-domain
boundary modules, memory instances, signals referenced by SDC or UPF. Pin them
with `desiredName` and `dontTouch`, and check them in CI.

### 5.4 A CI pipeline that catches drift

*illustrative — a shape, not a runnable workflow*
```
sbt test                          # unit tests (FIRRTL-level by default - see ch13)
sbt "runMain Generate"            # emit the SystemVerilog
verilator --lint-only -Wall ...   # lint with a baselined waiver list
diff against golden .sv           # catch unintended emission changes
sby formal.sby                    # formal on key blocks
synthesis smoke run               # area/timing trend
```

The step people skip is **diff against golden `.sv`**, and it is the one that
catches a firtool upgrade changing emission style before it reaches the
verification team.

### 5.5 Version pinning

Pin Chisel, firtool, and your simulator together, and upgrade deliberately.
Generated SystemVerilog changes between firtool releases — sometimes in ways that
alter lint output, waveform names, or which signals exist at all. This appendix
pins Chisel 6.5.0 / firtool 1.62.0 for exactly that reason, and every measured
claim in Levels A–D1 is tied to those versions.

---

## 6. Pitfalls

**Name drift breaking UPF/SDC/bind silently.** Nothing errors; the constraint
simply stops applying.

**Treating RTL-sim-clean as done.** It says nothing about metastability
([C2](../ch08-cdc/README.md)), X-propagation, or timing.

**Toolchain upgrades without an SV diff.** The generated code is your interface
to every downstream tool; changing it silently is changing the interface.

**Forcing Chisel Bundles to become SV interfaces.** Write a shim at the boundary
instead ([Ch 7 §3](../ch07-synthesizable-rtl/README.md#3-sv-interface-vs-chisel-bundle)).

**Unmanaged bores and probes.** Cross-hierarchy connections are invisible in the
modules they pass through; keep them few and documented.

---

## 7. Exercises

These are open-ended by necessity — they need your flow, not this repo.

1. Take any chapter design, synthesize it with Yosys (`synth -top X`), and
   compare the cell count with your mental model of the RTL.
2. Write the naming contract for one of the tutorial's designs: which names would
   you forbid from changing, and how would you pin each one?
3. Sketch the CI pipeline above for a design you actually own. Which step would
   have caught your last integration bug?
4. Find a design with an unreset register and reason about how it would behave in
   a pessimistic (4-state) gate-level simulation versus RTL.

---

## Epilogue — the A→D arc

- **A** taught you to read a generated file, and that it does not look like
  SystemVerilog you would write.
- **B** taught you to find things in it, and what Chisel's implicit clock, reset,
  and directions become.
- **C** taught you to make it synthesizable, safe across clock domains, and
  integrable with other people's RTL.
- **D** placed all of that inside a verification organization and a silicon flow.

The through-line: **Chisel is the source of truth for intent; the generated
SystemVerilog is the source of truth for what got built.** Every skill here is
some version of checking that the two agree.

---

## Where next

- [**Ch 10 — Verification at Scale**](../ch10-verification/README.md)
- [**Ch 11 — Hierarchy and the Pipeline**](../ch11-hierarchy/README.md)
- Back to the [appendix index](../README.md).
