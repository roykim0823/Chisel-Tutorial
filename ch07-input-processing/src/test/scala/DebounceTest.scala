import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Use a small divide factor so the sample period is short enough to simulate.
// Drive a bouncing press (a brief glitch, then a steady press) and confirm the
// LED counter only advances after the signal is stable across several samples.
class DebounceTest extends AnyFlatSpec with ChiselScalatestTester {
  val FAC = 100

  "Debounce" should "ignore bounces and rise once" in {
    test(new Debounce(FAC)) { dut =>
      dut.io.btnU.poke(false.B)
      dut.clock.step(3)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC / 3)
      dut.io.btnU.poke(true.B)   // a short glitch...
      dut.clock.step(FAC / 30)
      dut.io.btnU.poke(false.B)
      dut.clock.step(FAC / 30)
      dut.io.btnU.poke(true.B)   // ...then a steady press
      dut.clock.step(FAC)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC)
      dut.io.led.expect(true.B)  // counter finally advances
      dut.clock.step(FAC)
      dut.io.led.expect(true.B)
    }
  }

  "DebounceFunc" should "behave the same" in {
    test(new DebounceFunc(FAC)) { dut =>
      dut.io.btnU.poke(false.B)
      dut.clock.step(3)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC / 3)
      dut.io.btnU.poke(true.B)
      dut.clock.step(FAC / 30)
      dut.io.btnU.poke(false.B)
      dut.clock.step(FAC / 30)
      dut.io.btnU.poke(true.B)
      dut.clock.step(FAC)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC)
      dut.io.led.expect(false.B)
      dut.clock.step(FAC)
      dut.io.led.expect(true.B)
    }
  }
}
