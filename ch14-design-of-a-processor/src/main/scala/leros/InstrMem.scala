package leros

import chisel3._
import chisel3.util._
import leros.util.Assembler

// The Leros instruction memory. Its constructor calls the assembler, so the
// program is assembled during hardware generation: a hardware generator that
// compiles code for the processor it is generating. `memReg` is the memory's
// address register, which is what lets the memory map onto an FPGA on-chip
// memory (and is why the "next PC" has to be fed to it, not the current PC).
class InstrMem(memAddrWidth: Int, prog: String) extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(memAddrWidth.W))
    val instr = Output(UInt(16.W))
  })

  val code = Assembler.getProgram(prog)
  assert(scala.math.pow(2, memAddrWidth) >= code.length, "Program too large")
  val progMem = VecInit(code.toIndexedSeq.map(_.U(16.W)))
  val memReg = RegInit(0.U(memAddrWidth.W))
  memReg := io.addr
  io.instr := progMem(memReg)
}
