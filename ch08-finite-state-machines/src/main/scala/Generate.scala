import chisel3._

// Emit SystemVerilog for the three FSMs.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new SimpleFsm(), opts)
  emitVerilog(new RisingFsm(), opts)
  emitVerilog(new RisingMooreFsm(), opts)
}
