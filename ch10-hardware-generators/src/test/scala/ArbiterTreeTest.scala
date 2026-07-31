import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Tests for the generated arbitration tree. The tester is written against the
// BASE class (Arbiter), so the same code drives both the fair and the priority
// tree - the same trick as TickerTest.
class ArbiterTreeTest extends AnyFlatSpec with ChiselScalatestTester {

  // One requester sends a value; it must reach the output and stay there while
  // the consumer keeps `ready` low.
  def testBasic[T <: Arbiter[_ <: UInt]](dut: T, n: Int): Unit = {
    for (i <- 0 until n) {
      dut.io.in(i).valid.poke(false.B)
    }
    dut.io.out.ready.poke(false.B) // keep the output till we read it
    dut.io.in(2).valid.poke(true.B)
    dut.io.in(2).bits.poke(2.U)

    // Wait for this input's registered `ready`, with a bound so a broken
    // arbiter fails the test instead of hanging forever.
    var waited = 0
    while (!dut.io.in(2).ready.peek().litToBoolean && waited < 20) {
      dut.clock.step()
      waited += 1
    }
    assert(waited < 20, "input 2 was never acknowledged")

    dut.clock.step()
    dut.io.in(2).valid.poke(false.B)
    dut.clock.step(10)
    dut.io.out.bits.expect(2.U)
  }

  // Keep every input valid with a distinct value (input i sends i+1) and record
  // which values reach the output. `gatedReady` models a proper consumer that
  // asserts ready only when it sees valid; false holds ready high all the time.
  def serve[T <: Arbiter[_ <: UInt]](dut: T, n: Int, cycles: Int, gatedReady: Boolean): Seq[Int] = {
    for (i <- 0 until n) {
      dut.io.in(i).valid.poke(true.B)
      dut.io.in(i).bits.poke((i + 1).U)
    }
    if (!gatedReady) dut.io.out.ready.poke(true.B)

    val seen = scala.collection.mutable.ListBuffer[Int]()
    for (_ <- 0 until cycles) {
      val valid = dut.io.out.valid.peek().litToBoolean
      if (gatedReady) dut.io.out.ready.poke(valid.B)
      if (valid) seen += dut.io.out.bits.peek().litValue.toInt
      dut.clock.step()
    }
    seen.toList
  }

  "ArbiterTree" should "pass a value through the tree" in {
    test(new ArbiterTree(4, UInt(8.W))) { dut => testBasic(dut, 4) }
  }

  it should "be fair: every input gets served" in {
    test(new ArbiterTree(4, UInt(8.W))) { dut =>
      val seen = serve(dut, 4, 40, gatedReady = false)
      println(s"fair served: $seen")
      // All four inputs appear, and no input is served far less than the others.
      assert(seen.toSet == Set(1, 2, 3, 4), s"some input starved: served $seen")
      for (v <- 1 to 4)
        assert(seen.count(_ == v) >= 4, s"input $v served only ${seen.count(_ == v)} times")
    }
  }

  "ArbiterSimpleTree" should "pass a value through the tree" in {
    test(new ArbiterSimpleTree(4, UInt(8.W))) { dut => testBasic(dut, 4) }
  }

  // The point of the section: the simple arbiter is a PRIORITY arbiter. With
  // every input requesting forever, the `b` side of each 2:1 node never wins,
  // so inputs 1 and 3 (values 2 and 4) starve.
  it should "starve the low-priority inputs" in {
    test(new ArbiterSimpleTree(4, UInt(8.W))) { dut =>
      val seen = serve(dut, 4, 40, gatedReady = true)
      println(s"priority served: $seen")
      assert(seen.nonEmpty, "nothing was served at all")
      assert(!seen.contains(2), "input 1 should have starved")
      assert(!seen.contains(4), "input 3 should have starved")
      assert(seen.toSet == Set(1, 3), s"expected only inputs 0 and 2 to win: served $seen")
    }
  }

  // A second flaw, and a good lesson in reading a ready/valid handshake: the
  // simple arbiter empties its data register whenever `ready` is high, without
  // checking `valid`. A consumer that parks ready high therefore gets nothing.
  it should "stall while ready is held high" in {
    test(new ArbiterSimpleTree(4, UInt(8.W))) { dut =>
      val seen = serve(dut, 4, 40, gatedReady = false)
      assert(seen.isEmpty, s"expected no output, but got $seen")
    }
  }
}
