import chisel3._

// Convenience entry point so `sbt run` produces something visible for this
// chapter. It emits the SystemVerilog for both modules into generated/:
//   generated/Logic.sv   and   generated/RegisterFile.sv
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Logic(), opts)
  emitVerilog(new RegisterFile(true), opts)
}
