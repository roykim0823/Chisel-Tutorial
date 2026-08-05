import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// The four single-variant modules must agree with each other (and with the
// combined FunctionalMin) - otherwise comparing their Verilog is meaningless.
class MinVariantsTest extends AnyFlatSpec with ChiselScalatestTester {

  val data = Seq(3, 5, 1, 7)   // unique minimum 1 at index 2
  val tie = Seq(1, 0, 3, 0)    // minimum 0 at index 1 AND index 3

  "MinValueOnly" should "find the minimum value" in {
    test(new MinValueOnly(data.length, 8)) { d =>
      data.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.min.expect(1.U)
    }
  }

  "MinBundle" should "find the minimum and its index" in {
    test(new MinBundle(data.length, 8)) { d =>
      data.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.min.expect(1.U)
      d.io.idx.expect(2.U)
    }
  }

  "MinTuple" should "find the minimum and its index" in {
    test(new MinTuple(data.length, 8)) { d =>
      data.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.min.expect(1.U)
      d.io.idx.expect(2.U)
    }
  }

  "MinMixedVec" should "find the minimum and its index" in {
    test(new MinMixedVec(data.length, 8)) { d =>
      data.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.min.expect(1.U)
      d.io.idx.expect(2.U)
    }
  }

  // The chain (c) and the trees (b)/(d) associate the comparisons differently,
  // yet a strict `<` makes the LAST minimum win in either shape.
  "all three index variants" should "report the last index on a tie" in {
    test(new MinBundle(tie.length, 8)) { d =>
      tie.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.idx.expect(3.U)
    }
    test(new MinTuple(tie.length, 8)) { d =>
      tie.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.idx.expect(3.U)
    }
    test(new MinMixedVec(tie.length, 8)) { d =>
      tie.zipWithIndex.foreach { case (v, i) => d.io.in(i).poke(v.U) }
      d.io.idx.expect(3.U)
    }
  }
}
