package leros

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// The data memory is a synchronous-read memory with a byte write mask.
class DataMemTest extends AnyFlatSpec with ChiselScalatestTester {

  // Write one word, with the given byte mask.
  private def write(dut: DataMem, addr: Int, data: Long, mask: String = "b1111"): Unit = {
    dut.io.wrAddr.poke(addr.U)
    dut.io.wrData.poke(data.U)
    dut.io.wrMask.poke(mask.U)
    dut.io.wr.poke(true.B)
    dut.clock.step(1)
    dut.io.wr.poke(false.B)
  }

  "DataMem" should "deliver a written word one cycle after the read address" in {
    test(new DataMem(8)) { dut =>
      write(dut, 0x10, 0x12345678L)
      dut.io.rdAddr.poke(0x10.U)
      dut.clock.step(1) // the read is synchronous: data arrives a cycle later
      dut.io.rdData.expect("h12345678".U)
    }
  }

  it should "write only the bytes selected by the mask" in {
    test(new DataMem(8)) { dut =>
      write(dut, 0x20, 0x00000000L)
      write(dut, 0x20, 0xffffffffL, "b0010") // byte 1 only
      dut.io.rdAddr.poke(0x20.U)
      dut.clock.step(1)
      dut.io.rdData.expect("h0000ff00".U)
    }
  }

  it should "not write at all when wr is low" in {
    test(new DataMem(8)) { dut =>
      write(dut, 0x30, 0x0000002aL)
      dut.io.wrAddr.poke(0x30.U)
      dut.io.wrData.poke(0xffffffffL.U)
      dut.io.wr.poke(false.B)
      dut.clock.step(1)
      dut.io.rdAddr.poke(0x30.U)
      dut.clock.step(1)
      dut.io.rdData.expect(0x2a.U)
    }
  }
}
