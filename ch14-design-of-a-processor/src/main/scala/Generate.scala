import chisel3._
import leros._

// Emit SystemVerilog for the Leros building blocks that elaborate standalone.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new AluAccu(32), opts)
  emitVerilog(new Decode(), opts)
  emitVerilog(new DataMem(8), opts)
}
