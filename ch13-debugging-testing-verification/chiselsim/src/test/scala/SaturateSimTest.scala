import chisel3._
import chisel3.simulator.Exceptions
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

// The other half of §13.4's comparison. `Saturate` wraps for exactly one input
// out of 2^32, and these tests show what that means for simulation: a random
// search does not find it, but once formal has named the input, reproducing the
// failure takes a single poke.
class SaturateSimTest extends AnyFlatSpec with ChiselSim {

  "A random search" should "not find the bug in 100000 tries" in {
    val rng = new scala.util.Random(42) // fixed seed, so this is deterministic
    simulate(new Saturate()) { dut =>
      for (_ <- 0 until 100000) {
        dut.io.in.poke((rng.nextLong() & 0xffffffffL).U)
        dut.clock.step()
      }
    }
    // Reaching this line is the result: 100000 random inputs, no violation.
    // At one failing value in 2^32 the chance of hitting it is about 0.0023%.
  }

  "The counterexample formal produced" should "fail on the first cycle" in {
    intercept[Exceptions.AssertionFailed] {
      simulate(new Saturate()) { dut =>
        dut.io.in.poke("hffffffff".U) // the input named in Saturate.bmc.vcd
        dut.clock.step()
      }
    }
  }

  "SaturateFixed" should "survive the same input" in {
    simulate(new SaturateFixed()) { dut =>
      dut.io.in.poke("hffffffff".U)
      dut.clock.step()
    }
  }
}
