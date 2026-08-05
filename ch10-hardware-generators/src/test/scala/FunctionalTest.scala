import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionalTest extends AnyFlatSpec with ChiselScalatestTester {
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

  "FunctionalComp" should "compare via a tuple-returning function" in {
    test(new FunctionalComp) { dut =>
      dut.io.a.poke(5.U); dut.io.b.poke(3.U)
      dut.io.equ.expect(false.B); dut.io.gt.expect(true.B)
      
      dut.io.a.poke(7.U); dut.io.b.poke(7.U)
      dut.io.equ.expect(true.B); dut.io.gt.expect(false.B)
    }
  }
}

import ScalaFunctionalMin._

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
