import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class AssertTest extends AnyFlatSpec with ChiselScalatestTester {
  "Assert" should "hold (even across an overflowing add)" in {
    test(new Assert()) { dut =>
      dut.io.a.poke(1.U)
      dut.io.b.poke(2.U)
      dut.clock.step()
      dut.io.a.poke(100.U)
      dut.io.b.poke(200.U)   // 300 wraps to 44 in 8 bits; the assert still holds
      dut.clock.step()
    }
  }
}
