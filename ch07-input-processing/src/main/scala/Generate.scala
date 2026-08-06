import chisel3._

// Emit SystemVerilog for this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Debounce(), opts)
  emitVerilog(new DebounceFunc(), opts)
  emitVerilog(new SyncReset(), opts)
}
