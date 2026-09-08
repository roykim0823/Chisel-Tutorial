import chisel3._
import chisel3.simulator.Exceptions
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

// The chapter's AssertTest, rewritten for ChiselSim - plus the case the
// chiseltest version never makes: proving that a false assertion really does
// stop the simulation.
class AssertSimTest extends AnyFlatSpec with ChiselSim {
  "Assert" should "hold (even across an overflowing add)" in {
    simulate(new Assert()) { dut =>
      dut.io.a.poke(1.U)
      dut.io.b.poke(2.U)
      dut.clock.step()
      dut.io.a.poke(100.U)
      dut.io.b.poke(200.U) // 300 wraps to 44 in 8 bits; the assert still holds
      dut.clock.step()
    }
  }

  // AssertOverflow asserts `io.sum >= io.a`, which is false whenever the 8-bit
  // add wraps. A passing test here means the assertion fired.
  "AssertOverflow" should "stop the simulation when its assertion fails" in {
    intercept[Exceptions.AssertionFailed] {
      simulate(new AssertOverflow()) { dut =>
        dut.io.a.poke(100.U)
        dut.io.b.poke(200.U) // 44 >= 100 is false
        dut.clock.step()
      }
    }
  }

  it should "be satisfied when the add does not overflow" in {
    simulate(new AssertOverflow()) { dut =>
      dut.io.a.poke(100.U)
      dut.io.b.poke(20.U) // 120 >= 100 holds
      dut.clock.step()
    }
  }
}
