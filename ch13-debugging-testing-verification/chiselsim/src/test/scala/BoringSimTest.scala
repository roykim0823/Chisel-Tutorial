import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

// The chapter's BoringTest, rewritten for ChiselSim. Three lines change: the
// import, the trait mixed in, and `test(...)` becoming `simulate(...)`. The
// class rename is only to keep the two readable side by side. Every
// poke/step/expect inside the body is identical.
class BoringSimTest extends AnyFlatSpec with ChiselSim {
  "Boring" should "expose the internal counter" in {
    simulate(new TickGenTestTop()) { dut =>
      dut.io.tick.expect(false.B)
      dut.io.counter.expect(0.U)

      dut.clock.step()
      dut.io.tick.expect(false.B)
      dut.io.counter.expect(1.U)

      dut.clock.step(8)
      dut.io.tick.expect(true.B)
      dut.io.counter.expect(9.U)

      dut.clock.step()
      dut.io.tick.expect(false.B)
      dut.io.counter.expect(0.U)
    }
  }
}
