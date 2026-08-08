import chisel3._

// Emit SystemVerilog for every design of this chapter.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new EncDec(), opts)
  emitVerilog(new Comparator(), opts)

  // The conditional-assignment forms of Section 5.1. Emitting all four lets you
  // see that `when`, `when/otherwise`, `elsewhen`, and `WireDefault` are all
  // just multiplexers in the generated always_comb block.
  emitVerilog(new Combinational(), opts)
  emitVerilog(new CombWhen(), opts)
  emitVerilog(new CombOther(), opts)
  emitVerilog(new CombElseWhen(), opts)
  emitVerilog(new CombWireDefault(), opts)

  // All three arbiter styles, so you can compare the generated SystemVerilog:
  // hand-written chain vs. truth table vs. for-loop generator.
  emitVerilog(new Arbiter3(), opts)
  emitVerilog(new Arbiter3Direct(), opts)
  emitVerilog(new Arbiter3Loop(), opts)
}
