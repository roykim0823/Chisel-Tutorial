package uart

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Send a byte through Tx and back out of Rx over the looped-back serial wire.
// A moderate frequency/baud ratio keeps a full 11-bit frame short enough to
// simulate while still sampling robustly.
class UartTest extends AnyFlatSpec with ChiselScalatestTester {
  "Uart loopback" should "transmit and receive a byte" in {
    test(new UartLoopback(frequency = 1000, baudRate = 10)) { dut =>
      dut.clock.setTimeout(0)   // a full frame takes >1000 cycles at this ratio
      dut.io.out.ready.poke(true.B)
      dut.io.in.valid.poke(false.B)

      // Let the Rx finish its initial idle countdown and settle to "listening"
      // (the looped-back line must be idle-high before the start bit).
      dut.clock.step(200)

      // Now transmit one byte.
      dut.io.in.bits.poke('A'.U)
      dut.io.in.valid.poke(true.B)
      var guard = 0
      while (!dut.io.in.ready.peekBoolean() && guard < 200) { dut.clock.step(); guard += 1 }
      dut.clock.step()            // one cycle for the Tx to latch the byte
      dut.io.in.valid.poke(false.B)

      // Wait for the Rx to reassemble and present the byte.
      guard = 0
      while (!dut.io.out.valid.peekBoolean() && guard < 5000) { dut.clock.step(); guard += 1 }
      dut.io.out.valid.expect(true.B)
      dut.io.out.bits.expect('A'.U)
    }
  }
}
