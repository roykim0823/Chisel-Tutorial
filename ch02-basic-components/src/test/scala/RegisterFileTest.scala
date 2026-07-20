import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RegisterFileTest extends AnyFlatSpec with ChiselScalatestTester {
  "RegisterFile" should "have a debug port" in {
    test(new RegisterFile(true)) { dut =>
      dut.io.rs1.poke(4.U)
      dut.io.rs2.poke(2.U)
      dut.io.rd.poke(4.U)
      dut.io.wrData.poke(123.U)
      dut.io.wrEna.poke(true.B)
      dut.clock.step()
      dut.io.rs1Val.expect(123.U)
      dut.io.dbgPort.get(4).expect(123.U)
    }
  }

  "RegisterFile" should "work without the debug port" in {
    test(new RegisterFile(false)) { dut =>
      dut.io.rs1.poke(4.U)
      dut.io.rs2.poke(2.U)
      dut.io.rd.poke(4.U)
      dut.io.wrData.poke(123.U)
      dut.io.wrEna.poke(true.B)
      dut.clock.step()
      dut.io.rs1Val.expect(123.U)
    }
  }
}
