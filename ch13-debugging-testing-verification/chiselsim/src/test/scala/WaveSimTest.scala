import chisel3._
import chisel3.simulator.scalatest.{ChiselSim, Cli}
import org.scalatest.flatspec.AnyFlatSpec

// §13.1's waveform debugging, on the ChiselSim side. Mixing in Cli.EmitVcd
// adds an `emitVcd` command-line option to this suite instead of hard-coding
// a WriteVcdAnnotation, so the same test runs with or without tracing:
//   sbt "testOnly WaveSimTest -- -DemitVcd=1"
class WaveSimTest extends AnyFlatSpec with ChiselSim with Cli.EmitVcd {
  "TickGen" should "tick on the tenth cycle" in {
    simulate(new TickGen()) { dut =>
      dut.clock.step(9)
      // Real ChiselSim keeps expect's message argument (the Chisel 7
      // chiseltest-compatibility shim does not).
      dut.io.tick.expect(true.B, "tick must be high on the tenth cycle")
    }
  }
}
