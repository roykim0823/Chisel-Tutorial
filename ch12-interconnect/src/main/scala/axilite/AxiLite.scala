package axilite

import chisel3._
import chisel3.util._
import axi.AxiResp

class AxiLiteAddr(addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W)      // privilege/security/instruction attributes
}

class AxiLiteWrData extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)      // write strobes: which byte lanes are valid
}

class AxiLiteRdData extends Bundle {
  val data = UInt(32.W)
  val resp = UInt(2.W)
}

// The B channel. Named for what it is -- a write response -- to match AXI4's
// `Axi4WrResp` and keep it apart from the `AxiResp` codes.
class AxiLiteWrResp extends Bundle {
  val resp = UInt(2.W)
}

// AXI4-Lite from the *master's* point of view: three request channels it drives
// and two response channels it receives. Every one of the five is an
// independent ready/valid (`Decoupled`) handshake -- the same discipline as
// Chapter 9, applied five times over. A slave uses `Flipped(new AxiLiteIO(n))`.
class AxiLiteIO(addrWidth: Int) extends Bundle {
  val aw = Decoupled(new AxiLiteAddr(addrWidth))    // write address
  val w = Decoupled(new AxiLiteWrData)              // write data
  val b = Flipped(Decoupled(new AxiLiteWrResp))       // write response
  val ar = Decoupled(new AxiLiteAddr(addrWidth))    // read address
  val r = Flipped(Decoupled(new AxiLiteRdData))     // read data
}

// The four free-running loadable counters once more, now as an AXI4-Lite slave.
//
// The interesting part is the write path. AW and W are separate channels with
// no ordering between them, so the slave may see the address first, the data
// first, or both together, and has to cope with all three. It therefore accepts
// each channel into its own holding register and performs the write once both
// halves have arrived -- which is what the chapter means by "a more complex
// slave able to accept the two in any order".
class AxiLiteCounter extends Module {
  val io = IO(Flipped(new AxiLiteIO(4)))

  val cntRegs = RegInit(VecInit(Seq.fill(4)(0.U(32.W))))
  for (i <- 0 until 4) {
    cntRegs(i) := cntRegs(i) + 1.U
  }

  // --- write address and write data, captured independently ---------------
  val awIdxReg = RegInit(0.U(2.W))
  val awFullReg = RegInit(false.B)
  val wDataReg = RegInit(0.U(32.W))
  val wFullReg = RegInit(false.B)
  val bValidReg = RegInit(false.B)

  io.aw.ready := !awFullReg             // room for one address
  io.w.ready := !wFullReg               // room for one data beat

  when(io.aw.fire) {
    awIdxReg := io.aw.bits.addr(3, 2)
    awFullReg := true.B
  }
  when(io.w.fire) {
    wDataReg := io.w.bits.data
    wFullReg := true.B
  }

  // Both halves present (in whichever order they arrived) and the previous
  // response already taken: perform the write and raise the response.
  when(awFullReg && wFullReg && !bValidReg) {
    cntRegs(awIdxReg) := wDataReg
    awFullReg := false.B
    wFullReg := false.B
    bValidReg := true.B
  }
  when(io.b.fire) {
    bValidReg := false.B
  }

  io.b.valid := bValidReg
  io.b.bits.resp := AxiResp.okay

  // --- read ---------------------------------------------------------------
  // One outstanding read: the address is accepted, the counter sampled into a
  // register, and the data offered on R until the master takes it.
  val rDataReg = RegInit(0.U(32.W))
  val rValidReg = RegInit(false.B)

  io.ar.ready := !rValidReg
  when(io.ar.fire) {
    rDataReg := cntRegs(io.ar.bits.addr(3, 2))
    rValidReg := true.B
  }
  when(io.r.fire) {
    rValidReg := false.B
  }

  io.r.valid := rValidReg
  io.r.bits.data := rDataReg
  io.r.bits.resp := AxiResp.okay
}
