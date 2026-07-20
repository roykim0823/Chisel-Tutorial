import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class FsmTest extends AnyFlatSpec with ChiselScalatestTester {

  "SimpleFsm" should "ring the bell after two bad events" in {
    test(new SimpleFsm) { dut =>
      dut.io.clear.poke(false.B)
      dut.io.badEvent.poke(false.B)
      dut.io.ringBell.expect(false.B)
      dut.clock.step()
      dut.io.ringBell.expect(false.B)
      dut.io.badEvent.poke(true.B)     // green -> orange
      dut.clock.step()
      dut.io.ringBell.expect(false.B)
      dut.io.badEvent.poke(false.B)
      dut.clock.step()
      dut.io.ringBell.expect(false.B)
      dut.io.badEvent.poke(true.B)     // orange -> red
      dut.clock.step()
      dut.io.ringBell.expect(true.B)
      dut.io.badEvent.poke(false.B)
      dut.clock.step()
      dut.io.ringBell.expect(true.B)
      dut.io.clear.poke(true.B)        // red -> green
      dut.clock.step()
      dut.io.ringBell.expect(false.B)
    }
  }

  "RisingFsm (Mealy)" should "pulse on the same cycle as the edge" in {
    test(new RisingFsm) { dut =>
      dut.io.din.poke(false.B)
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.din.poke(true.B)
      dut.io.risingEdge.expect(true.B)   // Mealy: immediate
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
      dut.io.din.poke(false.B)
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
    }
  }

  "RisingMooreFsm (Moore)" should "pulse one cycle after the edge" in {
    test(new RisingMooreFsm) { dut =>
      dut.io.din.poke(false.B)
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.din.poke(true.B)
      dut.io.risingEdge.expect(false.B)  // Moore: not yet
      dut.clock.step()
      dut.io.risingEdge.expect(true.B)   // pulse one cycle later
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
      dut.io.din.poke(false.B)
      dut.io.risingEdge.expect(false.B)
      dut.clock.step()
      dut.io.risingEdge.expect(false.B)
    }
  }
}
