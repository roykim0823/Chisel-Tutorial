import chisel3._

// Emit SystemVerilog for representative designs from this chapter.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new BubbleFifo(8, 4))                       // custom-interface FIFO
  emitVerilog(new fifo.MemFifo(UInt(16.W), 8))            // ready/valid memory FIFO
  emitVerilog(new fifo.DoubleBufferFifo(UInt(16.W), 4))   // double-buffer FIFO
  emitVerilog(new uart.Sender(50000000, 115200))          // UART "Hello World!" sender
}
