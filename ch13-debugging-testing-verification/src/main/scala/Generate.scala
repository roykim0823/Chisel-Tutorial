import chisel3._

// Emit SystemVerilog. Note: the `assert` in Assert is dropped in generation.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new Assert())
  emitVerilog(new TickGenTestTop())
}
