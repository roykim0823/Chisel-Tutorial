package leros

import chisel3._
import chisel3.util._
import leros.shared.Constants._

// The two states of the Leros state machine.
object State extends ChiselEnum {
  val fetch, execute = Value
}

// Leros as a state machine with a datapath: every instruction takes two clock
// cycles. In `fetch` the instruction is fetched and decoded, and the data
// memory read is started (it is synchronous and needs a cycle). In `execute`
// the accumulator gets its new value and a store, if any, is performed.
class Leros(prog: String, memAddrWidth: Int = 8, size: Int = 32) extends Module {
  val io = IO(new Bundle {
    val accu = Output(UInt(size.W))
    val pc = Output(UInt(memAddrWidth.W))
    val exit = Output(Bool())
  })

  import State._

  val stateReg = RegInit(fetch)

  // Instruction fetch. The program is assembled into this memory.
  val imem = Module(new InstrMem(memAddrWidth, prog))
  val instr = imem.io.instr

  // Decode. The signals are consumed in the execute state, so they are
  // registered at the end of fetch.
  val dec = Module(new Decode())
  dec.io.din := instr
  val decout = dec.io.dout

  val decReg = RegInit(DecodeOut.default)
  when(stateReg === fetch) {
    decReg := decout
  }

  val alu = Module(new AluAccu(size))
  val accu = alu.io.accu

  // The main architectural state: the program counter and the address register.
  val pcReg = RegInit(0.U(memAddrWidth.W))
  val addrReg = RegInit(0.U(size.W))

  // The data memory, which also holds the 256 registers.
  val dataMem = Module(new DataMem(memAddrWidth))

  // A register operand is addressed by the instruction's low byte. An indirect
  // access is addressed by AR plus the decoded offset - a byte address, so the
  // word address drops the low two bits and they become the byte offset.
  val effAddr = (addrReg.asSInt +& decout.off).asUInt
  val effAddrWord = effAddr(memAddrWidth + 1, 2)
  val effAddrOff = effAddr(1, 0)

  val memAddr = Mux(decout.isDataAccess, effAddrWord, instr(7, 0))
  val memAddrReg = RegNext(memAddr)
  val effAddrOffReg = RegNext(effAddrOff)
  dataMem.io.rdAddr := memAddr
  val dataRead = dataMem.io.rdData
  dataMem.io.wrAddr := memAddrReg
  dataMem.io.wrData := accu
  dataMem.io.wr := false.B
  dataMem.io.wrMask := "b1111".U

  // The ALU's second operand is the decoded immediate or the memory read.
  val opd = WireDefault(dataRead)
  when(decReg.useDecOpd) {
    opd := decReg.operand
  }
  alu.io.din := opd
  alu.io.op := decReg.op
  // The accumulator may only change in execute, so during fetch the byte
  // write mask is forced to "no bytes".
  alu.io.enaMask := Mux(stateReg === execute, decReg.enaMask, DecodeOut.MaskNone)
  alu.io.enaByte := decReg.isLoadIndB
  alu.io.enaHalf := decReg.isLoadIndH
  alu.io.off := effAddrOffReg

  // Branches are recognized from the upper 4 bits of the instruction; the
  // remaining 12 bits are a signed offset relative to the branch itself.
  def brField(op: Int) = ((op >> 4) & 0x0f).U
  val brOff = instr(11, 0).asSInt
  val doBranch = WireDefault(false.B)
  switch(instr(15, 12)) {
    is(brField(BR)) { doBranch := true.B }
    is(brField(BRZ)) { doBranch := accu === 0.U }
    is(brField(BRNZ)) { doBranch := accu =/= 0.U }
    is(brField(BRP)) { doBranch := accu(size - 1) === 0.U }
    is(brField(BRN)) { doBranch := accu(size - 1) === 1.U }
  }

  // The same "next PC" goes to the PC register and to the instruction
  // memory's address register, so it must hold during fetch and only advance
  // in execute.
  val brTarget = (pcReg.asSInt +& brOff).asUInt
  val pcNext = WireDefault(pcReg)
  imem.io.addr := pcNext

  switch(stateReg) {
    is(fetch) {
      stateReg := execute
    }
    is(execute) {
      stateReg := fetch
      pcNext := Mux(doBranch, brTarget(memAddrWidth - 1, 0), pcReg +% 1.U)
      pcReg := pcNext

      when(decReg.isStore || decReg.isStoreInd) {
        dataMem.io.wr := true.B
      }
      when(decReg.isStoreIndB) {
        dataMem.io.wr := true.B
        dataMem.io.wrMask := UIntToOH(effAddrOffReg)
        dataMem.io.wrData := Fill(4, accu(7, 0))
      }
      when(decReg.isLoadAddr) {
        addrReg := accu
      }
    }
  }

  io.accu := accu
  io.pc := pcReg
  io.exit := decReg.exit
}
