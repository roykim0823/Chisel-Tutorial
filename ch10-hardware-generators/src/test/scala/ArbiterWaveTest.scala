import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Record REAL waveforms for the two 2:1 arbitration functions of Section 10.6.2.
// Every test below attaches WriteVcdAnnotation, so each one drops a .vcd under
// test_run_dir/<test name>/ that can be opened in GTKWave (or Surfer).
//
//   sbt "testOnly ArbiterWaveTest"
//
// The scenarios are deliberately paired: the SAME stimulus is applied to the
// priority arbiter (ArbiterSimpleTree) and to the fair one (ArbiterTree), so the
// two waveforms can be put side by side. This is how the timing diagrams in the
// chapter README were produced.
class ArbiterWaveTest extends AnyFlatSpec with ChiselScalatestTester {

  private val W = UInt(8.W)

  /** Drive a scenario and return the values the consumer accepted, in order.
    *
    * Input `i` sends the constant value `i + 1`, and only the inputs listed in
    * `requesting` hold `valid`. The consumer is correct by construction: it
    * asserts `ready` only in a cycle in which it sees `valid` — never parks it
    * high, which the simple arbiter mishandles (see ArbiterTreeTest). With
    * `acceptEvery = k` it takes only every k-th offered word, which models a
    * slow consumer and makes back-pressure visible in the waveform.
    */
  def run[T <: Arbiter[_ <: UInt]](
      dut: T,
      n: Int,
      cycles: Int,
      requesting: Set[Int],
      acceptEvery: Int = 1
  ): Seq[Int] = {
    for (i <- 0 until n) {
      dut.io.in(i).valid.poke(requesting.contains(i).B)
      dut.io.in(i).bits.poke((i + 1).U)
    }
    dut.io.out.ready.poke(false.B)

    val seen = scala.collection.mutable.ListBuffer[Int]()
    var offered = 0
    for (_ <- 0 until cycles) {
      val valid = dut.io.out.valid.peek().litToBoolean
      var take = false
      if (valid) {
        offered += 1
        take = offered % acceptEvery == 0
      }
      dut.io.out.ready.poke(take.B)
      if (take) seen += dut.io.out.bits.peek().litValue.toInt
      dut.clock.step()
    }
    seen.toList
  }

  // --- case 1: both inputs request forever -------------------------------
  // The headline difference. a sends 1, b sends 2, both hold valid.

  "priority 2to1, both requesting" should "record a waveform showing b starved" in {
    test(new ArbiterSimpleTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 24, Set(0, 1))
      println(s"[wave] priority, both requesting  -> $seen")
      assert(seen.nonEmpty, "nothing was served at all")
      assert(!seen.contains(2), s"input b should starve, but got $seen")
    }
  }

  "fair 2to1, both requesting" should "record a waveform showing alternation" in {
    test(new ArbiterTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 24, Set(0, 1))
      println(s"[wave] fair, both requesting      -> $seen")
      assert(seen.toSet == Set(1, 2), s"both inputs should be served, got $seen")
    }
  }

  // --- case 2: only the low-priority input requests -----------------------
  // Priority is not the same as starvation: with a idle, b is served fine.

  "priority 2to1, only b requesting" should "record a waveform showing b served" in {
    test(new ArbiterSimpleTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 24, Set(1))
      println(s"[wave] priority, only b requesting-> $seen")
      assert(seen.nonEmpty && seen.forall(_ == 2), s"only b's value (2) expected, got $seen")
    }
  }

  "fair 2to1, only b requesting" should "record a waveform showing b served" in {
    test(new ArbiterTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 24, Set(1))
      println(s"[wave] fair, only b requesting    -> $seen")
      assert(seen.nonEmpty && seen.forall(_ == 2), s"only b's value (2) expected, got $seen")
    }
  }

  // --- case 3: a slow consumer -------------------------------------------
  // Back-pressure: the arbiter holds the word until it is taken, so `out.valid`
  // stays high across several cycles instead of pulsing once.

  "priority 2to1, slow consumer" should "record a waveform with back-pressure" in {
    test(new ArbiterSimpleTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 40, Set(0, 1), acceptEvery = 4)
      println(s"[wave] priority, slow consumer    -> $seen")
      assert(seen.nonEmpty, "nothing was served at all")
    }
  }

  "fair 2to1, slow consumer" should "record a waveform with back-pressure" in {
    test(new ArbiterTree(2, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 2, 40, Set(0, 1), acceptEvery = 4)
      println(s"[wave] fair, slow consumer        -> $seen")
      assert(seen.nonEmpty, "nothing was served at all")
    }
  }

  // --- case 4: the whole 4-input tree -------------------------------------
  // Two levels of 2:1 nodes. The priority tree serves only the `a` side of each
  // node (inputs 0 and 2); the fair tree cycles through all four.

  "priority 4input tree" should "record a waveform of the whole tree" in {
    test(new ArbiterSimpleTree(4, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 4, 40, Set(0, 1, 2, 3))
      println(s"[wave] priority, 4-input tree     -> $seen")
      assert(seen.nonEmpty, "nothing was served at all")
      assert(seen.toSet.subsetOf(Set(1, 3)), s"only inputs 0 and 2 should win, got $seen")
    }
  }

  "fair 4input tree" should "record a waveform of the whole tree" in {
    test(new ArbiterTree(4, W)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      val seen = run(dut, 4, 40, Set(0, 1, 2, 3))
      println(s"[wave] fair, 4-input tree         -> $seen")
      assert(seen.toSet == Set(1, 2, 3, 4), s"every input should be served, got $seen")
    }
  }
}
