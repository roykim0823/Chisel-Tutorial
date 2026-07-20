import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// One test drives BOTH flasher versions (they share FlasherBase): start it,
// then check the light is on for 6 cycles and off for 4 between three flashes.
class FlasherTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "Flasher"

  def testFn[T <: FlasherBase](f: T) = {
    f.io.start.poke(0.U)
    f.io.light.expect(0.U)
    f.clock.step()
    f.io.light.expect(0.U)
    f.clock.step()
    f.io.light.expect(0.U)
    f.io.start.poke(1.U)
    f.clock.step()
    f.io.start.poke(0.U)
    f.io.light.expect(1.U)
    f.clock.step(5)
    f.io.light.expect(1.U)
    f.clock.step()
    for (_ <- 0 until 2) {
      f.io.light.expect(0.U)
      f.clock.step(3)
      f.io.light.expect(0.U)
      f.clock.step()
      f.io.light.expect(1.U)
      f.clock.step(5)
      f.io.light.expect(1.U)
      f.clock.step()
      f.io.light.expect(0.U)
    }
    f.clock.step()
    f.io.light.expect(0.U)
    f.clock.step(5)
    f.io.light.expect(0.U)
  }

  it should "flash three times (both versions)" in {
    test(new Flasher)  { dut => testFn(dut) }
    test(new Flasher2) { dut => testFn(dut) }
  }
}
