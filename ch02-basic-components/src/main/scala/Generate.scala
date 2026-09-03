import chisel3._

// Convenience entry point so `sbt run` produces something visible for this
// chapter. It emits the SystemVerilog for every module into generated/:
//   Logic.sv, RegisterFile.sv, Structure.sv, Registers.sv, Defaults.sv
// Channel and BundleVec are Bundles, not Modules - a Bundle is a type, so it
// has no .sv of its own; it shows up as the flattened ports/wires of whatever
// module uses it (here Structure.sv).
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Logic(), opts)
  emitVerilog(new RegisterFile(true), opts)
  emitVerilog(new Structure(), opts)
  emitVerilog(new Registers(), opts)
  emitVerilog(new Defaults(), opts)
}
