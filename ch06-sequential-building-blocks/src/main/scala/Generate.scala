import chisel3._

// Emit SystemVerilog for every design of this chapter.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Registers(), opts)
  emitVerilog(new Count100(), opts)
  emitVerilog(new Timer(), opts)
  emitVerilog(new Pwm(), opts)
  emitVerilog(new ShiftRegister(), opts)

  // All five counter styles of Section 6.2 at the same width, so the generated
  // SystemVerilog can be compared directly. They describe the same counter.
  emitVerilog(new WhenCounter(10), opts)
  emitVerilog(new MuxCounter(10), opts)
  emitVerilog(new DownCounter(10), opts)
  emitVerilog(new FunctionCounter(10), opts)
  emitVerilog(new NerdCounter(10), opts)

  // All three read/write behaviors of the synchronous memory.
  emitVerilog(new Memory(), opts)
  emitVerilog(new ForwardingMemory(), opts)
  emitVerilog(new MemoryWriteFirst(), opts)
}
