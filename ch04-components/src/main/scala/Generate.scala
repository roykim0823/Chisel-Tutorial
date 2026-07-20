import chisel3._

// Emit SystemVerilog for the fully-implemented modules of this chapter.
// Run with:  sbt "runMain Generate"
// Produces:  Count10.sv, Alu.sv
//
// Note: TopLevel/CompA..D are intentionally left empty (this chapter is about
// *connecting* components, not their function), so their outputs are undriven
// and they cannot be elaborated to Verilog. Count10 and Alu are complete.
object Generate extends App {
  emitVerilog(new Count10())
  emitVerilog(new Alu())
}
