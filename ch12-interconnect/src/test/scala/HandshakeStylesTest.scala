import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import axi4._
import axilite._
import soc._
import wishbone._

// Measures what separates the acknowledgment styles the chapter compares. Each
// test keeps a slave maximally busy and counts how many transfers it completes,
// so the throughput claims in Sections 12.2 to 12.4 are numbers rather than
// assertions.
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

  "A pipelined slave" should "complete one transaction every cycle" in {
    test(new CounterDevice()) { dut =>
      // Issue a new read every cycle without ever waiting for an ack. This is
      // what the single-cycle command buys: the master never has to hold the
      // bus, so a second request can go out while the first is still in flight.
      val n = 6
      var acks = 0
      for (i <- 0 until n) {
        dut.io.address.poke(((i % 4) * 4).U)
        dut.io.rd.poke(true.B)
        dut.clock.step()
        if (dut.io.ack.peekBoolean()) acks += 1
      }
      dut.io.rd.poke(false.B)
      assert(acks == n, s"a pipelined slave sustains one ack per cycle, got $acks in $n")
    }
  }

  // --- the same device, one port, three schemes ---------------------------
  // These four all speak ReqAckIO and hold the same four counters, so the only
  // thing the numbers can reflect is the handshake.

  private def reqAckRate(dut: ReqAckIO, clock: Clock, cycles: Int): Int = {
    dut.address.poke(0.U)
    dut.wrData.poke(0.U)
    dut.wrMask.poke(15.U)
    dut.wr.poke(false.B)
    dut.rd.poke(true.B)                  // request held high throughout
    var acks = 0
    for (_ <- 0 until cycles) {
      clock.step()
      if (dut.ack.peekBoolean()) acks += 1
    }
    acks
  }

  "A combinational ReqAckIO device" should "answer inside the request cycle" in {
    test(new CounterDeviceComb()) { dut =>
      dut.io.address.poke(0.U)
      dut.io.rd.poke(true.B)
      // No clock step: with no wait states the ack is already there.
      dut.io.ack.expect(true.B, "a combinational ack lands in the request cycle")

      // And it is a wire, not a flop: withdrawing the request withdraws the ack
      // in the same cycle.
      dut.io.rd.poke(false.B)
      dut.io.ack.expect(false.B, "ack tracks rd within the cycle")
    }
  }

  it should "sustain one transfer per cycle with no wait states" in {
    test(new CounterDeviceComb(0)) { dut =>
      assert(reqAckRate(dut.io, dut.clock, 12) == 12,
        "a zero-wait combinational device acks every cycle")
    }
  }

  it should "drop to one per three cycles with two wait states" in {
    test(new CounterDeviceComb(2)) { dut =>
      assert(reqAckRate(dut.io, dut.clock, 12) == 4,
        "two wait states means one transfer per three cycles")
    }
  }

  "A registered ReqAckIO device" should "manage one transfer every two cycles" in {
    test(new CounterDeviceReg()) { dut =>
      // The request is held continuously and the device is never idle, yet it
      // can only answer every other cycle -- the cost of keeping the request
      // asserted through the ack cycle.
      assert(reqAckRate(dut.io, dut.clock, 12) == 6,
        "a registered device acks every other cycle")
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
