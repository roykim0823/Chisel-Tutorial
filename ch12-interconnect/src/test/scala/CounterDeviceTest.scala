import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._

class CounterDeviceTest extends AnyFlatSpec with ChiselScalatestTester {

  // "Bit banging": every pin of the protocol is poked and expected by hand.
  // It works, but it covers only a couple of cases and is already hard to read.
  "CounterDevice" should "work" in {
    test(new CounterDevice()) { dut =>
      dut.io.ack.expect(false.B)
      dut.clock.step()
      dut.io.address.poke(0.U)
      dut.io.rd.poke(true.B)
      dut.io.ack.expect(false.B)
      dut.clock.step()
      dut.io.rd.poke(false.B)
      dut.io.ack.expect(true.B)
      dut.clock.step(100)
      dut.io.rd.poke(true.B)
      dut.io.address.poke(4.U)
      dut.clock.step()
      assert(dut.io.rdData.peekInt() > 100)
      dut.io.wr.poke(true.B)
      dut.io.wrData.poke(0.U)
      dut.clock.step()
      dut.io.wr.poke(false.B)
      dut.io.rd.poke(true.B)
      dut.clock.step()
      dut.io.rdData.expect(1.U)
      dut.io.address.poke(0.U)
      dut.clock.step()
      assert(dut.io.rdData.peekInt() > 100)
    }
  }

  // A readable test that wraps the pipelined protocol in read()/write() helpers.
  "CounterDevice" should "read, advance, and load counters" in {
    test(new CounterDevice()) { dut =>
      def step(n: Int = 1) = dut.clock.step(n)

      def read(addr: Int) = {
        dut.io.address.poke(addr.U)
        dut.io.rd.poke(true.B)
        step()
        dut.io.rd.poke(false.B)
        while (!dut.io.ack.peekBoolean()) step()   // wait for the delayed ack
        dut.io.rdData.peekInt()
      }
      def write(addr: Int, data: Int) = {
        dut.io.address.poke(addr.U)
        dut.io.wrData.poke(data.U)
        dut.io.wr.poke(true.B)
        step()
        dut.io.wr.poke(false.B)
        while (!dut.io.ack.peekBoolean()) step()
      }

      for (i <- 0 until 4) assert(read(i * 4) < 10, s"counter $i just started")
      step(100)
      for (i <- 0 until 4) assert(read(i * 4) > 100, s"counter $i advanced")
      write(2 * 4, 0)
      write(3 * 4, 1000)
      assert(read(2 * 4) < 5, "counter reset")
      assert(read(3 * 4) > 1000, "counter loaded")
    }
  }

  // --- one port, one device, three handshakes ------------------------------
  // CounterDevice, CounterDeviceComb, and CounterDeviceReg all speak ReqAckIO
  // and hold the same four counters, so the only thing the rates below can
  // reflect is the handshake. Section 12.3.4 tabulates them.

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
}
