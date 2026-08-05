import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

// The type-parameterized MODULE: a router whose payload type is a parameter, with
// the address riding in its own parallel vector.
class ParamModuleTest extends AnyFlatSpec with ChiselScalatestTester {

  "NocRouter" should "route a Payload with a separate address vector" in {
    test(new NocRouter(new Payload, 2)) { dut =>
      dut.io.inPort(0).data.poke(111.U)
      dut.io.inPort(0).flag.poke(true.B)
      dut.io.address(0).poke(10.U)
      dut.io.inPort(1).data.poke(222.U)
      dut.io.inPort(1).flag.poke(false.B)
      dut.io.address(1).poke(20.U)

      // The stand-in routing logic swaps the two ports.
      dut.io.outPort(0).data.expect(222.U)
      dut.io.outPort(0).flag.expect(false.B)
      dut.io.outPort(1).data.expect(111.U)
      dut.io.outPort(1).flag.expect(true.B)
    }
  }
}
