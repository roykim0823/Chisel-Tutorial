# Chapter 14 — Design of a Processor (Leros)

This chapter builds a real (if small) microprocessor: **Leros**, a 32-bit
**accumulator machine**. It's an advanced example — some computer-architecture
background helps — that ties together everything so far: an ISA, an ALU with an
accumulator, an instruction decoder, a data memory, and a fetch/execute state
machine. We follow the book's bottom-up path (ALU → decode → memory) and build
the pieces that stand on their own, testing the ALU against a Scala reference
model.

*Conventions: every file path is relative to
`tutorial/ch14-design-of-a-processor/`, and every command is run from that
folder.*

> **Scope of this project.** The full Leros ties the datapath together with an
> **assembler** and an **instruction memory** that reads and assembles a program
> file at generation time (`InstrMem`, `Leros`, the `util` assembler). Those need
> an external program and file I/O, so — to keep this a clean, self-contained,
> passing build — we include the parts that elaborate and test standalone: the
> **ALU/accumulator**, the **decoder**, the **data memory**, and the shared
> constants. The excluded pieces are described below; read the full design in the
> [Leros repository](https://github.com/leros-dev/leros).

## What's in this project

```
ch14-design-of-a-processor/
├── build.sbt · project/build.properties
├── figures/leros-datapath.png
├── src/main/scala/
│   ├── leros/shared/shared.scala   opcode + ALU-op constants (shared)
│   ├── leros/AluAccu.scala         the ALU with the accumulator register
│   ├── leros/Decode.scala          the instruction decoder
│   ├── leros/DataMem.scala         the byte-addressable data memory
│   └── Generate.scala
└── src/test/scala/leros/
    └── AluAccuTest.scala           ALU vs. a Scala reference model
```

---

## 14.1 The instruction set (ISA)

The ISA is the contract between software and hardware, independent of the
implementation. Leros is an **accumulator machine**: every operation has the
accumulator `A` as one source and (usually) the destination; the second operand
is either an immediate `i` or one of 256 registers `Rn`. Memory access goes
through `A` using an address register `AR`. The full instruction set:

| Opcode | Function | Description |
|--------|----------|-------------|
| `add` | A = A + Rn | add register Rn to A |
| `addi` | A = A + i | add immediate value i to A |
| `sub` | A = A − Rn | subtract register Rn from A |
| `subi` | A = A − i | subtract immediate value i from A |
| `shr` | A = A >>> 1 | shift A logically right |
| `load` | A = Rn | load register Rn into A |
| `loadi` | A = i | load immediate value i into A |
| `and` | A = A and Rn | and register Rn with A |
| `andi` | A = A and i | and immediate value i with A |
| `or` | A = A or Rn | or register Rn with A |
| `ori` | A = A or i | or immediate value i with A |
| `xor` | A = A xor Rn | xor register Rn with A |
| `xori` | A = A xor i | xor immediate value i with A |
| `loadhi` | A[15:8] = i | load immediate into second byte |
| `loadh2i` | A[23:16] = i | load immediate into third byte |
| `loadh3i` | A[31:24] = i | load immediate into fourth byte |
| `store` | Rn = A | store A into register Rn |
| `jal` | PC = A, Rn = PC + 2 | jump to A and store return address |
| `ldaddr` | AR = A | load address register AR with A |
| `loadind` | A = mem[AR+(i << 2)] | load a word from memory into A |
| `loadindb` | A = mem[AR+i][7:0] | load a byte from memory into A |
| `loadindh` | A = mem[AR+(i << 1)][15:0] | load a half word from memory into A |
| `storeind` | mem[AR+(i << 2)] = A | store A into memory |
| `storeindb` | mem[AR+i] = A[7:0] | store a byte into memory |
| `storeindh` | mem[AR+(i << 1)] = A[15:0] | store a half word into memory |
| `br` | PC = PC + o | branch |
| `brz` | if A == 0: PC = PC + o | branch if A is zero |
| `brnz` | if A != 0: PC = PC + o | branch if A is not zero |
| `brp` | if A >= 0: PC = PC + o | branch if A is positive |
| `brn` | if A < 0: PC = PC + o | branch if A is negative |
| `scall` | — | system call (simulation hook) |

`A` is the accumulator, `PC` the program counter, `i` an immediate (0–255),
`Rn` a register `n` (0–255), `o` a branch offset relative to `PC`, and `AR` the
address register for memory access.

*The accumulator and the register file are, in this current implementation,
32 bits wide — kept configurable, with an eye towards also supporting 16-bit or
64-bit versions of Leros.*

Leros branches are **relative** to the current instruction, and can branch
forward and backward around **2000 instructions**. For larger control-flow
changes and for function calls/returns, Leros has a jump-and-link (`jal`)
instruction: it jumps to the address held in the accumulator and stores the
address of the following instruction into a register, which can later be used
to return from the function.

Here is an example program in Leros assembly:

```
loadi 1
addi 2
ori 0x50
andi 0x1f
subi 0x13
loadi 0xab
addi 0x01
subi 0xac

scall 0
```

Each instruction consists of an opcode mnemonic and a constant, written in
decimal or hexadecimal. This snippet shows immediate versions of load,
arithmetic, and logic instructions. The last instruction, `scall 0`, is a
system call that ends execution (or simulation) — this short program is part
of the Leros test suite, whose convention is that the accumulator holds 0 at
the end of a passing test.

Instructions are **16 bits**: the upper byte encodes the opcode, the lower byte
holds an immediate, register number, or branch offset (part of the branch
offset also uses bits in the upper byte). For example, `00001001.00000010` is
an `addi` instruction that adds 2 to the accumulator, whereas `00001000.00000011`
adds the content of `R3` to the accumulator. For branches, 3 of the instruction
bits are used for larger offsets.

The full encoding, in the upper 8 bits of each instruction (unused bits marked
`-`):

```
+--------+----------+
|00000---| nop      |
|000010-0| add      |
|000010-1| addi     |
|000011-0| sub      |
|000011-1| subi     |
|00010---| sra      |
|00011---| -        |
|00100000| load     |
|00100001| loadi    |
|00100010| and      |
|00100011| andi     |
|00100100| or       |
|00100101| ori      |
|00100110| xor      |
|00100111| xori     |
|00101001| loadhi   |
|00101010| loadh2i  |
|00101011| loadh3i  |
|00110---| store    |
|001110-?| out      |
|000001-?| in       |
|01000---| jal      |
|01001---| -        |
|01010---| ldaddr   |
|01100-00| ldind    |
|01100-01| ldindb   |
|01100-10| ldindh   |
|01110-00| stind    |
|01110-01| stindb   |
|01110-10| stindh   |
|1000nnnn| br       |
|1001nnnn| brz      |
|1010nnnn| brnz     |
|1011nnnn| brp      |
|1100nnnn| brn      |
|11111111| scall    |
+--------+----------+
```

The internal ALU operation codes and instruction opcodes both live in one
shared object, so the hardware, an assembler, and a simulator can all use them:

`src/main/scala/leros/shared/shared.scala`
```scala
object Constants {
  val ADD = 0x08; val ADDI = 0x09; val SUB = 0x0c /* ... */
  // ALU operation codes (used by AluAccu and produced by Decode)
  val nop = 0; val add = 1; val sub = 2; val and = 3
  val or = 4; val xor = 5; val ld = 6; val shr = 7
}
```

---

## 14.2 The datapath

Leros executes each instruction in **two clock cycles** (`fetch`, `execute`) —
a state machine with a datapath (the FSMD idea from Chapter 9).

<p align="center">
  <img src="figures/leros-datapath.png" alt="The Leros datapath" width="720">
</p>

***Figure 14.1** — The Leros datapath. The PC addresses the instruction memory;
`Decode` drives the muxes and the ALU; the data memory holds data and the
registers; the ALU combines the accumulator `A` with an immediate or a register
value; `AR` holds the memory address.*

On-chip memories usually have input registers that cannot themselves be read
(at least on FPGAs). That's why the same "next PC" value is fed to both the PC
register and the input register of the instruction memory. For non-branching
instructions, the next PC is simply PC + 1 (counted in 16-bit instruction
words, not bytes). For a relative branch, `Decode` sign-extends the branch
immediate and adds it to the PC. For `jal`, the PC can instead be loaded from
`A`.

In the `fetch` state an instruction is fetched and decoded; `Decode` decides
what happens in the following `execute` state. Decode also generates the
operand for instructions with an immediate operand (e.g., `addi`, `loadhi`).
Since that operand is only consumed in the `execute` state, it must be stored
in a register between the two states.

The second memory doubles as both the general data memory and the storage for
the 255 registers — the register number is just an address into the same
memory. `AR` (itself loaded from `A`) supplies the address for a load or store;
a load's result is placed into `A`, and a store takes its data from `A`.
Finally, the ALU combines `A` with either an immediate (from the instruction)
or a register value (read from memory).

---

## 14.3 The ALU with accumulator

The central component of a processor is the
[arithmetic logic unit](https://en.wikipedia.org/wiki/Arithmetic_logic_unit),
or ALU for short — so we start with the ALU and a test bench. `op` selects the
operation; one operand is the accumulator, the other is `din`. The `switch`
maps each op to a Chisel expression; the `enaMask`/`enaByte`/`off` machinery
supports byte/half-word and load-high instructions by writing only selected
bytes back into the accumulator:

`src/main/scala/leros/AluAccu.scala`
```scala
switch(op) {
  is(nop.U) { res := a }
  is(add.U) { res := a + b }
  is(sub.U) { res := a - b }
  is(and.U) { res := a & b }
  is(or.U)  { res := a | b }
  is(xor.U) { res := a ^ b }
  is(shr.U) { res := a >> 1 }
  is(ld.U)  { res := b }
}
```

**Testing against a Scala reference model.** For testing, we write the same
ALU function in plain Scala:

`src/test/scala/leros/AluAccuTest.scala`
```scala
def alu(a: Int, b: Int, op: Int): Int = {
  op match {
    case 0 => a
    case 1 => a + b
    case 2 => a - b
    case 3 => a & b
    case 4 => a | b
    case 5 => a ^ b
    case 6 => b
    case 7 => a >>> 1
    case _ => -123 // shall not happen
  }
}
```

While this duplication of hardware written in Chisel and an implementation in
Scala does not detect errors that are already in the shared specification, it
is at least a strong sanity check. We use some corner-case values as the test
vector, and a helper `testOne()` that pokes `a` in, steps, then pokes `b` and
the operation `fun`, steps again, and compares against the Scala reference:

`src/test/scala/leros/AluAccuTest.scala`
```scala
def testOne(a: Int, b: Int, fun: Int): Unit = {
  dut.io.op.poke(ld.U)
  dut.io.enaMask.poke("b1111".U)
  dut.io.din.poke((a.toLong & 0x00ffffffffL).U)
  dut.clock.step(1)
  dut.io.op.poke(fun.U)
  dut.io.din.poke((b.toLong & 0x00ffffffffL).U)
  dut.clock.step(1)
  dut.io.accu.expect((alu(a, b, fun.toInt).toLong & 0x00ffffffffL).U)
}

def test(values: Seq[Int]) = {
  for (fun <- 0 to 7; a <- values; b <- values) testOne(a, b, fun)
}

// Interesting corner cases, then random inputs.
val interesting = Seq(1, 2, 4, 123, 0, -1, -2, 0x80000000, 0x7fffffff)
test(interesting)

val randArgs = Seq.fill(10)(scala.util.Random.nextInt())
test(randArgs)
```

Exhaustive testing of a 32-bit ALU is not possible (there are far too many
input combinations), which is why we picked corner cases as input values;
besides testing against corner cases, it is also useful to test against random
inputs, as the last two lines above do.

You can run the ALU test on its own with

```
$ sbt "testOnly leros.AluAccuTest"
```

The book's version of the test produces a success message similar to:

```
[info] AluAccuTest:
[info] AluAccu
[info] - should pass
[info] Run completed in 1 second, 794 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

(our copy of the test names the assertion "should match a Scala reference
model" — see § 14.10 for this project's actual captured output.)

---

## 14.4 Decoding instructions

From the ALU, we work backward and implement the instruction decoder.
Instruction decoding generates the signals that drive the multiplexers and the
ALU in the next stage/state. We define the instruction encoding in its own
Scala class and a *shared* package (§ 14.1's `Constants`), because we want to
share the encoding constants between the hardware implementation of Leros, an
assembler for Leros, and an instruction-set simulator — all three can then
stay in agreement automatically.

For the decode component we define a `Bundle` for the output, later used in
the execution state and fed partially into the ALU. The real `DecodeOut`
bundle has more fields than shown in the book's condensed listing — see the
snippet below for the full set used in this project:

`src/main/scala/leros/Decode.scala`
```scala
class DecodeOut extends Bundle {
  val operand = UInt(32.W)
  val enaMask = UInt(4.W)
  val op = UInt()
  val off = SInt(10.W)
  val isRegOpd = Bool()
  val useDecOpd = Bool()
  val isStore = Bool()
  // ... and more fields
}
```

We also define a companion object for `DecodeOut` with a function `default()`
that creates a `DecodeOut` and sets every field to a default value, so decode
only has to override what an instruction actually needs:

`src/main/scala/leros/Decode.scala`
```scala
object DecodeOut {
  val MaskNone = "b0000".U
  val MaskAll = "b1111".U

  def default: DecodeOut = {
    val v = Wire(new DecodeOut)
    v.operand := 0.U
    v.enaMask := MaskNone
    v.op := nop.U
    v.off := 0.S
    v.isRegOpd := false.B
    v.useDecOpd := false.B
    v.isStore := false.B
    // ... and more fields
    v
  }
}
```

Decode takes an 8-bit opcode as input and delivers the decoded signals as
output; those driving signals are given a default value by calling `default`:

`src/main/scala/leros/Decode.scala`
```scala
class Decode() extends Module {
  val io = IO(new Bundle {
    val din = Input(UInt(16.W))
    val dout = Output(new DecodeOut)
  })

  import DecodeOut._

  val d = DecodeOut.default
  // ...
}
```

The decoding itself is just a large switch statement on the part of the
instruction that represents the opcode (in Leros, for most instructions, the
upper 8 bits):

`src/main/scala/leros/Decode.scala`
```scala
switch(instr(15, 8)) {
  is(ADD.U)  { d.op := add.U; d.enaMask := MaskAll; d.isRegOpd := true.B }
  is(ADDI.U) { d.op := add.U; d.enaMask := MaskAll; d.useDecOpd := true.B }
  is(SHR.U)  { d.op := shr.U; d.enaMask := MaskAll }
  // ... loads, logic, store, memory access, scall
}
```

Additionally, decode generates a sign-extended version of the constant in the
instruction, and computes the offset used by the indirect load and store
instructions. Branch opcodes are detected from only the upper 4 bits.

---

## 14.5 The data memory

Data memory also holds the 256 registers. It's organized as 32-bit words split
into four bytes, so byte/half-word stores can use a **write mask**:

`src/main/scala/leros/DataMem.scala`
```scala
val mem = SyncReadMem(1 << memAddrWidth, Vec(4, UInt(8.W)))
val rdVec = mem.read(io.rdAddr)
io.rdData := rdVec(3) ## rdVec(2) ## rdVec(1) ## rdVec(0)
// ... split wrData into bytes ...
when (io.wr) { mem.write(io.wrAddr, wrVec, wrMask) }
```

---

## 14.6 Assembling instructions (excluded here)

To write programs for Leros we need an assembler. For a very first test we can
hard-code a few instructions into a Scala array and use it to initialize the
instruction memory:

```scala
val prog = Array[Int](
  0x0903, // addi 0x3
  0x09ff, // -1
  0x0d02, // subi 2
  0x21ab, // ldi 0xab
  0x230f, // and 0x0f
  0x25c3, // or 0xc3
  0x0000
)

def getProgramFix() = prog
```
*illustrative — from the Leros repository's `util/util.scala`, not built in
this project.*

That is a very inefficient way to test a processor, though. Writing an
assembler with an expressive language like Scala is not a big project — Leros'
assembler is about 100 lines of code. A function `getProgram` calls the
assembler; a symbol table for branch destinations is collected in a `Map`. A
classic assembler runs in two passes — (1) collect the symbol-table values,
(2) assemble the program using the symbols from pass 1 — so `assemble` is
called twice with a parameter indicating which pass it is:

```scala
def getProgram(prog: String) = {
  assemble(prog)
}

// collect destination addresses in first pass
val symbols = collection.mutable.Map[String, Int]()

def assemble(prog: String): Array[Int] = {
  assemble(prog, false)
  assemble(prog, true)
}
```
*illustrative — from `util/util.scala`, not built in this project.*

`assemble` starts by opening the source file and defining two helper
functions to parse the two possible operands: an integer constant (decimal or
hex), and a register number:

```scala
def assemble(prog: String, pass2: Boolean): Array[Int] = {

  val source = Source.fromFile(prog)
  var program = List[Int]()
  var pc = 0

  def toInt(s: String): Int = {
    if (s.startsWith("0x")) {
      Integer.parseInt(s.substring(2), 16)
    } else {
      Integer.parseInt(s)
    }
  }

  def regNumber(s: String): Int = {
    assert(s.startsWith("r"), "Register numbers shall start with 'r'")
    s.substring(1).toInt
  }
```
*illustrative — from `util/util.scala`, not built in this project.*

The core of the assembler is a Scala `match` expression over the mnemonic of
each line:

```scala
for (line <- source.getLines()) {
  if (!pass2) println(line)
  val tokens = line.trim.split(" ")
  val Pattern = "(.*:)".r
  val instr = tokens(0) match {
    case "//" => // comment
    case Pattern(l) => if (!pass2) symbols += (l.substring(0, l.length - 1) -> pc)
    case "add" => (ADD << 8) + regNumber(tokens(1))
    case "sub" => (SUB << 8) + regNumber(tokens(1))
    case "and" => (AND << 8) + regNumber(tokens(1))
    case "or" => (OR << 8) + regNumber(tokens(1))
    case "xor" => (XOR << 8) + regNumber(tokens(1))
    case "load" => (LD << 8) + regNumber(tokens(1))
    case "addi" => (ADDI << 8) + toInt(tokens(1))
    case "subi" => (SUBI << 8) + toInt(tokens(1))
    case "andi" => (ANDI << 8) + toInt(tokens(1))
    case "ori" => (ORI << 8) + toInt(tokens(1))
    case "xori" => (XORI << 8) + toInt(tokens(1))
    case "shr" => (SHR << 8)
    // ...
    case "" => // println("Empty line")
    case t: String => throw new Exception("Assembler error: unknown instruction: " + t)
    case _ => throw new Exception("Assembler error")
  }
```
*illustrative — from `util/util.scala`, not built in this project (Listing
lst:leros-asm-match in the book).*

---

## 14.7 The instruction memory (excluded here)

`InstrMem` is configured with the address width (`memAddrWidth`) and a path to
the program (`prog`). Its constructor calls the assembler from § 14.6 to
assemble the program — an example of a hardware generator that assembles code
for an embedded processor during hardware generation. The Scala array holding
the program is converted to a `Seq` and then mapped to a Chisel `Vec` with the
anonymous function `_.asUInt(16.W)`. The memory also contains an address
register (`memReg`) to enable mapping the instruction memory onto FPGA
on-chip memory:

```scala
class InstrMem(memAddrWidth: Int, prog: String) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(memAddrWidth.W))
    val instr = Output(UInt(16.W))
  })
  val code = Assembler.getProgram(prog)
  assert(scala.math.pow(2, memAddrWidth) >= code.length, "Program too large")
  val progMem = VecInit(code.toIndexedSeq.map(_.asUInt(16.W)))
  val memReg = RegInit(0.U(memAddrWidth.W))
  memReg := io.addr
  io.instr := progMem(memReg)
}
```
*illustrative — from `InstrMem.scala`, not built in this project (Listing
lst:leros-instr-mem in the book).*

*In the current version of Chisel, the generated code for this pattern
contains a large priority mux that FPGA synthesis tools cannot map onto an
on-chip memory. Using the MLIR-based CIRCT backend is expected to fix this
issue. Another workaround, used in the Patmos project for its bootloader, is
to hand-write Verilog that fits the synthesis tools and include it as a black
box.*

---

## 14.8 A state machine with data path implementation (excluded here)

The Leros ISA does not mandate a concrete implementation — throughout this
chapter we made implicit design decisions, and here we discuss one design
option. In the presented implementation, the data memory is **shared** with
the register file: the 256 registers are just an array in the data memory. A
different implementation might use dedicated on-chip memories for data and
registers instead.

Leros is implemented as a state machine with a datapath, so each instruction
takes more than one clock cycle: two states, `fetch` and `execute`.

```scala
object State extends ChiselEnum {
  val fetch, execute = Value
}
import State._

