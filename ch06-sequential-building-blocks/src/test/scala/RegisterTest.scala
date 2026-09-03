import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegisterTest extends AnyFlatSpec with ChiselScalatestTester {
  "Registers" should "store values" in {
    test(new Registers) { dut =>
      dut.io.in.poke(13.U)
      dut.clock.step()
      dut.io.out.expect(13.U)
    }
  }

  it should "hold its value while the enable is low" in {
    test(new Registers) { dut =>
      dut.io.enable.poke(true.B)
      dut.io.inVal.poke(3.U)
      dut.clock.step()
      dut.io.enableOut.expect(3.U)
      dut.io.enableOut2.expect(3.U)
      dut.io.resetEnableOut.expect(3.U)
      dut.io.resetEnableOut2.expect(3.U)

      // With the enable low the new input is never captured: all four forms
      // keep the old value, whichever way they were written.
      dut.io.enable.poke(false.B)
      dut.io.inVal.poke(4.U)
      dut.clock.step()
      dut.io.enableOut.expect(3.U)
      dut.io.enableOut2.expect(3.U)
      dut.io.resetEnableOut.expect(3.U)
      dut.io.resetEnableOut2.expect(3.U)
    }
  }

  it should "detect a rising edge for exactly one cycle" in {
    test(new Registers) { dut =>
      dut.io.din.poke(false.B)
      dut.clock.step()
      dut.io.risingEdgeOut.expect(false.B)

      // din goes high: the edge is seen in this cycle only, because RegNext
      // still holds the old, low value.
      dut.io.din.poke(true.B)
      dut.io.risingEdgeOut.expect(true.B)
      dut.clock.step()
      dut.io.risingEdgeOut.expect(false.B) // still high, but no longer an edge
    }
  }
}
