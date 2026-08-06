import chisel3._

// Emit SystemVerilog for a representative set of this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Registers(), opts)
  emitVerilog(new WhenCounter(10), opts)
  emitVerilog(new Timer(), opts)
  emitVerilog(new Pwm(), opts)
  emitVerilog(new ShiftRegister(), opts)
  emitVerilog(new Memory(), opts)
  emitVerilog(new ForwardingMemory(), opts)
}
