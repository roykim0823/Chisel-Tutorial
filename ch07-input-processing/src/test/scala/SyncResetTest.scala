import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class SyncResetTest extends AnyFlatSpec with ChiselScalatestTester {
  "SyncReset" should "run with a synchronized reset" in {
    test(new SyncReset) { dut =>
      dut.clock.step(20)
    }
  }
}
