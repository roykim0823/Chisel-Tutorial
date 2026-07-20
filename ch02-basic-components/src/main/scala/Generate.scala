import chisel3._

// Convenience entry point so `sbt run` produces something visible for this
// chapter. It emits the Verilog for both modules into the current directory:
//   Logic.v         and         RegisterFile.v
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new Logic())
  emitVerilog(new RegisterFile(true))
}
