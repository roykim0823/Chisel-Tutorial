import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import ScalaFunctionalMin._

class FunctionalMinTest extends AnyFlatSpec with ChiselScalatestTester {

  "ScalaFunctionalMin (reference model)" should "find the min and index" in {
    assert(findMin(List(1, 0, 3, 2, 0, 5)) == (0, 1))
  }

  // On a tie the hardware and the Scala model disagree BY CONSTRUCTION: the
  // reductions compare with a strict `<` (so the later element wins and the LAST
  // minimum's index survives), while findMin uses `<=` and keeps the FIRST.
  // Pinned here so the difference cannot drift unnoticed.
  "a tie" should "give the last index in hardware and the first in the model" in {
    val data = Seq(1, 0, 3, 2, 0, 5)   // minimum 0 at index 1 and index 4
    assert(findMin(data) == (0, 1))
    test(new FunctionalMin(data.length, 8)) { d =>
      data.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.clock.step()
      d.io.min.expect(0.U)
      d.io.idxA.expect(4.U)
      d.io.idxB.expect(4.U)
      d.io.idxC.expect(4.U)
    }
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
