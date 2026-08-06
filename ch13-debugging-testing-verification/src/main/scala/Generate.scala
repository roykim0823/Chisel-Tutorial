import chisel3._

// Emit SystemVerilog. Note: the `assert` in Assert is dropped in generation.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Assert(), opts)
  emitVerilog(new TickGenTestTop(), opts)
}
