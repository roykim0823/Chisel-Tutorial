import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PwmTest extends AnyFlatSpec with ChiselScalatestTester {
  "Pwm" should "have a 30% duty cycle on the fixed output" in {
    test(new Pwm) { dut =>
      // io.led bit 0 is the fixed pwm(10, 3.U): high for 3 of every 10 cycles.
      var highs = 0
      for (_ <- 0 until 10) {
        if ((dut.io.led.peekInt() & 1) == 1) highs += 1
        dut.clock.step()
      }
      assert(highs == 3)
    }
  }
}
