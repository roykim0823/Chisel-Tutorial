package axi4

import chisel3._
import chisel3.util._
import axi.AxiResp

// Burst types carried on AW/AR. WRAP (2) is used for cache-line fills, where
// the address wraps inside an aligned block; it is left out of the memories
// below and mentioned in the appendix as an exercise.
object Axi4Burst {
  val fixed = 0.U(2.W)      // every beat hits the same address (e.g. a FIFO port)
  val incr = 1.U(2.W)       // address advances by the transfer size each beat
  val wrap = 2.U(2.W)
}

// Address channel. Compared with AXI4-Lite this adds the three fields that make
// a burst -- `len`, `size`, `burst` -- and the `id` tag that lets a master have
// several transactions outstanding at once.
class Axi4Addr(addrWidth: Int, idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val addr = UInt(addrWidth.W)
  val len = UInt(8.W)       // beats in this burst, minus one (so 0 = 1 beat)
  val size = UInt(3.W)      // bytes per beat, log2 (2 = 4 bytes = 32 bits)
  val burst = UInt(2.W)
  val prot = UInt(3.W)
}

// Write data. `last` marks the final beat -- the slave counts beats off the
// data channel rather than trusting `len` here, which is why W carries no id.
class Axi4WrData extends Bundle {
  val data = UInt(32.W)
  val strb = UInt(4.W)
  val last = Bool()
}

class Axi4RdData(idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val data = UInt(32.W)
  val resp = UInt(2.W)
  val last = Bool()
}

// The B channel. Named for what it is -- a write response -- to keep it apart
// from the `AxiResp` codes.
class Axi4WrResp(idWidth: Int) extends Bundle {
  val id = UInt(idWidth.W)
  val resp = UInt(2.W)
}

// The read half of AXI4, for slaves that only serve reads.
class Axi4ReadIO(addrWidth: Int, idWidth: Int) extends Bundle {
  val ar = Decoupled(new Axi4Addr(addrWidth, idWidth))
  val r = Flipped(Decoupled(new Axi4RdData(idWidth)))
}

// Full AXI4, master's point of view. Same five channels as AXI4-Lite; the
// difference is entirely in what the bundles carry.
class Axi4IO(addrWidth: Int, idWidth: Int) extends Bundle {
  val aw = Decoupled(new Axi4Addr(addrWidth, idWidth))
  val w = Decoupled(new Axi4WrData)
  val b = Flipped(Decoupled(new Axi4WrResp(idWidth)))
  val ar = Decoupled(new Axi4Addr(addrWidth, idWidth))
  val r = Flipped(Decoupled(new Axi4RdData(idWidth)))
}

// A burst-capable memory: one read burst and one write burst in flight, each
// completing before the next is accepted. This is where `len`, `burst`, and
// `last` earn their keep -- one address handshake now covers many data beats.
class Axi4Memory(val words: Int = 16, val addrWidth: Int = 8, val idWidth: Int = 4)
    extends Module {
  val io = IO(Flipped(new Axi4IO(addrWidth, idWidth)))

  val mem = RegInit(VecInit(Seq.fill(words)(0.U(32.W))))
  private val hi = log2Ceil(words) + 1
  private def index(a: UInt) = a(hi, 2)

  // Apply the write strobes byte lane by byte lane: a Scala `map` over the four
  // lanes builds four muxes, which `Cat` reassembles into the new word.
  private def merge(old: UInt, data: UInt, strb: UInt) =
    Cat((3 to 0 by -1).map(b => Mux(strb(b), data(8 * b + 7, 8 * b), old(8 * b + 7, 8 * b))))

  // --- write burst --------------------------------------------------------
  val wIdle :: wBurst :: wResp :: Nil = Enum(3)
  val wState = RegInit(wIdle)
  val wIdReg = RegInit(0.U(idWidth.W))
  val wAddrReg = RegInit(0.U(addrWidth.W))
  val wFixedReg = RegInit(false.B)

  io.aw.ready := wState === wIdle
  io.w.ready := wState === wBurst
  io.b.valid := wState === wResp
  io.b.bits.id := wIdReg
  io.b.bits.resp := AxiResp.okay

  when(io.aw.fire) {
    wIdReg := io.aw.bits.id
    wAddrReg := io.aw.bits.addr
    wFixedReg := io.aw.bits.burst === Axi4Burst.fixed
    wState := wBurst
  }
  when(io.w.fire) {
    mem(index(wAddrReg)) := merge(mem(index(wAddrReg)), io.w.bits.data, io.w.bits.strb)
    when(!wFixedReg) {
      wAddrReg := wAddrReg + 4.U
    }
    // One response per burst, not per beat -- that is the whole saving.
    when(io.w.bits.last) {
      wState := wResp
    }
  }
  when(io.b.fire) {
    wState := wIdle
  }

  // --- read burst ---------------------------------------------------------
  val rIdle :: rBurst :: Nil = Enum(2)
  val rState = RegInit(rIdle)
  val rIdReg = RegInit(0.U(idWidth.W))
  val rAddrReg = RegInit(0.U(addrWidth.W))
  val rCntReg = RegInit(0.U(8.W))
  val rFixedReg = RegInit(false.B)

  io.ar.ready := rState === rIdle
  io.r.valid := rState === rBurst
  io.r.bits.id := rIdReg
  io.r.bits.data := mem(index(rAddrReg))
  io.r.bits.resp := AxiResp.okay
  io.r.bits.last := rCntReg === 0.U

  when(io.ar.fire) {
    rIdReg := io.ar.bits.id
    rAddrReg := io.ar.bits.addr
    rCntReg := io.ar.bits.len          // len is beats-minus-one, so it doubles
    rFixedReg := io.ar.bits.burst === Axi4Burst.fixed
    rState := rBurst
  }
  when(io.r.fire) {
    when(!rFixedReg) {
      rAddrReg := rAddrReg + 4.U
    }
    rCntReg := rCntReg - 1.U
    when(io.r.bits.last) {
      rState := rIdle
    }
  }
}

