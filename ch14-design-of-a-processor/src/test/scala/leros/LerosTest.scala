package leros

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// Run whole programs on the processor. The Leros test-suite convention is that
// a passing program leaves 0 in the accumulator and ends with a system call,
// so one assertion per program covers every instruction it uses.
class LerosTest extends AnyFlatSpec with ChiselScalatestTester {

  private def run(prog: String, maxCycles: Int = 1000): Unit = {
    test(new Leros(prog)) { dut =>
      var cycles = 0
      while (!dut.io.exit.peekBoolean() && cycles < maxCycles) {
        dut.clock.step(1)
        cycles += 1
      }
      assert(cycles < maxCycles, s"$prog did not reach scall in $maxCycles cycles")
      dut.io.accu.expect(0.U, s"$prog shall leave 0 in the accumulator")
    }
  }

  "Leros" should "run the example program of Section 14.1" in { run("asm/test.s") }
  it should "compute with register operands" in { run("asm/registers.s") }
  it should "build a word with the load-high instructions" in { run("asm/loadhi.s") }
  it should "load and store words and bytes indirectly" in { run("asm/memory.s") }
  it should "take all five kinds of branch" in { run("asm/branch.s") }
}
