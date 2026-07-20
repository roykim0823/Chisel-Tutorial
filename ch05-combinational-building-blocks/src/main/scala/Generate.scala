import chisel3._

// Emit SystemVerilog for the main building blocks of this chapter.
// Run with:  sbt "runMain Generate"
// Produces:  EncDec.sv, Arbiter3Loop.sv, Comparator.sv
object Generate extends App {
  emitVerilog(new EncDec())
  emitVerilog(new Arbiter3Loop())
  emitVerilog(new Comparator())
}
