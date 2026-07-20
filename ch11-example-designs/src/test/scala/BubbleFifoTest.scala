import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class BubbleFifoTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Bubble FIFO"

  it should "bubble a word through and flow-control" in {
    test(new BubbleFifo(8, 4)) { dut =>
      dut.io.enq.din.poke("hab".U)
      dut.io.enq.write.poke(false.B)
      dut.io.deq.read.poke(false.B)
      dut.clock.step()

      // Write one word.
      dut.io.enq.din.poke("h12".U)
      dut.io.enq.write.poke(true.B)
      dut.clock.step()
      dut.io.enq.write.poke(false.B)

      // Let it bubble to the far end (depth 4), then read it out.
      dut.clock.step(4)
      dut.io.deq.empty.expect(false.B)
      dut.io.deq.dout.expect("h12".U)
      dut.io.deq.read.poke(true.B)
      dut.clock.step()
      dut.io.deq.read.poke(false.B)
    }
  }

  // ChiselTest supports concurrent test threads with fork/join.
  it should "work with multiple threads" in {
    test(new BubbleFifo(8, 4)) { dut =>
      val enq = fork {
        while (dut.io.enq.full.peekBoolean()) dut.clock.step()
        dut.io.enq.din.poke(42.U)
        dut.io.enq.write.poke(true.B)
        dut.clock.step()
        dut.io.enq.write.poke(false.B)
      }
      while (dut.io.deq.empty.peekBoolean()) dut.clock.step()
      dut.io.deq.dout.expect(42.U)
      dut.io.deq.read.poke(true.B)
      dut.clock.step()
      dut.io.deq.empty.expect(true.B)
      enq.join()
    }
  }
}
