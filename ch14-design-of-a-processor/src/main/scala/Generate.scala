import chisel3._
import leros._

// Emit SystemVerilog for the Leros processor and for the building blocks the
// chapter discusses in their own right. InstrMem is not emitted separately:
// one emitVerilog writes the whole hierarchy into Leros.sv, so it is in there.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new AluAccu(32), opts)
  emitVerilog(new Decode(), opts)
  emitVerilog(new DataMem(8), opts)
  emitVerilog(new Leros("asm/test.s"), opts)
}
