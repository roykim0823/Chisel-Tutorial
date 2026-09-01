import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import axi4._
import axilite._
import soc._
import wishbone._

// Cross-protocol throughput comparison: Wishbone, AXI4-Lite, and full AXI4
// slaves kept maximally busy, each counted over the same window, so the rate
// claims in Section 12.5 and the recap table are numbers rather than assertions.
// The three ReqAckIO schemes of Section 12.3 are measured the same way in
// CounterDeviceTest, next to the devices they belong to.
class HandshakeStylesTest extends AnyFlatSpec with ChiselScalatestTester {

  "A combinational slave with wait states" should
      "hold ack low until its access time has passed (Figure 12.3)" in {
    test(new WishboneCounterWait(2)) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)

      // The request cycle and the one after it are wait states; the ack lands
      // in the third cycle, exactly as the figure draws it.
      dut.io.ack.expect(false.B, "request cycle: the device is not ready yet")
      dut.clock.step()
      dut.io.ack.expect(false.B, "one wait state still to go")
      dut.clock.step()
      dut.io.ack.expect(true.B, "ack in the third cycle of the request")
    }
  }

  it should "drive ack combinationally, not from a register" in {
    test(new WishboneCounterWait(2)) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)
      dut.clock.step(2)
      dut.io.ack.expect(true.B)

      // Withdrawing the request withdraws the ack in the *same* cycle, with no
      // clock step in between. A registered ack could not do this -- which is
      // the whole objection to the combinational handshake: this path runs from
      // the master, through address decoding and the slave, and back.
      dut.io.cyc.poke(false.B)
      dut.io.ack.expect(false.B, "ack tracks cyc within the cycle")
      dut.io.cyc.poke(true.B)
      dut.io.ack.expect(true.B, "and comes straight back")
    }
  }

  it should "complete one transaction every three cycles" in {
    test(new WishboneCounterWait(2)) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)

      var acks = 0
      for (_ <- 0 until 9) {
        dut.clock.step()
        if (dut.io.ack.peekBoolean()) acks += 1
      }
      assert(acks == 3, s"two wait states means one transfer per three cycles, got $acks")
    }
  }

  "A registered slave" should "complete one transaction every two cycles" in {
    test(new WishboneCounterSync()) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)

      // The request is held continuously, so the slave is never idle -- and
      // still it can only answer every other cycle, because the master has to
      // keep presenting the request through the ack cycle.
      var acks = 0
      for (_ <- 0 until 8) {
        dut.clock.step()
        if (dut.io.ack.peekBoolean()) acks += 1
      }
      assert(acks == 4, s"a registered slave acks every other cycle, got $acks in 8")
    }
  }

  // --- the ready/valid slaves ---------------------------------------------
  // `Decoupled` fixes one axis of the taxonomy: a source always holds `valid`
  // until `ready`. So the question for AXI is not "does the master hold?" but
  // how many transactions the slave will accept at once.

  private val Window = 16

  "An AXI4-Lite slave" should "also manage only one transfer every two cycles" in {
    test(new AxiLiteCounter()) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.w.valid.poke(false.B)
      dut.io.b.ready.poke(false.B)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.prot.poke(0.U)
      dut.io.ar.valid.poke(true.B)
      dut.io.r.ready.poke(true.B)

      var beats = 0
      for (_ <- 0 until Window) {
        dut.clock.step()
        if (dut.io.r.valid.peekBoolean()) beats += 1
      }
      // One outstanding read, so acceptance and completion serialise and the
      // rate lands exactly on the registered scheme's.
      assert(beats == Window / 2, s"expected one read per two cycles, got $beats in $Window")
    }
  }

  private def axi4ReadRate(len: Int): Int = {
    var beats = 0
    test(new Axi4Memory()) { dut =>
      dut.io.aw.valid.poke(false.B)
      dut.io.w.valid.poke(false.B)
      dut.io.b.ready.poke(false.B)
      dut.io.ar.bits.id.poke(0.U)
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.len.poke(len.U)
      dut.io.ar.bits.size.poke(2.U)
      dut.io.ar.bits.burst.poke(Axi4Burst.incr)
      dut.io.ar.bits.prot.poke(0.U)
      dut.io.ar.valid.poke(true.B)
      dut.io.r.ready.poke(true.B)

      for (_ <- 0 until Window) {
        dut.clock.step()
        if (dut.io.r.valid.peekBoolean()) beats += 1
      }
    }
    beats
  }

  "A full AXI4 memory" should "match that rate on single-beat reads" in {
    assert(axi4ReadRate(0) == Window / 2,
      s"len=0 should behave like AXI4-Lite, got ${axi4ReadRate(0)} in $Window")
  }

  it should "reach one beat per cycle inside a burst" in {
    // This is where AXI's structure pays: one address handshake amortised over
    // eight beats, which no request/acknowledge device in the chapter can do.
    val beats = axi4ReadRate(7)
    assert(beats == Window - 1, s"expected near one beat per cycle, got $beats in $Window")
  }

  "An out-of-order AXI4 memory" should "buy ordering freedom, not throughput" in {
    test(new Axi4OooReadMemory()) { dut =>
      dut.io.ar.bits.id.poke(0.U)          // id 0 -> no artificial delay
      dut.io.ar.bits.addr.poke(0.U)
      dut.io.ar.bits.len.poke(0.U)
      dut.io.ar.bits.size.poke(2.U)
      dut.io.ar.bits.burst.poke(Axi4Burst.incr)
      dut.io.ar.bits.prot.poke(0.U)
      dut.io.ar.valid.poke(true.B)
      dut.io.r.ready.poke(true.B)

      var beats = 0
      for (_ <- 0 until Window) {
        dut.clock.step()
        if (dut.io.r.valid.peekBoolean()) beats += 1
      }
      // Two slots, yet still one transfer per two cycles: `servingReg` is set a
      // cycle after a slot becomes ready and cleared on the last beat, so every
      // burst is followed by a dead cycle. The slots buy reordering, not rate.
      assert(beats == Window / 2, s"expected one read per two cycles, got $beats in $Window")
    }
  }
}
