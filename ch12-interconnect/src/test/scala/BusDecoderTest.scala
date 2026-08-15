import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import soc._

// The decoder and read mux of Figure 12.2. Purely combinational, so every check
// here happens without stepping the clock.
class BusDecoderTest extends AnyFlatSpec with ChiselScalatestTester {

  "A bus decoder" should "select one device per 16-byte window" in {
    test(new BusDecoder(devices = 4)) { dut =>
      // Byte address -> which device. The low four bits address *within* a
      // device, so 0x00 and 0x0c both land on device 0.
      for ((addr, device) <- Seq(0x00 -> 0, 0x0c -> 0, 0x10 -> 1,
                                 0x20 -> 2, 0x35 -> 3)) {
        dut.io.address.poke(addr.U)
        for (i <- 0 until 4) {
          dut.io.cs(i).expect((i == device).B,
            f"address 0x$addr%02x should select device $device, not $i")
        }
      }
    }
  }

  it should "never select two devices at once" in {
    test(new BusDecoder(devices = 4)) { dut =>
      for (addr <- 0 until 64) {
        dut.io.address.poke(addr.U)
        val hot = (0 until 4).count(i => dut.io.cs(i).peekBoolean())
        assert(hot == 1, f"address 0x$addr%02x drove $hot chip selects, expected 1")
      }
    }
  }

  it should "route the selected device's data back through the read mux" in {
    test(new BusDecoder(devices = 4)) { dut =>
      // Give each device a distinguishable value, then check the mux picks it.
      for (i <- 0 until 4) {
        dut.io.deviceRdData(i).poke((0xd0 + i).U)
      }
      for ((addr, device) <- Seq(0x00 -> 0, 0x10 -> 1, 0x20 -> 2, 0x30 -> 3)) {
        dut.io.address.poke(addr.U)
        dut.io.rdData.expect((0xd0 + device).U,
          f"address 0x$addr%02x should read device $device")
      }
    }
  }
}
