import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._
import wishbone._

// Measures the cost of putting Wishbone underneath a pipelined master. Both
// devices under test expose the same `ReqAckIO(4)` port and hold the same four
// counters, so the only difference the numbers can reflect is the protocol.
class ReqAckToWishboneTest extends AnyFlatSpec with ChiselScalatestTester {

  // Issue a read and return how many clock cycles after the command the ack
  // appeared. A native pipelined slave manages 1 -- the minimum the scheme
  // allows.
  private def readLatency(io: ReqAckIO, clock: Clock, addr: Int): (BigInt, Int) = {
    io.address.poke(addr.U)
    io.rd.poke(true.B)
    clock.step()
    io.rd.poke(false.B)
    var cycles = 1
    while (!io.ack.peekBoolean()) {
      clock.step()
      cycles += 1
    }
    val value = io.rdData.peekInt()
    clock.step()
    (value, cycles)
  }

  private def write(io: ReqAckIO, clock: Clock, addr: Int, data: Int): Unit = {
    io.address.poke(addr.U)
    io.wrData.poke(data.U)
    io.wrMask.poke(15.U)
    io.wr.poke(true.B)
    clock.step()
    io.wr.poke(false.B)
    while (!io.ack.peekBoolean()) clock.step()
    clock.step()
  }

  "The Wishbone bridge" should "cost exactly one extra cycle of latency" in {
    var native = 0
    test(new CounterDevice()) { dut =>
      native = readLatency(dut.io, dut.clock, 0)._2
    }

    var bridged = 0
    test(new BridgedWishboneCounter()) { dut =>
      bridged = readLatency(dut.io, dut.clock, 0)._2
    }

    assert(native == 1, s"a native pipelined slave acks after one cycle, got $native")
    assert(bridged == native + 1,
      s"registering the command for Wishbone costs one cycle: $native -> $bridged")
  }

  it should "still move data correctly" in {
    test(new BridgedWishboneCounter()) { dut =>
      write(dut.io, dut.clock, 8, 2000)   // byte address 8 -> counter 2
      val (value, _) = readLatency(dut.io, dut.clock, 8)
      // Free-running counters again: the value has ticked on a few cycles.
      assert(value >= 2000 && value < 2010, s"expected just over 2000, got $value")
    }
  }
}
