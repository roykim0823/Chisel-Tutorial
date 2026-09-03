import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegistersTest extends AnyFlatSpec with ChiselScalatestTester {
  "Registers" should "delay its input by one cycle and count 0 to 9" in {
    test(new Registers) { dut =>
      // A register's output only changes on the clock edge: poke now, read
      // the value one step() later.
      dut.io.d.poke(7.U)
      dut.io.q.expect(0.U) // still the reset value
      dut.clock.step()
      dut.io.q.expect(7.U)
      dut.io.next.expect(7.U)     // RegNext(d)
      dut.io.nextInit.expect(7.U) // RegNext(d, 0.U)

      // cntReg started at 0 and has been counting since; check it wraps at 9
      // rather than reaching 10.
      for (_ <- 0 until 12) {
        val cnt = dut.io.cnt.peekInt()
        assert(cnt >= 0 && cnt <= 9, s"counter left the 0..9 range: $cnt")
        dut.clock.step()
      }
    }
  }
}
