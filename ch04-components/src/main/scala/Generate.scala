import chisel3._

// Emit SystemVerilog for the fully-implemented modules of this chapter.
// Run with:  sbt "runMain Generate"
// Produces:  generated/{Count10,Alu,Processor6}.sv
//
// Note: TopLevel/CompA..D and the book's original Fetch/Decode/Execute/Processor
// are intentionally left empty (this chapter is about *connecting* components,
// not their function), so their outputs are undriven and, in the Processor's
// case, its `<>` connections no longer elaborate under Chisel 6. Count10, Alu,
// and the reworked Processor6 are complete and can be emitted.
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Count10(), opts)
  emitVerilog(new Alu(), opts)
  // compiler error due to <> bulk connection mismatch since Chisel 6
  //emitVerilog(new Processor())
  emitVerilog(new Processor6(), opts)
}
