import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class ReadyValidBufferTest extends AnyFlatSpec with ChiselScalatestTester {
  "ReadyValidBuffer" should "accept, hold, and release one word" in {
    test(new ReadyValidBuffer) { dut =>
      dut.io.in.valid.poke(false.B)
      dut.io.in.bits.poke(12.U)
      dut.io.out.ready.poke(false.B)
      dut.clock.step()
      // empty: ready to accept, nothing valid out
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(false.B)
      // write one word -> full: not ready in, valid out
      dut.io.in.valid.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.expect(false.B)
      dut.io.out.valid.expect(true.B)
      dut.io.out.bits.expect(12.U)
      // read it -> empty again
      dut.io.out.ready.poke(true.B)
      dut.clock.step()
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(false.B)
    }
  }
}
