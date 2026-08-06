import chisel3._

// Emit SystemVerilog for this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Flasher(), opts)
  emitVerilog(new Flasher2(), opts)
  emitVerilog(new PopulationCount(), opts)
  emitVerilog(new ReadyValidBuffer(), opts)
}
