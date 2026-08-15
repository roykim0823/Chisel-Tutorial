import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import axi.AxiResp
import axilite._

// AXI4-Lite has no ordering between the write-address and write-data channels,
// so the same write is driven twice here -- address first, then data first --
// and both have to work.
class AxiLiteCounterTest extends AnyFlatSpec with ChiselScalatestTester {

  private def sendAddr(ch: DecoupledIO[AxiLiteAddr], clock: Clock, addr: Int): Unit = {
    ch.bits.addr.poke(addr.U)
    ch.bits.prot.poke(0.U)
    ch.valid.poke(true.B)
    while (!ch.ready.peekBoolean()) clock.step()
    clock.step()
    ch.valid.poke(false.B)
  }

  private def sendData(ch: DecoupledIO[AxiLiteWrData], clock: Clock, data: Int): Unit = {
    ch.bits.data.poke(data.U)
    ch.bits.strb.poke(15.U)
    ch.valid.poke(true.B)
    while (!ch.ready.peekBoolean()) clock.step()
    clock.step()
    ch.valid.poke(false.B)
  }

  "An AXI4-Lite slave" should "accept a write with the address first" in {
    test(new AxiLiteCounter()) { dut =>
      sendAddr(dut.io.aw, dut.clock, 0)
      sendData(dut.io.w, dut.clock, 1000)

      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.io.b.bits.resp.expect(AxiResp.okay)
      dut.clock.step()
    }
  }

  it should "accept the same write with the data first" in {
    test(new AxiLiteCounter()) { dut =>
      // The data beat arrives with no address in sight; the slave has to park
      // it until AW turns up.
      sendData(dut.io.w, dut.clock, 2000)
      dut.io.b.valid.expect(false.B, "no response until both halves have arrived")
      sendAddr(dut.io.aw, dut.clock, 4)

      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.io.b.bits.resp.expect(AxiResp.okay)
      dut.clock.step()
    }
  }

  it should "read back what was written" in {
    test(new AxiLiteCounter()) { dut =>
      sendAddr(dut.io.aw, dut.clock, 8)   // byte address 8 -> counter 2
      sendData(dut.io.w, dut.clock, 3000)
      dut.io.b.ready.poke(true.B)
      while (!dut.io.b.valid.peekBoolean()) dut.clock.step()
      dut.clock.step()

      sendAddr(dut.io.ar, dut.clock, 8)
      dut.io.r.ready.poke(true.B)
      while (!dut.io.r.valid.peekBoolean()) dut.clock.step()
      val value = dut.io.r.bits.data.peekInt()
      dut.io.r.bits.resp.expect(AxiResp.okay)
      dut.clock.step()

      // Free-running counters: a few cycles have passed since the write.
      assert(value >= 3000 && value < 3010, s"expected just over 3000, got $value")
    }
  }
}
