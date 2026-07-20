import chisel3._

// Emit SystemVerilog for this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new Debounce())
  emitVerilog(new DebounceFunc())
  emitVerilog(new SyncReset())
}
