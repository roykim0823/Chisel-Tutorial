import chisel3._
import chiseltest._
import chiseltest.formal._
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec

// Formal verification needs an SMT solver on the PATH, which not every reader
// will have, so these tests carry a tag that build.sbt excludes from the
// default `sbt test`. Run them with:
//   sbt "testOnly * -- -n NeedsSolver"
object NeedsSolver extends Tag("NeedsSolver")

class FormalTest extends AnyFlatSpec with ChiselScalatestTester with Formal {

  // The chapter's adder carries only a tautology, so it is provable.
  "Assert" should "pass a bounded check" taggedAs (NeedsSolver) in {
    verify(new Assert(), Seq(BoundedCheck(5)))
  }

  // AssertOverflow's assertion is the one the book's author found to be wrong.
  // A passing test here means the solver refuted it, as it should.
  "AssertOverflow" should "be refuted, because an 8-bit add can wrap" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new AssertOverflow(), Seq(BoundedCheck(5)))
    }
  }

  // The needle-in-a-haystack case: one failing input out of 2^32.
  // WriteVcdAnnotation dumps the counterexample trace that names it.
  "Saturate" should "be refuted, with a counterexample trace" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new Saturate(), Seq(BoundedCheck(1), WriteVcdAnnotation))
    }
  }

  "SaturateFixed" should "pass, for all 2^32 inputs" taggedAs (NeedsSolver) in {
    verify(new SaturateFixed(), Seq(BoundedCheck(1)))
  }

  // `assume` constrains the inputs the solver may choose. The same assertion
  // that AssertOverflow is refuted on becomes provable under it.
  "AssumeNoOverflow" should "pass, because assume rules out the counterexample" taggedAs (NeedsSolver) in {
    verify(new AssumeNoOverflow(), Seq(BoundedCheck(5)))
  }

  // A bound is a bound. The counter only misbehaves when it wraps at 255, so a
  // shallow check passes and only a deep one finds the bug.
  "MonotonicCounter" should "survive a shallow bounded check" taggedAs (NeedsSolver) in {
    verify(new MonotonicCounter(), Seq(BoundedCheck(10)))
  }

  it should "be refuted once the bound reaches the wrap" taggedAs (NeedsSolver) in {
    intercept[FailedBoundedCheckException] {
      verify(new MonotonicCounter(), Seq(BoundedCheck(300)))
    }
  }
}
