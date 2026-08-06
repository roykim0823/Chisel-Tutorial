import chisel3._
import wildcat.pipeline._

// Emit SystemVerilog for the Wildcat pieces that elaborate without an external
// program. Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  // A tiny RISC-V program for the instruction ROM:
  //   addi x1, x0, 10
  //   addi x2, x0, 20
  //   add  x3, x1, x2
  //   ecall
  val program = Array(0x00A00093, 0x01400113, 0x002081B3, 0x00000073)

  emitVerilog(new ThreeCats(), opts)            // the full 3-stage pipelined CPU
  emitVerilog(new Csr(), opts)
  emitVerilog(new InstructionROM(program), opts)
  emitVerilog(new AluModule(), opts)
  emitVerilog(new DecodeModule(), opts)
}
