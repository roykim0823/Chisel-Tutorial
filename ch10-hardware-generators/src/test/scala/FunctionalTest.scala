import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionalTest extends AnyFlatSpec with ChiselScalatestTester {
  "FunctionalAdd" should "sum the vector" in {
    test(new FunctionalAdd) { dut =>
      Seq(3, 2, 0, 9, 1).zipWithIndex.foreach { case (v, i) =>
        dut.io.in(i).poke(v.U)
      }
      dut.clock.step()
      dut.io.res.expect(15.U)
    }
  }

  "FunctionalComp" should "compare via a tuple-returning function" in {
    test(new FunctionalComp) { dut =>
      dut.io.a.poke(5.U); dut.io.b.poke(3.U)
      dut.clock.step()
      dut.io.equ.expect(false.B); dut.io.gt.expect(true.B)
      dut.io.a.poke(7.U); dut.io.b.poke(7.U)
      dut.clock.step()
      dut.io.equ.expect(true.B); dut.io.gt.expect(false.B)
    }
  }
}
