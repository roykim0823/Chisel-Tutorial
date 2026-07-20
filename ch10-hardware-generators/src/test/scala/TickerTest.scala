import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// One generic test for any Ticker subclass: wait for the first tick, then check
// it repeats every n cycles.
trait TickerTestFunc {
  def testFn[T <: Ticker](dut: T, n: Int) = {
    var count = -1   // -1 means no tick seen yet
    for (_ <- 0 to n * 3) {
      if (count > 0)
        dut.io.tick.expect(false.B)
      else if (count == 0)
        dut.io.tick.expect(true.B)

      if (dut.io.tick.peekBoolean())
        count = n - 1
      else
        count -= 1
      dut.clock.step()
    }
  }
}

class TickerTest extends AnyFlatSpec with ChiselScalatestTester with TickerTestFunc {
  "UpTicker 5" should "tick every 5 cycles" in { test(new UpTicker(5)) { dut => testFn(dut, 5) } }
  "DownTicker 7" should "tick every 7 cycles" in { test(new DownTicker(7)) { dut => testFn(dut, 7) } }
  "NerdTicker 11" should "tick every 11 cycles" in { test(new NerdTicker(11)) { dut => testFn(dut, 11) } }
}
