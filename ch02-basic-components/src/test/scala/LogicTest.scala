import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class LogicTest extends AnyFlatSpec with ChiselScalatestTester {
  "Logic" should "pass" in {
    test(new Logic) { dut =>
      dut.io.a.poke(1.U)
      dut.io.b.poke(0.U)
      dut.io.c.poke(1.U)
      dut.clock.step()
      dut.io.out.expect(1.U)      // (a & b) | c = (1 & 0) | 1 = 1
      dut.io.cat.expect("hff01".U) // highByte ## lowByte = 0xff ## 0x01
      dut.io.ch.expect(65.U)       // 'A' in ASCII
      dut.io.word.expect("hff01".U)
      dut.io.result.expect(5.U)    // the 4 bits of 5.U reassembled
    }
  }
}
