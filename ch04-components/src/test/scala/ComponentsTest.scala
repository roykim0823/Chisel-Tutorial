import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ComponentsTest extends AnyFlatSpec with ChiselScalatestTester {

  "Count10" should "count 0..9 and wrap" in {
    test(new Count10) { dut =>
      for (i <- 0 until 10) {
        dut.io.dout.expect(i.U)
        dut.clock.step()
      }
      dut.io.dout.expect(0.U) // wrapped back to 0
    }
  }

  "Alu" should "add, subtract, or, and" in {
    test(new Alu) { dut =>
      dut.io.a.poke(12.U)
      dut.io.b.poke(10.U)
      dut.io.fn.poke(0.U); dut.io.y.expect(22.U) // 12 + 10
      dut.io.fn.poke(1.U); dut.io.y.expect(2.U)  // 12 - 10
      dut.io.fn.poke(2.U); dut.io.y.expect((12 | 10).U)
      dut.io.fn.poke(3.U); dut.io.y.expect((12 & 10).U)
    }
  }
}