// A read-only memory that completes transactions **out of order**.
//
// It keeps a small table of accepted read commands and serves whichever one is
// ready first, tagging each data beat with the `id` the master supplied. To
// make the reordering visible without modelling a real memory hierarchy, each
// command is given an artificial delay of `id * 4` cycles -- a stand-in for the
// real reasons latency varies (a bank conflict, a cache miss, a slow peripheral
// behind the same port). A master that issues id 1 and then id 0 gets id 0 back
// first, which is exactly the behaviour the ids exist to make safe.
//
// AXI4 requires the beats of one burst to be contiguous -- read data of
// different ids may not be interleaved (AXI3 allowed that and AXI4 dropped it),
// so a burst, once started, runs to its `last` beat before another is served.
class Axi4OooReadMemory(val words: Int = 16, val addrWidth: Int = 8,
                        val idWidth: Int = 4, val slots: Int = 2) extends Module {
  val io = IO(Flipped(new Axi4ReadIO(addrWidth, idWidth)))

  // Distinct contents so a test can tell which address a beat came from.
  val mem = RegInit(VecInit(Seq.tabulate(words)(i => (0x100 + i).U(32.W))))
  private val hi = log2Ceil(words) + 1

  val busyRegs = RegInit(VecInit(Seq.fill(slots)(false.B)))
  val idRegs = RegInit(VecInit(Seq.fill(slots)(0.U(idWidth.W))))
  val addrRegs = RegInit(VecInit(Seq.fill(slots)(0.U(addrWidth.W))))
  val cntRegs = RegInit(VecInit(Seq.fill(slots)(0.U(8.W))))
  val delayRegs = RegInit(VecInit(Seq.fill(slots)(0.U(8.W))))
  val fixedRegs = RegInit(VecInit(Seq.fill(slots)(false.B)))

  val servingReg = RegInit(false.B)
  val slotReg = RegInit(0.U(log2Ceil(slots).W))

  // Count every waiting command down towards "ready to serve".
  for (i <- 0 until slots) {
    when(busyRegs(i) && delayRegs(i) =/= 0.U) {
      delayRegs(i) := delayRegs(i) - 1.U
    }
  }

  // --- accept a command into any free slot --------------------------------
  val free = VecInit(busyRegs.map(!_))
  val hasFree = free.reduce(_ || _)
  val freeSlot = PriorityEncoder(free)

  io.ar.ready := hasFree
  when(io.ar.fire) {
    busyRegs(freeSlot) := true.B
    idRegs(freeSlot) := io.ar.bits.id
    addrRegs(freeSlot) := io.ar.bits.addr
    cntRegs(freeSlot) := io.ar.bits.len
    fixedRegs(freeSlot) := io.ar.bits.burst === Axi4Burst.fixed
    delayRegs(freeSlot) := io.ar.bits.id << 2
  }

  // --- pick the next burst to serve ---------------------------------------
  val ready = VecInit((0 until slots).map(i => busyRegs(i) && delayRegs(i) === 0.U))
  when(!servingReg && ready.reduce(_ || _)) {
    servingReg := true.B
    slotReg := PriorityEncoder(ready)
  }

  io.r.valid := servingReg
  io.r.bits.id := idRegs(slotReg)
  io.r.bits.data := mem(addrRegs(slotReg)(hi, 2))
  io.r.bits.resp := AxiResp.okay
  io.r.bits.last := cntRegs(slotReg) === 0.U

  when(io.r.fire) {
    when(!fixedRegs(slotReg)) {
      addrRegs(slotReg) := addrRegs(slotReg) + 4.U
    }
    cntRegs(slotReg) := cntRegs(slotReg) - 1.U
    when(io.r.bits.last) {
      busyRegs(slotReg) := false.B
      servingReg := false.B
    }
  }
}
