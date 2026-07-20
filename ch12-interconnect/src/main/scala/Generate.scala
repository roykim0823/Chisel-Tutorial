import chisel3._

// Emit SystemVerilog for this chapter's devices.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new CounterDevice())
  emitVerilog(new UseMemMappedRV(UInt(16.W)))
}
