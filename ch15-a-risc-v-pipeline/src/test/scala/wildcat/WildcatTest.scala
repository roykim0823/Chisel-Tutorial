package wildcat.pipeline

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import wildcat.AluType._
import wildcat.InstrType
import wildcat.CSR._

// Exercise the Wildcat pieces one at a time - the ALU and decoder functions
// (via their wrappers), the CSR module, and the instruction ROM - and then the
// whole ThreeCats pipeline running a small hand-assembled RV32I program in
// WildcatTop.
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

  // The program below is hand-assembled RV32I. It ends with ecall, which the
  // pipeline reports on `stop`.
  val program = Array(
    0x00A00093, // addi x1, x0, 10
    0x01400113, // addi x2, x0, 20
    0x002081B3, // add  x3, x1, x2   (both operands forwarded)
    0x00302023, // sw   x3, 0(x0)
    0x00002203, // lw   x4, 0(x0)
    0x00000073, // ecall
    0x00000013, // nop
    0x00000013) // nop

  "ThreeCats" should "execute a small RV32I program" in {
    test(new WildcatTop(program)) { dut =>
      // Three cycles of fill (fetch, decode, execute) before the first result
      // reaches the register file.
      dut.clock.step(3)
      dut.io.regs(1).expect(10.U, "addi x1, x0, 10")

      // From here the pipeline is full: one instruction commits per cycle.
      dut.clock.step()
      dut.io.regs(2).expect(20.U, "addi x2, x0, 20")
      dut.clock.step()
      dut.io.regs(3).expect(30.U, "add x3, x1, x2 with forwarded operands")

      // sw x3 then lw x4 round-trips the value through the data memory.
      dut.clock.step(2)
      dut.io.regs(4).expect(30.U, "lw x4 reads back what sw x3 wrote")
    }
  }

  it should "raise stop on ecall" in {
    test(new WildcatTop(program)) { dut =>
      dut.io.stop.expect(false.B)
      // ecall is the sixth instruction, so it reaches execute in cycle 7.
      dut.clock.step(7)
      dut.io.stop.expect(true.B, "ecall reached the execute stage")
    }
  }
}
