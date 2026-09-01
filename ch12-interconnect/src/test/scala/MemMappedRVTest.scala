import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._

class MemMappedRVTest extends AnyFlatSpec with ChiselScalatestTester {

  // The device's 16-byte window, as byte offsets.
  private val status = 0
  private val data = 4
  private val count = 8

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
      def write(addr: Int, value: Int) = {
        dut.io.mem.address.poke(addr.U)
        dut.io.mem.wrData.poke(value.U)
        dut.io.mem.wr.poke(true.B)
        step()
        dut.io.mem.wr.poke(false.B)
        while (!dut.io.mem.ack.peekBoolean()) step()
      }

      step(5)
      assert(read(status) == 1, "TX flag should be set (FIFO ready)")
      write(data, 123)                  // transmit -> into the FIFO
      step(10)
      assert(read(status) == 3, "TX and RX flags should be set")
      assert(read(data) == 123, "receive value should match what was sent")
      assert(read(count) == 1, "one word has been read out of the stream")
    }
  }

  it should "not transmit on a write to the control word" in {
    test(new UseMemMappedRV(UInt(16.W))) { dut =>
      def step(n: Int = 1) = dut.clock.step(n)
      def write(addr: Int, value: Int) = {
        dut.io.mem.address.poke(addr.U)
        dut.io.mem.wrData.poke(value.U)
        dut.io.mem.wr.poke(true.B)
        step()
        dut.io.mem.wr.poke(false.B)
        while (!dut.io.mem.ack.peekBoolean()) step()
      }

      step(5)
      write(status, 0)                  // control write: must not enqueue
      step(10)
      dut.io.mem.address.poke(status.U)
      dut.io.mem.rd.poke(true.B)
      step()
      dut.io.mem.rd.poke(false.B)
      while (!dut.io.mem.ack.peekBoolean()) step()
      dut.io.mem.rdData.expect(1.U, "RDRF must still be clear: nothing was sent")
    }
  }

  it should "raise irq only for an enabled condition" in {
    test(new UseMemMappedRV(UInt(16.W))) { dut =>
      def step(n: Int = 1) = dut.clock.step(n)
      def write(addr: Int, value: Int) = {
        dut.io.mem.address.poke(addr.U)
        dut.io.mem.wrData.poke(value.U)
        dut.io.mem.wr.poke(true.B)
        step()
        dut.io.mem.wr.poke(false.B)
        while (!dut.io.mem.ack.peekBoolean()) step()
      }

      step(5)
      dut.io.irq.expect(false.B, "no interrupt while the control word is zero")

      write(status, 2)                  // enable the "data to read" interrupt
      step()
      dut.io.irq.expect(false.B, "still nothing to read")

      write(data, 55)                   // send one word round the loopback
      step(10)
      dut.io.irq.expect(true.B, "RDRF is set and its interrupt is enabled")
    }
  }
}
