import chisel3._

// Emit SystemVerilog for representative designs from this chapter.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new BubbleFifo(8, 4), opts)                       // custom-interface FIFO
  emitVerilog(new fifo.MemFifo(UInt(16.W), 8), opts)            // ready/valid memory FIFO
  emitVerilog(new fifo.DoubleBufferFifo(UInt(16.W), 4), opts)   // double-buffer FIFO
  emitVerilog(new uart.Sender(50000000, 115200), opts)          // UART "Hello World!" sender
}
