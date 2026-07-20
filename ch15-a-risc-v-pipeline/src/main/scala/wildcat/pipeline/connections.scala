package wildcat.pipeline

import chisel3._

// Interface to the instruction memory: present an address, get the instruction
// (one cycle later); `stall` freezes the pipeline (e.g. a cache miss).
class InstrIO extends Bundle {
  val address = Output(UInt(32.W))
  val data = Input(UInt(32.W))
  val stall = Input(Bool())
}

// Interface to the data memory: separate read and write ports, byte write
// enables (Vec of 4 for the four bytes of a 32-bit word).
class MemIO extends Bundle {
  val rdAddress = Output(UInt(32.W))
  val rdData = Input(UInt(32.W))
  val rdEnable = Output(Bool())
  val wrAddress = Output(UInt(32.W))
  val wrData = Output(UInt(32.W))
  val wrEnable = Output(Vec(4, Bool()))
  val stall = Input(Bool())
}

// The decoded form of an instruction — the output of the decode function.
class DecodedInstr extends Bundle {
  val instrType = UInt(3.W)
  val aluOp = UInt(4.W)
  val imm = SInt(32.W)
  val isImm = Bool()
  val isLui = Bool()
  val isAuiPc = Bool()
  val isLoad = Bool()
  val isStore = Bool()
  val isBranch = Bool()
  val isJal = Bool()
  val isJalr = Bool()
  val rfWrite = Bool()
  val isECall = Bool()
  val isCssrw = Bool()
  val rs1Valid = Bool()
  val rs2Valid = Bool()
}
