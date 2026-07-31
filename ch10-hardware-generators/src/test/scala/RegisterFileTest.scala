import chisel3._
import chiseltest._
import circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec

// The optional debug port, from both sides: the tester unwraps it with .get,
// and the generated Verilog shows whether it cost anything.
class RegisterFileTest extends AnyFlatSpec with ChiselScalatestTester {

  // Write `value` into register `rd`, taking one clock cycle.
  def write(dut: RegisterFile, rd: Int, value: Int): Unit = {
    dut.io.rd.poke(rd.U)
    dut.io.wrData.poke(value.U)
    dut.io.wrEna.poke(true.B)
    dut.clock.step()
    dut.io.wrEna.poke(false.B)
  }

  "RegisterFile with debug" should "expose every register on the debug port" in {
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

  it should "read two registers at once" in {
    test(new RegisterFile(true)) { dut =>
      write(dut, 4, 123)
      write(dut, 2, 456)
      dut.io.rs1.poke(4.U)
      dut.io.rs2.poke(2.U)
      dut.io.rs1Val.expect(123.U)
      dut.io.rs2Val.expect(456.U)
      // Every register is visible at once through the debug port.
      dut.io.dbgPort.get(4).expect(123.U)
      dut.io.dbgPort.get(2).expect(456.U)
      dut.io.dbgPort.get(7).expect(0.U)
    }
  }

  "RegisterFile without debug" should "still read and write normally" in {
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

  // The book leaves this as a TODO: reaching for a port that was never built is
  // a plain Scala error, raised at TEST-ELABORATION time, not a hardware fault.
  it should "raise an exception when the missing port is unwrapped" in {
    test(new RegisterFile(false)) { dut =>
      assert(dut.io.dbgPort.isEmpty)
      intercept[NoSuchElementException] {
        dut.io.dbgPort.get(4).expect(123.U)
      }
    }
  }

  // A `None` port costs nothing: it leaves no trace in the generated Verilog.
  it should "generate no debug ports at all" in {
    def dbgPorts(debug: Boolean): Int =
      ChiselStage
        .emitSystemVerilog(new RegisterFile(debug), firtoolOpts = Array("-strip-debug-info"))
        .linesIterator
        .count(l => l.contains("dbgPort") && l.contains("output"))

    assert(dbgPorts(true) == 32, s"expected 32 debug outputs, got ${dbgPorts(true)}")
    assert(dbgPorts(false) == 0, s"expected no debug outputs, got ${dbgPorts(false)}")
  }
}