val stateReg = RegInit(fetch)

switch(stateReg) {
  is(fetch) {
    stateReg := execute
  }
  is(execute) {
    stateReg := fetch
  }
}
```
*illustrative — from `Leros.scala`, not built in this project (Listing
lst:leros:state in the book).*

The state machine switches between the two states. In `fetch`, an instruction
is fetched from the instruction memory and decoded. We also **start** a read
from the data memory already in `fetch`, because the data memory is
synchronous and needs one clock cycle to deliver its result. In `execute`, a
new value is computed for the accumulator (or the data-memory read result is
moved into it), and a write to the data memory, if any, is performed.

The following shows the instantiation of the ALU/accumulator and the two main
state registers, the program counter (`pcReg`) and the address register
(`addrReg`):

```scala
val alu = Module(new AluAccu(size))
val accu = alu.io.accu

// The main architectural state
val pcReg = RegInit(0.U(memAddrWidth.W))
val addrReg = RegInit(0.U(memAddrWidth.W))

val pcNext = WireDefault(pcReg + 1.U)
```
*illustrative — from `Leros.scala`, not built in this project.*

Instantiation of the instruction memory, parameterized with the program file
name:

```scala
val mem = Module(new InstrMem(memAddrWidth, prog))
mem.io.addr := pcNext
val instr = mem.io.instr
```
*illustrative — from `Leros.scala`, not built in this project.*

Instantiation of the decoder. Its input is the instruction from the fetch
module, and its outputs are the decode signals; since those signals are
needed in the `execute` state, they are registered in `decReg`:

```scala
val dec = Module(new Decode())
dec.io.din := instr
val decout = dec.io.dout

