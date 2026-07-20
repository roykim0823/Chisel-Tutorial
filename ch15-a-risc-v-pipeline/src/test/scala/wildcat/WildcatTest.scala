package wildcat.pipeline

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wildcat.AluType._
import wildcat.InstrType
import wildcat.CSR._

// Exercise the core Wildcat pieces that build without an external program:
// the ALU and decoder functions (via wrappers), the CSR module, and the
// instruction ROM.
class WildcatTest extends AnyFlatSpec with ChiselScalatestTester {

  "Alu" should "compute the RV32I operations" in {
    test(new AluModule) { dut =>
      def check(op: AluType, a: Long, b: Long, expected: Long) = {
        dut.io.op.poke(op.id.U)
        dut.io.a.poke(a.U)
        dut.io.b.poke(b.U)
        dut.io.res.expect(expected.U)
      }
      check(ADD, 12, 10, 22)
      check(SUB, 12, 10, 2)
      check(AND, 12, 10, 8)
      check(OR, 12, 10, 14)
      check(XOR, 12, 10, 6)
      check(SLL, 1, 4, 16)      // 1 << 4
      check(SRL, 256, 2, 64)    // 256 >> 2
      check(SLT, 3, 5, 1)       // 3 < 5
      check(SLTU, 5, 3, 0)      // 5 < 3 is false
    }
  }

  "Decode" should "decode an R-type add" in {
    test(new DecodeModule) { dut =>
      // add x3, x1, x2  = 0x002081B3
      dut.io.instr.poke("h002081B3".U)
      dut.io.instrType.expect(InstrType.R.id.U)
      dut.io.aluOp.expect(ADD.id.U)
      dut.io.rfWrite.expect(true.B)
      dut.io.isImm.expect(false.B)
      dut.io.isBranch.expect(false.B)
    }
  }

  "Decode" should "decode an I-type addi with its immediate" in {
    test(new DecodeModule) { dut =>
      // addi x1, x0, 10 = 0x00A00093
      dut.io.instr.poke("h00A00093".U)
      dut.io.instrType.expect(InstrType.I.id.U)
      dut.io.aluOp.expect(ADD.id.U)
      dut.io.rfWrite.expect(true.B)
      dut.io.isImm.expect(true.B)
      dut.io.imm.expect(10.S)
    }
  }

  "Csr" should "return the Wildcat architecture id" in {
    test(new Csr) { dut =>
      dut.io.address.poke(MARCHID.U)
      dut.io.data.expect(47.U)
      dut.io.address.poke(CYCLE.U)
      dut.io.data.expect(0.U)
    }
  }

  "InstructionROM" should "return the preloaded program (one-cycle latency)" in {
    val program = Array(0x00A00093, 0x01400113, 0x002081B3, 0x00000073)
    test(new InstructionROM(program)) { dut =>
      dut.io.address.poke(0.U)
      dut.clock.step()
      dut.io.data.expect("h00A00093".U)
      dut.io.address.poke(4.U)
      dut.clock.step()
      dut.io.data.expect("h01400113".U)
      dut.io.address.poke(8.U)
      dut.clock.step()
      dut.io.data.expect("h002081B3".U)
    }
  }
}
