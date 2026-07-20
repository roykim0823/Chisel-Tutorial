import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class PopulationCountTest extends AnyFlatSpec with ChiselScalatestTester {
  "PopulationCount" should "count the set bits" in {
    test(new PopulationCount) { dut =>
      dut.io.din.poke(0xac.U)          // 0xac = 1010_1100 -> four 1s
      dut.io.dinValid.poke(true.B)
      dut.clock.step(12)
      dut.io.popCnt.expect(4.U)
    }
  }
}
