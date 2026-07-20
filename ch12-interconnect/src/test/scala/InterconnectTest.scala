import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class InterconnectTest extends AnyFlatSpec with ChiselScalatestTester {
  "MemMappedRV bridge" should "expose status and move data through the FIFO" in {
    test(new UseMemMappedRV(UInt(16.W))) { dut =>
      def step(n: Int = 1) = dut.clock.step(n)

      def read(addr: Int) = {
        dut.io.mem.address.poke(addr.U)
        dut.io.mem.rd.poke(true.B)
        step()
        dut.io.mem.rd.poke(false.B)
        while (!dut.io.mem.ack.peekBoolean()) step()
        dut.io.mem.rdData.peekInt()
      }
      def write(addr: Int, data: Int) = {
        dut.io.mem.address.poke(addr.U)
        dut.io.mem.wrData.poke(data.U)
        dut.io.mem.wr.poke(true.B)
        step()
        dut.io.mem.wr.poke(false.B)
        while (!dut.io.mem.ack.peekBoolean()) step()
      }

      step(5)
      assert(read(0) == 1, "TX flag should be set (FIFO ready)")
      write(1, 123)                     // transmit -> into the FIFO
      step(10)
      assert(read(0) == 3, "TX and RX flags should be set")
      assert(read(1) == 123, "receive value should match what was sent")
    }
  }
}
