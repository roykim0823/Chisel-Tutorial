import chisel3._
import wildcat.pipeline._

// Emit SystemVerilog for the Wildcat pieces that elaborate without an external
// program. Run with:  sbt "runMain Generate"
object Generate extends App {
  // A tiny RISC-V program for the instruction ROM:
  //   addi x1, x0, 10
  //   addi x2, x0, 20
  //   add  x3, x1, x2
  //   ecall
  val program = Array(0x00A00093, 0x01400113, 0x002081B3, 0x00000073)

  emitVerilog(new ThreeCats())            // the full 3-stage pipelined CPU
  emitVerilog(new Csr())
  emitVerilog(new InstructionROM(program))
  emitVerilog(new AluModule())
  emitVerilog(new DecodeModule())
}
