import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wishbone._

// Drives the two Wishbone counter slaves with one and the same master routine
// (poll ACK_I, then release CYC_O/STB_O) and checks that each answers with the
// timing its figure shows.
class WishboneCounterTest extends AnyFlatSpec with ChiselScalatestTester {

  "An asynchronous Wishbone slave" should "acknowledge in the request cycle (Figure 12.5)" in {
    test(new WishboneCounter()) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)

      // No clock step: the ack is combinational, so it is already there.
      dut.io.ack.expect(true.B, "an asynchronous slave acks within the request cycle")
      dut.clock.step()

      dut.io.cyc.poke(false.B)
      dut.io.stb.poke(false.B)
      dut.io.ack.expect(false.B, "ack falls with the request")
    }
  }

  "A synchronous Wishbone slave" should "acknowledge one cycle later (Figure 12.6)" in {
    test(new WishboneCounterSync()) { dut =>
      dut.io.sel.poke(15.U)
      dut.io.adr.poke(0.U)
      dut.io.we.poke(false.B)
      dut.io.cyc.poke(true.B)
      dut.io.stb.poke(true.B)

      dut.io.ack.expect(false.B, "a registered slave cannot ack in the request cycle")
      dut.clock.step()
      dut.io.ack.expect(true.B, "the ack arrives on the next clock edge")

      dut.clock.step()
      dut.io.cyc.poke(false.B)
      dut.io.stb.poke(false.B)
    }
  }

  // The same master routine works against either slave because it waits for the
  // ack rather than assuming a fixed latency. `cycles` counts the clock steps
  // spent waiting, which is what separates Figure 12.5 from Figure 12.6.
  private def read(dut: WishboneCounterSync, addr: Int): (BigInt, Int) = {
    dut.io.sel.poke(15.U)
    dut.io.adr.poke(addr.U)
    dut.io.we.poke(false.B)
    dut.io.cyc.poke(true.B)
    dut.io.stb.poke(true.B)
    var cycles = 0
    while (!dut.io.ack.peekBoolean()) {
      dut.clock.step()
      cycles += 1
    }
    val value = dut.io.datRd.peekInt()
    dut.clock.step()
    dut.io.cyc.poke(false.B)
    dut.io.stb.poke(false.B)
    (value, cycles)
  }

  private def write(dut: WishboneCounterSync, addr: Int, data: Int): Unit = {
    dut.io.sel.poke(15.U)
    dut.io.adr.poke(addr.U)
    dut.io.datWr.poke(data.U)
    dut.io.we.poke(true.B)
    dut.io.cyc.poke(true.B)
    dut.io.stb.poke(true.B)
    while (!dut.io.ack.peekBoolean()) dut.clock.step()
    dut.clock.step()
    dut.io.cyc.poke(false.B)
    dut.io.stb.poke(false.B)
    dut.io.we.poke(false.B)
  }

  it should "load and read back a counter" in {
    test(new WishboneCounterSync()) { dut =>
      write(dut, 4, 1000)                 // byte address 4 -> counter 1
      val (value, cycles) = read(dut, 4)
      assert(cycles == 1, "a registered slave takes one extra cycle")
      // The counters free-run, so the value has advanced by the handful of
      // cycles the read itself took.
      assert(value >= 1000 && value < 1010, s"expected just over 1000, got $value")
    }
  }
}
