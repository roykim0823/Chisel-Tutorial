import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import axi.AxiResp
import axi4._

// Exercises the three things full AXI4 adds over AXI4-Lite: multi-beat bursts,
// the FIXED burst type, and out-of-order completion tagged by transaction id.
class Axi4MemoryTest extends AnyFlatSpec with ChiselScalatestTester {

  private def sendAddr(ch: DecoupledIO[Axi4Addr], clock: Clock,
                       id: Int, addr: Int, len: Int, burst: UInt): Unit = {
    ch.bits.id.poke(id.U)
    ch.bits.addr.poke(addr.U)
    ch.bits.len.poke(len.U)             // beats minus one
    ch.bits.size.poke(2.U)              // 2^2 = 4 bytes per beat
    ch.bits.burst.poke(burst)
    ch.bits.prot.poke(0.U)
    ch.valid.poke(true.B)
    while (!ch.ready.peekBoolean()) clock.step()
    clock.step()
    ch.valid.poke(false.B)
  }

  "An AXI4 memory" should "write and read back a four-beat INCR burst" in {
    test(new Axi4Memory()) { dut =>
      sendAddr(dut.io.aw, dut.clock, id = 2, addr = 0, len = 3, burst = Axi4Burst.incr)

      // One address handshake, four data beats -- that is the point of a burst.
      for (i <- 0 until 4) {
        dut.io.w.bits.data.poke((0xa0 + i).U)
        dut.io.w.bits.strb.poke(15.U)
        dut.io.w.bits.last.poke((i == 3).B)
        dut.io.w.valid.poke(true.B)
        while (!dut.io.w.ready.peekBoolean()) dut.clock.step()
        dut.clock.step()
      }
      dut.io.w.valid.poke(false.B)

      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.io.b.bits.id.expect(2.U, "the response carries the id of its burst")
      dut.io.b.bits.resp.expect(AxiResp.okay)
      dut.clock.step()
      dut.io.b.ready.poke(false.B)

      sendAddr(dut.io.ar, dut.clock, id = 2, addr = 0, len = 3, burst = Axi4Burst.incr)
      dut.io.r.ready.poke(true.B)
      for (i <- 0 until 4) {
        while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
        dut.io.r.bits.id.expect(2.U)
        dut.io.r.bits.data.expect((0xa0 + i).U, s"beat $i")
        dut.io.r.bits.last.expect((i == 3).B, s"last only on beat 3, checked at $i")
        dut.clock.step()
      }
    }
  }

  it should "hold the address across a FIXED burst" in {
    test(new Axi4Memory()) { dut =>
      sendAddr(dut.io.aw, dut.clock, id = 0, addr = 0, len = 0, burst = Axi4Burst.incr)
      dut.io.w.bits.data.poke(0x55.U)
      dut.io.w.bits.strb.poke(15.U)
      dut.io.w.bits.last.poke(true.B)
      dut.io.w.valid.poke(true.B)
      while (!dut.io.w.ready.peekBoolean()) dut.clock.step()
      dut.clock.step()
      dut.io.w.valid.poke(false.B)
      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.clock.step()
      dut.io.b.ready.poke(false.B)

      // Two beats, FIXED: both must come from word 0, not 0 and then 1.
      sendAddr(dut.io.ar, dut.clock, id = 0, addr = 0, len = 1, burst = Axi4Burst.fixed)
      dut.io.r.ready.poke(true.B)
      for (i <- 0 until 2) {
        while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
        dut.io.r.bits.data.expect(0x55.U, s"FIXED beat $i stays at the same address")
        dut.clock.step()
      }
    }
  }

  "An out-of-order AXI4 memory" should "complete the later request first" in {
    test(new Axi4OooReadMemory()) { dut =>
      // id 1 is given the slow path (id * 4 cycles), id 0 the fast one. Issue
      // the slow one first; the ids are what make it safe to answer them in the
      // opposite order.
      sendAddr(dut.io.ar, dut.clock, id = 1, addr = 0, len = 0, burst = Axi4Burst.incr)
      sendAddr(dut.io.ar, dut.clock, id = 0, addr = 4, len = 0, burst = Axi4Burst.incr)

      dut.io.r.ready.poke(true.B)

      while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
      dut.io.r.bits.id.expect(0.U, "the fast request comes back first")
      dut.io.r.bits.data.expect(0x101.U, "word 1, as addressed by the id-0 request")
      dut.io.r.bits.last.expect(true.B)
      dut.clock.step()

      while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
      dut.io.r.bits.id.expect(1.U, "the slow request follows")
      dut.io.r.bits.data.expect(0x100.U, "word 0, as addressed by the id-1 request")
      dut.io.r.bits.last.expect(true.B)
      dut.clock.step()
    }
  }
}
