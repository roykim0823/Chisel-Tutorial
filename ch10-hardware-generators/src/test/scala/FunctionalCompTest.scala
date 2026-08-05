import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionalCompTest extends AnyFlatSpec with ChiselScalatestTester {
  "FunctionalComp" should "compare via a tuple-returning function" in {
    test(new FunctionalComp) { dut =>
      dut.io.a.poke(5.U); dut.io.b.poke(3.U)
      dut.io.equ.expect(false.B); dut.io.gt.expect(true.B)

      dut.io.a.poke(7.U); dut.io.b.poke(7.U)
      dut.io.equ.expect(true.B); dut.io.gt.expect(false.B)
    }
  }
}
