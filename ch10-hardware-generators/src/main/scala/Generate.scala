import chisel3._

// Emit SystemVerilog for a representative set of this chapter's generators.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new BcdTable())
  emitVerilog(new GenHardware())
  emitVerilog(new UseAdder())          // instantiates ParamAdder(8) and (16)
  emitVerilog(new ParamFunc())
  emitVerilog(new FunctionalMin(5, 8))
  emitVerilog(new UpTicker(5))
}
