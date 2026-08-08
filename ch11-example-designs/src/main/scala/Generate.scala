import chisel3._

// Emit SystemVerilog for representative designs from this chapter.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new BubbleFifo(8, 4), opts)                       // custom-interface FIFO

  // All five ready/valid FIFOs of Section 11.2 at the same width and depth.
  // They share the FifoIO interface, so the generated SystemVerilog differs
  // only in the storage and the pointer logic - that is the whole point.
  //
  // These go into generated/fifo/ because `fifo.BubbleFifo` emits a Verilog
  // module named `BubbleFifo`, exactly like the custom-interface one above:
  // Scala packages keep the two apart, but the emitted Verilog has no
  // namespaces, so in one directory the second would overwrite the first.
  val fifoOpts = Array("--target-dir", "generated/fifo")

  emitVerilog(new fifo.BubbleFifo(UInt(16.W), 4), fifoOpts)        // one register per stage
  emitVerilog(new fifo.DoubleBufferFifo(UInt(16.W), 4), fifoOpts)  // double-buffer FIFO
  emitVerilog(new fifo.RegFifo(UInt(16.W), 4), fifoOpts)           // register file + pointers
  emitVerilog(new fifo.MemFifo(UInt(16.W), 8), fifoOpts)           // ready/valid memory FIFO
  emitVerilog(new fifo.CombFifo(UInt(16.W), 8), fifoOpts)          // combinational read

  emitVerilog(new uart.Sender(50000000, 115200), opts)          // UART "Hello World!" sender
  emitVerilog(new uart.Echo(50000000, 115200), opts)            // Rx -> Tx echo
  emitVerilog(new uart.UartLoopback(50000000, 115200), opts)    // Tx -> Rx loopback
}
