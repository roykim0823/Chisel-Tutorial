import chisel3._
import chiseltest._
import ScalaFunctionalMin._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionalMinTester extends AnyFlatSpec with ChiselScalatestTester {

  "ScalaFunctionalMin (reference model)" should "find the min and index" in {
    assert(findMin(List(1, 0, 3, 2, 0, 5)) == (0, 1))
  }

  "FunctionalMin" should "find the min value and its index" in {
    test(new FunctionalMin(5, 8)) { d =>
      d.io.in(0).poke(3.U)
      d.io.in(1).poke(5.U)
      d.io.in(2).poke(1.U)
      d.io.in(3).poke(7.U)
      d.io.in(4).poke(3.U)
      d.clock.step()
      d.io.min.expect(1.U)
      d.io.resA.expect(1.U); d.io.idxA.expect(2.U)
      d.io.resB.expect(1.U); d.io.idxB.expect(2.U)
      d.io.resC.expect(1.U); d.io.idxC.expect(2.U)
    }
  }
}
