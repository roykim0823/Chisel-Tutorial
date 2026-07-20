import chisel3._

// Emit SystemVerilog for this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new Flasher())
  emitVerilog(new Flasher2())
  emitVerilog(new PopulationCount())
  emitVerilog(new ReadyValidBuffer())
}
