import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import Arbitration._        // the book's arbiters      (ArbiterTree.scala)
import ArbiterVariants._    // the variants under study (ArbiterVariants.scala)

// Does the ORDER of the two `when` blocks in an arbitration function matter?
//
//   sbt "testOnly ArbiterOrderTest"
//
// Each test builds the arbiter twice from the SAME statements - only their order
// differs - and drives both with identical stimulus. The functions themselves
// live in ArbiterVariants.scala; this file only measures them. Appendix A of the
// chapter README walks through the results.
class ArbiterOrderTest extends AnyFlatSpec with ChiselScalatestTester {

  // Both inputs request forever; the consumer asserts ready only when it sees
  // valid. Returns (ready trace of input a, values the consumer accepted).
  def drive(dut: Arbiter[UInt], cycles: Int): (Seq[Int], Seq[Int]) = {
    dut.io.in(0).valid.poke(true.B); dut.io.in(0).bits.poke(1.U)
    dut.io.in(1).valid.poke(true.B); dut.io.in(1).bits.poke(2.U)
    dut.io.out.ready.poke(false.B)

    val ready = scala.collection.mutable.ListBuffer[Int]()
    val taken = scala.collection.mutable.ListBuffer[Int]()
    for (_ <- 0 until cycles) {
      ready += (if (dut.io.in(0).ready.peek().litToBoolean) 1 else 0)
      val v = dut.io.out.valid.peek().litToBoolean
      dut.io.out.ready.poke(v.B)
      if (v) taken += dut.io.out.bits.peek().litValue.toInt
      dut.clock.step()
    }
    (ready.toList, taken.toList)
  }

  // Deterministic stimulus depending only on the cycle index, so every device
  // under test sees exactly the same inputs. Unlike `drive` it also idles the
  // inputs and stalls the consumer, which exercises the guards.
  def stim(c: Int) = (((c * 7) % 5) != 0, ((c * 11) % 4) != 0, ((c * 5) % 3) != 0)

  def record(dut: Arbiter[UInt], n: Int): Seq[String] = {
    val trace = scala.collection.mutable.ListBuffer[String]()
    for (c <- 0 until n) {
      val (av, bv, orr) = stim(c)
      dut.io.in(0).valid.poke(av.B); dut.io.in(0).bits.poke(1.U)
      dut.io.in(1).valid.poke(bv.B); dut.io.in(1).bits.poke(2.U)
      dut.io.out.ready.poke(orr.B)
      trace += s"${if (dut.io.in(0).ready.peek().litToBoolean) 1 else 0}" +
               s"${if (dut.io.in(1).ready.peek().litToBoolean) 1 else 0}" +
               s"${if (dut.io.out.valid.peek().litToBoolean) 1 else 0}" +
               s"${dut.io.out.bits.peek().litValue}"
      dut.clock.step()
    }
    trace.toList
  }

  // Report the cycles at which two traces disagree.
  def mismatches(x: Seq[String], y: Seq[String]): Seq[Int] =
    x.zip(y).zipWithIndex.collect { case ((p, q), i) if p != q => i }

  val cycles = 12
  val probe = 200

  "the two statement orders" should "produce different hardware" in {
    var asWritten: Seq[Int] = Nil
    var swapped: Seq[Int] = Nil
    var tookA = 0
    var tookS = 0

    // decide, then capture — the order in ArbiterTree.scala
    test(new Arbiter(2, UInt(8.W), arbitrateSimp[UInt])) { d =>
      val (r, t) = drive(d, cycles); asWritten = r; tookA = t.length
    }
    // capture, then decide — the same statements, reordered
    test(new Arbiter(2, UInt(8.W), arbitrateSimpSwapped[UInt])) { d =>
      val (r, t) = drive(d, cycles); swapped = r; tookS = t.length
    }

    println("cycle              : " + (0 until cycles).map(c => f"$c%2d").mkString(" "))
    println("in(0).ready  decide-then-capture: " + asWritten.map(v => f"$v%2d").mkString(" "))
    println("in(0).ready  capture-then-decide: " + swapped.map(v => f"$v%2d").mkString(" "))
    println(s"grant cycles: as written = ${asWritten.sum}, swapped = ${swapped.sum}" +
            s"   (words delivered: $tookA vs $tookS)")

    // As written, the grant is one cycle wide; swapped, it is two - so `a` is
    // acknowledged more often than words are actually forwarded.
    assert(asWritten != swapped, "statement order made no difference - unexpected")
    assert(asWritten.sum == tookA, "as written: one grant per word delivered")
    assert(swapped.sum > tookS, "swapped: more grants than words delivered")
  }

  "the order-free rewrite" should "behave exactly like arbitrateSimp" in {
    var book: Seq[String] = Nil
    var free: Seq[String] = Nil
    test(new Arbiter(2, UInt(8.W), arbitrateSimp[UInt]))     { d => book = record(d, probe) }
    test(new Arbiter(2, UInt(8.W), arbitrateSimpFree[UInt])) { d => free = record(d, probe) }
    val diffs = mismatches(book, free)
    println(s"order-free vs arbitrateSimp: $probe cycles, ${diffs.length} mismatching")
    assert(diffs.isEmpty, s"behaviour differs at cycles ${diffs.take(5)}")
  }

  it should "be immune to the swap that breaks arbitrateSimp" in {
    var asIs: Seq[String] = Nil
    var swapped: Seq[String] = Nil
    test(new Arbiter(2, UInt(8.W), arbitrateSimpFree[UInt]))        { d => asIs = record(d, probe) }
    test(new Arbiter(2, UInt(8.W), arbitrateSimpFreeSwapped[UInt])) { d => swapped = record(d, probe) }
    val diffs = mismatches(asIs, swapped)
    println(s"order-free, decide-first vs capture-first: $probe cycles, ${diffs.length} mismatching")
    assert(diffs.isEmpty, s"statement order still matters at cycles ${diffs.take(5)}")
  }
}
