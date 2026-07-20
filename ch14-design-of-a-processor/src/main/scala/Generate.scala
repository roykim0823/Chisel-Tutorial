import chisel3._
import leros._

// Emit SystemVerilog for the Leros building blocks that elaborate standalone.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new AluAccu(32))
  emitVerilog(new Decode())
  emitVerilog(new DataMem(8))
}
