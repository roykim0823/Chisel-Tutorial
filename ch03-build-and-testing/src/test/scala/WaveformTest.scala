import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Generate a waveform (.vcd) by attaching WriteVcdAnnotation. The file lands
// under test_run_dir/<test-name>/DeviceUnderTest.vcd and can be opened in
// GTKWave. Run with:  sbt "testOnly WaveformTest"
class WaveformTest extends AnyFlatSpec with ChiselScalatestTester {
  "Waveform" should "pass" in {
    test(new DeviceUnderTest)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.a.poke(0.U)
      dut.io.b.poke(0.U)
      dut.clock.step()
      dut.io.a.poke(1.U)
      dut.io.b.poke(0.U)
      dut.clock.step()
      dut.io.a.poke(0.U)
      dut.io.b.poke(1.U)
      dut.clock.step()
      dut.io.a.poke(1.U)
      dut.io.b.poke(1.U)
      dut.clock.step()
    }
  }
}

// Same idea, but drive all input combinations with Scala loops instead of
// writing each poke by hand.
class WaveformCounterTest extends AnyFlatSpec with ChiselScalatestTester {
  "WaveformCounter" should "pass" in {
    test(new DeviceUnderTest)
      .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      for (a <- 0 until 4) {
        for (b <- 0 until 4) {
          dut.io.a.poke(a.U)
          dut.io.b.poke(b.U)
          dut.clock.step()
        }
      }
    }
  }
}
