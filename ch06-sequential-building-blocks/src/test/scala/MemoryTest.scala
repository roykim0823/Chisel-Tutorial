import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class MemoryTest extends AnyFlatSpec with ChiselScalatestTester {

  "Memory" should "read back written data" in {
    test(new Memory) { dut =>
      dut.io.wrEna.poke(true.B)
      for (i <- 0 to 10) {
        dut.io.wrAddr.poke(i.U)
        dut.io.wrData.poke((i * 10).U)
        dut.clock.step()
      }
      dut.io.wrEna.poke(false.B)

      dut.io.rdAddr.poke(10.U)
      dut.clock.step()            // synchronous read: data appears next cycle
      dut.io.rdData.expect(100.U)
      dut.io.rdAddr.poke(5.U)
      dut.io.rdData.expect(100.U) // still the previous read this cycle
      dut.clock.step()
      dut.io.rdData.expect(50.U)
    }
  }

  "ForwardingMemory" should "forward a read-during-write" in {
    test(new ForwardingMemory) { dut =>
      dut.io.wrAddr.poke(20.U)
      dut.io.wrData.poke(123.U)
      dut.io.wrEna.poke(true.B)
      dut.io.rdAddr.poke(20.U)     // read the same address being written
      dut.clock.step()
      dut.io.rdData.expect(123.U)  // forwarded new value
    }
  }

  "MemoryWriteFirst" should "return the just-written value" in {
    test(new MemoryWriteFirst) { dut =>
      dut.io.wrAddr.poke(20.U)
      dut.io.wrData.poke(123.U)
      dut.io.wrEna.poke(true.B)
      dut.io.rdAddr.poke(20.U)
      dut.clock.step()
      dut.io.rdData.expect(123.U)
    }
  }
}
