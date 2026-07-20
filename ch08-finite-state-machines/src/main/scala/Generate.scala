import chisel3._

// Emit SystemVerilog for the three FSMs.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new SimpleFsm())
  emitVerilog(new RisingFsm())
  emitVerilog(new RisingMooreFsm())
}
