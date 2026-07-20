# Chapter 15 — A RISC-V Pipeline

**Pipelining** overlaps the stages of instruction processing so that, ideally,
one instruction completes every clock cycle — in contrast to the multi-cycle
Leros processor of Chapter 14. This chapter presents **Wildcat**, a small,
readable **RISC-V (RV32I)** processor built as a **3-stage pipeline**. We build
the parts that stand on their own — the ALU and decoder (written as functions
returning hardware), the CSR block, and the instruction ROM — test them, and
generate the SystemVerilog for the *complete* `ThreeCats` CPU.

*Conventions: every file path is relative to `tutorial/ch15-a-risc-v-pipeline/`,
and every command is run from that folder.*

> **Scope note.** Wildcat's full system needs a *program* in its memories, and
> the upstream repo loads programs from ELF files via a `jelf` dependency and
> runs them on Verilator. To stay self-contained (three dependencies, no
> external files), this project **builds and generates** the whole `ThreeCats`
> pipeline and **unit-tests** the pieces that don't need a program: the ALU,
> the decoder, the CSRs, and the instruction ROM. Running full programs (and the
> `StandardFive`/`WildFour`/`ThreeCats` co-simulation test suite) lives in the
> [Wildcat GitHub repo](https://github.com/schoeberl/wildcat).

## What's in this project

```
ch15-a-risc-v-pipeline/
├── build.sbt · project/build.properties
├── figures/wildcat.png
├── src/main/scala/
│   ├── wildcat/defines.scala                 ISA constants + AluType/InstrType enums
│   └── wildcat/pipeline/
│       ├── connections.scala                 InstrIO / MemIO / DecodedInstr bundles
│       ├── Wildcat.scala                      abstract top level
│       ├── Functions.scala                    decode / alu / compare / regfile / ...
│       ├── ThreeCats.scala                    the 3-stage pipelined CPU
│       ├── InstructionROM.scala               preloaded instruction memory
│       ├── Csr.scala                          control & status registers
│       └── FunctionWrappers.scala             (tutorial) AluModule / DecodeModule
├── src/main/scala/Generate.scala
└── src/test/scala/wildcat/WildcatTest.scala
```

---

## 15.1 The RV32I ISA in brief

RV32I has 32 registers of 32 bits (x0 is always 0) and a program counter; a
load-store architecture with 32-bit instructions. The instruction classes:
register/register and register/immediate ALU ops, loads and stores, and control
flow (conditional branches, `jal`/`jalr`). Wildcat implements this base ISA.

---

## 15.2 A three-stage pipeline

A pipeline stage does one combinational job per cycle; registers between stages
hold intermediate results. On-chip memories (with a registered address and
one-cycle read latency) set a practical lower bound of three stages:

1. **Fetch** — instruction memory (IM) read.
2. **Decode** — register-file (RF) read, decode, address computation.
3. **Execute** — ALU / branch / memory access.

<p align="center">
  <img src="figures/wildcat.png" alt="The 3-stage Wildcat pipeline" width="720">
</p>

***Figure 15.1** — The 3-stage Wildcat pipeline (simplified). Instructions flow
left→right through PC/IM (fetch), RF/IR/Imm (decode), and the ALU/DM (execute);
results write back to the RF or DM.*

---

## 15.3 Datapath as functions

Wildcat writes the datapath pieces as **Scala functions that return hardware**
(the lightweight style from Chapter 10) and composes them in the pipeline. The
**ALU** switches on an operation id from an enum shared with a Scala ISA
simulator:

`src/main/scala/wildcat/pipeline/Functions.scala`
```scala
def alu(op: UInt, a: UInt, b: UInt): UInt = {
  val res = Wire(UInt(32.W))
  res := DontCare
  switch(op) {
    is(ADD.id.U) { res := a + b }
    is(SUB.id.U) { res := a - b }
    is(SLL.id.U) { res := a << b(4, 0) }
    is(SLT.id.U) { res := (a.asSInt < b.asSInt).asUInt }
    // ... XOR, SRL, SRA, OR, AND, SLTU
  }
  res
}
```

The **decoder** turns an opcode into control flags plus the immediate and ALU op:

`src/main/scala/wildcat/pipeline/Functions.scala`
```scala
def decode(instruction: UInt) = {
  val opcode = instruction(6, 0)
  val decOut = Wire(new DecodedInstr())
  // ... defaults ...
  switch(opcode) {
    is(AluImm.U) { decOut.instrType := I.id.U; decOut.isImm := true.B; decOut.rfWrite := true.B; decOut.rs1Valid := true.B }
    is(Alu.U)    { decOut.instrType := R.id.U; decOut.rfWrite := true.B; decOut.rs1Valid := true.B; decOut.rs2Valid := true.B }
    // ... Branch, Load, Store, Lui, AuiPc, Jal, JalR, System
  }
  decOut.aluOp := getAluOp(instruction)
  decOut.imm := getImm(instruction, decOut.instrType)
  decOut
}
```

The **register file** uses `SyncReadMem` with `WriteFirst` (read-during-write
forwarding), forces x0 to read 0, and returns a Scala tuple:

`src/main/scala/wildcat/pipeline/Functions.scala`
```scala
val regs = SyncReadMem(32, UInt(32.W), SyncReadMem.WriteFirst)
val rs1Val = Mux(RegNext(rs1) === 0.U, 0.U, regs.read(rs1))
val rs2Val = Mux(RegNext(rs2) === 0.U, 0.U, regs.read(rs2))
when(wrEna && rd =/= 0.U) { regs.write(rd, wrData) }
(rs1Val, rs2Val, debugRegs)
```

Because these are *functions*, we test them via tiny wrapper modules:

`src/main/scala/wildcat/pipeline/FunctionWrappers.scala`
```scala
class AluModule extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W)); val a = Input(UInt(32.W)); val b = Input(UInt(32.W))
    val res = Output(UInt(32.W))
  })
  io.res := alu(io.op, io.a, io.b)
}
```

---

## 15.4 The pipeline, fetch, and ROM

`ThreeCats` wires the three stages together, including forwarding (from the
execute stage back to decode) and branch/jump handling. The **fetch** stage
generates the PC and drives the instruction memory with the *next* PC (the ROM's
registered address is the fetch pipeline register):

`src/main/scala/wildcat/pipeline/ThreeCats.scala`
```scala
val pcReg = RegInit(0.S(32.W).asUInt)
val pcNext = WireDefault(Mux(doBranch, branchTarget, pcReg + 4.U))
pcReg := pcNext
io.imem.address := pcNext

val instr = WireDefault(io.imem.data)
when (io.imem.stall) {
  instr := 0x00000013.U   // NOP while stalled
  pcNext := pcReg
}
```

For simulation and small FPGA experiments, a ROM is preloaded from a Scala array:

`src/main/scala/wildcat/pipeline/InstructionROM.scala`
```scala
class InstructionROM(code: Array[Int]) extends Module {
  val io = IO(Flipped(new InstrIO()))
  val addrReg = RegInit(0.U(32.W))
  addrReg := io.address
  val instructions = VecInit(code.toIndexedSeq.map(_.S(32.W).asUInt))
  io.data := instructions(addrReg(31, 2))   // one-cycle read latency
  io.stall := false.B
}
```

---

## 15.5 Build, run, and check

```
$ sbt test
```

Expected tail (5 tests):

```
[info] Tests: succeeded 5, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

The tests check: the ALU across RV32I operations; the decoder on an R-type `add`
and an I-type `addi` (including its immediate); the CSR `MARCHID` (47); and the
instruction ROM returning a preloaded program (with its one-cycle latency).

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits `ThreeCats.sv` (the **complete 3-stage CPU** — ~500 lines of
SystemVerilog), `Csr.sv`, `InstructionROM.sv`, `AluModule.sv`, and
`DecodeModule.sv`. That the whole pipelined core elaborates to Verilog is the
headline result of this chapter.

> **`.v` → `.sv`:** as elsewhere, Chisel 6 emits SystemVerilog (`.sv`) via
> CIRCT/firtool where the book says `.v`.

---

## 15.6 Recap

- A **pipeline** overlaps fetch/decode/execute for ~1 instruction per cycle;
  on-chip memories with one-cycle reads motivate Wildcat's **3 stages**.
- The datapath is built from **functions returning hardware** (`alu`, `decode`,
  `compare`, `registerFile`, …), composed in `ThreeCats`, with forwarding and
  branch handling.
- The register file uses `SyncReadMem` (`WriteFirst`) and forces x0 to 0.
- The **whole CPU generates SystemVerilog**; the ALU/decoder/CSR/ROM are unit-
  tested here — full program execution is in the upstream Wildcat repo.

## 15.7 Exercise

Extend the design: add a new ALU wrapper test for the shift/compare ops, or
hand-assemble a short RV32I program into an `InstructionROM` and reason about how
many cycles each instruction takes to flow through the three stages. For the full
experience (running programs, co-simulation against a Scala ISA model), clone the
[Wildcat repository](https://github.com/schoeberl/wildcat).

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 14 — Design of a Processor](../ch14-design-of-a-processor/README.md)**.
Next: Chapter 16 — Contributing to Chisel (coming next).
