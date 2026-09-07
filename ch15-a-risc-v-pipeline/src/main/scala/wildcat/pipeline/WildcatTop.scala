package wildcat.pipeline

import chisel3._
import chisel3.util.experimental.BoringUtils

// A minimal SoC top level: the ThreeCats CPU plus the two memories it needs -
// an instruction ROM holding `program` and a data scratchpad. This is the
// wiring the abstract Wildcat class deliberately leaves out (see Wildcat.scala).
// The register-file mirror and the ecall flag are brought out as debug outputs
// so a test bench can watch a program execute.
class WildcatTop(program: Array[Int], nrBytes: Int = 4096) extends Module {
  val io = IO(new Bundle {
    val regs = Output(Vec(32, UInt(32.W)))
    val stop = Output(Bool())
  })

  val cpu = Module(new ThreeCats())
  val imem = Module(new InstructionROM(program))
  val dmem = Module(new ScratchPadMem(nrBytes))

  cpu.io.imem <> imem.io
  cpu.io.dmem <> dmem.io

  // debugRegs and stop are internal to ThreeCats, not ports, so we bore them
  // out instead of adding debug ports to the CPU (Chapter 13 section 13.2.3).
  io.regs := BoringUtils.bore(cpu.debugRegs)
  io.stop := BoringUtils.bore(cpu.stop)
}