val decReg = RegInit(DecodeOut.default)
when (stateReg === fetch) {
  decReg := decout
}
```
*illustrative — from `Leros.scala`, not built in this project.*

Finally, the instantiation of the data memory and its port connections:

```scala
val dataMem = Module(new DataMem((memAddrWidth)))

val memAddr = Mux(decout.isDataAccess, effAddrWord, instr(7, 0))
val memAddrReg = RegNext(memAddr)
val effAddrOffReg = RegNext(effAddrOff)
dataMem.io.rdAddr := memAddr
val dataRead = dataMem.io.rdData
dataMem.io.wrAddr := memAddrReg
dataMem.io.wrData := accu
dataMem.io.wr := false.B
dataMem.io.wrMask := "b1111".U
```
*illustrative — from `Leros.scala`, not built in this project.*

Together, `AluAccu`, `Decode`, and `DataMem` — the modules that **are** built
in this project (§ 14.3–14.5) — are exactly the building blocks this
top-level `Leros` module wires together.

---

## 14.9 Implementation variations

Real processors perform
[instruction pipelining](https://en.wikipedia.org/wiki/Instruction_pipelining):
more than one instruction is in flight at a time. For Leros, we could imagine
a three-stage pipeline — instruction fetch, instruction decode, and execute —
with three instructions in the pipeline at once, executing one instruction per
clock cycle instead of one every two cycles. Compared to the implementation
presented in this chapter, pipelining could roughly **double** the performance
of Leros. The next chapter presents a pipelined processor implementing the
RISC-V instruction set.

---

## 14.10 Build, run, and check

```
$ sbt test
```

Expected (1 test — the ALU vs. its reference model over corner + random inputs):

```
[info] AluAccuTest:
[info] AluAccu
[info] - should match a Scala reference model
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
```

Generate SystemVerilog:

```
$ sbt "runMain Generate"
```

emits `AluAccu.sv`, `Decode.sv`, and `DataMem.sv`.

---

## 14.11 Recap

- An **ISA** is the software/hardware contract; Leros is a 32-bit accumulator
  machine with 16-bit instructions.
- A processor is an **FSMD**: a fetch/execute state machine over a datapath (PC,
  instruction memory, decoder, data memory, ALU + accumulator, `AR`).
- Build **bottom-up** (ALU → decode → memory) and test the ALU against a **Scala
  reference model**.
- Shared **constants** let the hardware, assembler, and simulator agree; an
  assembler that runs at generation time is itself a hardware generator.
- Real processors **pipeline**; a 3-stage Leros pipeline could roughly double
  its performance — the subject of the next chapter.

## 14.12 Exercise

This exercise assignment, in one of the last chapters, is in a very free form.
You are at the end of your learning tour through Chisel and ready to tackle
design problems that you find interesting.

One option is to reread the chapter and read along with the full
[Leros repository](https://github.com/leros-dev/leros): run its tests, fiddle
with the code by breaking it, and see that tests fail.

Another option is to write your own implementation of Leros. The
implementation in the repository is just one possible organization — you could
write a Chisel simulation version of Leros with a single pipeline stage, or go
crazy and superpipeline Leros for the highest possible clocking frequency.

A third option is to design your own processor from scratch. Maybe the
demonstration of how to build the Leros processor, and the tools needed along
the way, have convinced you that processor design and implementation are no
magic art, but engineering that can be very joyful.

Back to the **[tutorial index](../README.md)**.
Previous: **[Chapter 13 — Debugging, Testing, and Verification](../ch13-debugging-testing-verification/README.md)**.
Next: **[Chapter 15 — A RISC-V Pipeline](../ch15-a-risc-v-pipeline/README.md)**.
