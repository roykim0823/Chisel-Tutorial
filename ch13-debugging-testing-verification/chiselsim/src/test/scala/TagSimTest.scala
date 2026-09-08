import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec

// Tags are a ScalaTest feature, not a simulator feature, so §13.2.2's filters
// work on ChiselSim tests unchanged. `Slow` marks the tests that pay for a
// Verilator build; exclude them with
//   sbt "testOnly * -- -l Slow"
object Slow extends Tag("Slow")

class TagSimTest extends AnyFlatSpec with ChiselSim {
  "A tagged ChiselSim test" should "still simulate" taggedAs (Slow) in {
    simulate(new TickGen()) { dut =>
      dut.clock.step(9)
      dut.io.tick.expect(true.B)
    }
  }

  it should "run alongside plain ScalaTest assertions" in {
    assert(17 + 25 == 42) // no simulator involved, so no Verilator build
  }
}
