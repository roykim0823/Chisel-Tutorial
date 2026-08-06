import chisel3._

// Emit SystemVerilog for the main building blocks of this chapter.
// Run with:  sbt "runMain Generate"
// Produces:  generated/{EncDec,Arbiter3Loop,Comparator}.sv
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new EncDec(), opts)
  emitVerilog(new Arbiter3Loop(), opts)
  emitVerilog(new Comparator(), opts)
}
