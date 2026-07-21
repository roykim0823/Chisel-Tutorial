import chisel3._

// Emit SystemVerilog for the fully-implemented modules of this chapter.
// Run with:  sbt "runMain Generate"
// Produces:  Count10.sv, Alu.sv, Processor6.sv
//
// Note: TopLevel/CompA..D and the book's original Fetch/Decode/Execute/Processor
// are intentionally left empty (this chapter is about *connecting* components,
// not their function), so their outputs are undriven and, in the Processor's
// case, its `<>` connections no longer elaborate under Chisel 6. Count10, Alu,
// and the reworked Processor6 are complete and can be emitted.
object Generate extends App {
  emitVerilog(new Count10())
  emitVerilog(new Alu())
  // compiler error due to <> bulk connection mismatch since Chisel 6
  //emitVerilog(new Processor())
  emitVerilog(new Processor6())
}
