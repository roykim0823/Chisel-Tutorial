import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// The simplest form: one `"<subject>" should "<behavior>" in { ... }` per test.
class BcdTableTest extends AnyFlatSpec with ChiselScalatestTester {
  "BCD table" should "output BCD encoded numbers" in {
    test(new BcdTable) { dut =>
      dut.io.address.poke(0.U)
      dut.io.data.expect("h00".U)
      dut.io.address.poke(1.U)
      dut.io.data.expect("h01".U)
      dut.io.address.poke(13.U)
      dut.io.data.expect("h13".U)
      dut.io.address.poke(99.U)
      dut.io.data.expect("h99".U)
    }
  }
}

// The equivalent `behavior of` / `it should` form: name the module once, then
// refer to it with `it`. This reads better once a module has several tests.
class BcdTableTest2 extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "BCD table"

  it should "output BCD encoded numbers" in {
    test(new BcdTable) { dut =>
      dut.io.address.poke(0.U)
      dut.io.data.expect("h00".U)
      dut.io.address.poke(1.U)
      dut.io.data.expect("h01".U)
      dut.io.address.poke(13.U)
      dut.io.data.expect("h13".U)
      dut.io.address.poke(99.U)
      dut.io.data.expect("h99".U)
    }
  }
}
