import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// ---------------------------------------------------------------------------
// Devices under test (DUTs)
// ---------------------------------------------------------------------------

// A tiny combinational circuit: bitwise AND of two 2-bit inputs, plus an
// equality flag. Used by every test bench below.
class DeviceUnderTest extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val out = Output(UInt(2.W))
    val equ = Output(Bool())
  })

  io.out := io.a & io.b
  io.equ := io.a === io.b
}

// Same DUT, but with a printf for "printf debugging". printf fires on every
// rising clock edge during simulation.
class DeviceUnderTestPrintf extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val out = Output(UInt(2.W))
  })

  io.out := io.a & io.b
  printf("dut: %d %d %d\n", io.a, io.b, io.out)
}

// ---------------------------------------------------------------------------
// Test benches
// ---------------------------------------------------------------------------

// Drive inputs, step the clock, and PRINT the outputs (peekInt returns a Scala
// value). Good first step for exploring a design.
class SimpleTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new DeviceUnderTest) { dut =>
      dut.io.a.poke(0.U)
      dut.io.b.poke(1.U)
      dut.clock.step()
      println("Result is: " + dut.io.out.peekInt())
      dut.io.a.poke(3.U)
      dut.io.b.poke(2.U)
      dut.clock.step()
      println("Result is: " + dut.io.out.peekInt())
    }
  }
}

// Same stimulus, but assert expectations with expect(). A mismatch fails.
class SimpleTestExpect extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new DeviceUnderTest) { dut =>
      dut.io.a.poke(0.U)
      dut.io.b.poke(1.U)
      dut.clock.step()
      dut.io.out.expect(0.U)
      dut.io.a.poke(3.U)
      dut.io.b.poke(2.U)
      dut.clock.step()
      dut.io.out.expect(2.U)
    }
  }
}

// Read outputs into Scala types (peekInt / peekBoolean) and use plain Scala
// assert(). Useful when test logic needs the value in "Scala land".
class SimpleTestPeek extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new DeviceUnderTest) { dut =>
      dut.io.a.poke(0.U)
      dut.io.b.poke(1.U)
      dut.clock.step()
      dut.io.out.expect(0.U)
      val res = dut.io.out.peekInt()
      assert(res == 0)
      val equ = dut.io.equ.peekBoolean()
      assert(!equ)
    }
  }
}

// Use Scala loops to enumerate ALL input combinations, exercising the printf DUT.
class SimplePrintfTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new DeviceUnderTestPrintf) { dut =>
      for (a <- 0 until 4) {
        for (b <- 0 until 4) {
          dut.io.a.poke(a.U)
          dut.io.b.poke(b.U)
          dut.clock.step()
        }
      }
    }
  }
}
