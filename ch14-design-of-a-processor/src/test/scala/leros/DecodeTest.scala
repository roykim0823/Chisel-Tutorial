package leros

import chisel3._
import chiseltest._
import leros.shared.Constants._
import org.scalatest.flatspec.AnyFlatSpec

// Decode is purely combinational: poke an instruction word, read the control
// signals back in the same cycle.
class DecodeTest extends AnyFlatSpec with ChiselScalatestTester {

  "Decode" should "route register and immediate operands differently" in {
    test(new Decode()) { dut =>
      // add r3: the operand comes from the register file.
      dut.io.din.poke(((ADD << 8) + 3).U)
      dut.io.dout.op.expect(add.U)
      dut.io.dout.isRegOpd.expect(true.B)
      dut.io.dout.useDecOpd.expect(false.B)
      dut.io.dout.enaMask.expect("b1111".U)

      // addi 2: the operand comes from decode itself.
      dut.io.din.poke(((ADDI << 8) + 2).U)
      dut.io.dout.op.expect(add.U)
      dut.io.dout.isRegOpd.expect(false.B)
      dut.io.dout.useDecOpd.expect(true.B)
      dut.io.dout.operand.expect(2.U)
    }
  }

  it should "sign-extend arithmetic immediates but not logic ones" in {
    test(new Decode()) { dut =>
      dut.io.din.poke(((ADDI << 8) + 0xff).U)
      dut.io.dout.operand.expect("hffffffff".U) // addi 0xff subtracts one
      dut.io.din.poke(((ANDI << 8) + 0xff).U)
      dut.io.dout.operand.expect("h000000ff".U) // andi masks the low byte
      dut.io.din.poke(((ORI << 8) + 0xff).U)
      dut.io.dout.operand.expect("h000000ff".U)
    }
  }

  it should "give each load-high instruction its own byte mask" in {
    test(new Decode()) { dut =>
      dut.io.din.poke(((LDHI << 8) + 0x12).U)
      dut.io.dout.enaMask.expect("b1110".U)
      dut.io.dout.operand.expect("h00001200".U)
      dut.io.din.poke(((LDH2I << 8) + 0x12).U)
      dut.io.dout.enaMask.expect("b1100".U)
      dut.io.dout.operand.expect("h00120000".U)
      dut.io.din.poke(((LDH3I << 8) + 0x12).U)
      dut.io.dout.enaMask.expect("b1000".U)
      dut.io.dout.operand.expect("h12000000".U)
    }
  }

  it should "scale the indirect offset by the access size" in {
    test(new Decode()) { dut =>
      dut.io.din.poke(((LDIND << 8) + 3).U)
      dut.io.dout.isDataAccess.expect(true.B)
      dut.io.dout.isLoadInd.expect(true.B)
      dut.io.dout.off.expect(12.S) // word access: three words is twelve bytes
      dut.io.dout.isByteOff.expect(false.B)

      dut.io.din.poke(((LDINDB << 8) + 3).U)
      dut.io.dout.isLoadIndB.expect(true.B)
      dut.io.dout.isByteOff.expect(true.B)
      dut.io.dout.off.expect(3.S) // byte access: the offset is used as it is
    }
  }

  it should "leave branches to the top level and flag scall" in {
    test(new Decode()) { dut =>
      // A branch's low bits are masked off, so no case of the switch matches
      // and every control signal keeps its default: the branch itself is
      // decoded in Leros, from the upper four bits.
      dut.io.din.poke(0xa005.U) // brnz -5
      dut.io.dout.op.expect(nop.U)
      dut.io.dout.enaMask.expect("b0000".U)
      dut.io.dout.isStore.expect(false.B)
      dut.io.dout.exit.expect(false.B)

      dut.io.din.poke(((SCALL << 8) + 0).U)
      dut.io.dout.exit.expect(true.B)
    }
  }
}
