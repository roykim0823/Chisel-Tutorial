import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionalAddTest extends AnyFlatSpec with ChiselScalatestTester {
  "FunctionalAdd" should "sum the vector" in {
    test(new FunctionalAdd) { dut =>
      Seq(3, 2, 0, 9, 1).zipWithIndex.foreach {
        // For each (value, index) pair,
        // poke the value into the corresponding input
        case (v, i) => dut.io.in(i).poke(v.U)
      }
      dut.io.res.expect(15.U)
    }
  }

  // The named function, the function literal and the `_` form all describe the
  // same sum, so all three outputs must agree.
  it should "give the same sum for all three spellings" in {
    test(new FunctionalAdd) { dut =>
      Seq(3, 2, 0, 9, 1).zipWithIndex.foreach { case (v, i) => dut.io.in(i).poke(v.U) }
      dut.io.res.expect(15.U)
      dut.io.sumNamed.expect(15.U)
      dut.io.sumLiteral.expect(15.U)
    }
  }
}
