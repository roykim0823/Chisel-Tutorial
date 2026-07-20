package wildcat.pipeline

import chisel3._
import wildcat.pipeline.Functions._

// Tutorial additions (not from the book): thin Module wrappers that expose the
// pure Wildcat datapath FUNCTIONS on IO ports, so they can be unit-tested and
// generated to Verilog. The functions themselves live in Functions.scala.

// Wraps the ALU function.
class AluModule extends Module {
  val io = IO(new Bundle {
    val op = Input(UInt(4.W))
    val a = Input(UInt(32.W))
    val b = Input(UInt(32.W))
    val res = Output(UInt(32.W))
  })
  io.res := alu(io.op, io.a, io.b)
}

// Wraps the instruction decoder, exposing a few decoded fields.
class DecodeModule extends Module {
  val io = IO(new Bundle {
    val instr = Input(UInt(32.W))
    val instrType = Output(UInt(3.W))
    val aluOp = Output(UInt(4.W))
    val imm = Output(SInt(32.W))
    val rfWrite = Output(Bool())
    val isImm = Output(Bool())
    val isBranch = Output(Bool())
  })
  val d = decode(io.instr)
  io.instrType := d.instrType
  io.aluOp := d.aluOp
  io.imm := d.imm
  io.rfWrite := d.rfWrite
  io.isImm := d.isImm
  io.isBranch := d.isBranch
}
