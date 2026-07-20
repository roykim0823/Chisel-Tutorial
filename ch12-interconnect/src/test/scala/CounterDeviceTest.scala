import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class CounterDeviceTest extends AnyFlatSpec with ChiselScalatestTester {

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
}
