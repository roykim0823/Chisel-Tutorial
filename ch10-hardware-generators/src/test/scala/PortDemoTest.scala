import chisel3._
import chiseltest._
import circt.stage.ChiselStage
import org.scalatest.flatspec.AnyFlatSpec

// Locks in the behaviour the README claims about parameterized Bundles. Most of
// these need no simulator at all: the field list and the generated port names
// are decided at elaboration time.
class PortDemoTest extends AnyFlatSpec with ChiselScalatestTester {

  def fields(b: Bundle): Seq[String] = b.elements.keys.toSeq

  // Generated port names of a router built from the given port type.
  def ports[T <: Data](dt: T): Seq[String] =
    ChiselStage
      .emitSystemVerilog(new NocRouter2(dt, 2), firtoolOpts = Array("-strip-debug-info"))
      .linesIterator
      .filter(l => l.contains("input") || l.contains("output"))
      .map(_.trim.stripSuffix(","))
      .toSeq

  "Port" should "keep its type parameter out of the Bundle's fields" in {
    assert(fields(new Port(new Payload)).toSet == Set("address", "data"))
  }

  it should "generate only address and data ports" in {
    val p = ports(new Port(new Payload))
    assert(!p.exists(_.contains("_dt_")), s"unexpected dt ports: $p")
    assert(p.exists(_.endsWith("io_inPort_0_address")))
    assert(p.exists(_.endsWith("io_inPort_0_data_data")))
  }

  "PortPublic" should "leak the public parameter as a third field" in {
    assert(fields(new PortPublic(new Payload)).toSet == Set("address", "data", "dt"))
  }

  // The phantom field is real hardware: a full extra Payload on every port.
  it should "generate phantom dt ports" in {
    val p = ports(new PortPublic(new Payload))
    assert(p.count(_.contains("_dt_")) == 8, s"expected 8 phantom ports, got $p")
  }

  "PortAliased" should "be rejected by Chisel's aliasing check" in {
    val e = intercept[Exception] {
      ChiselStage.emitSystemVerilog(new NocRouter2(new PortAliased(new Payload), 2))
    }
    assert(e.getMessage.contains("aliased fields"), s"unexpected message: ${e.getMessage}")
  }

  // The parameterized Bundle must carry BOTH of its fields through the router.
  "NocRouter2" should "route a Port[Payload] end to end" in {
    test(new NocRouter2(new Port(new Payload), 2)) { dut =>
      dut.io.inPort(0).address.poke(10.U)
      dut.io.inPort(0).data.data.poke(111.U)
      dut.io.inPort(0).data.flag.poke(true.B)
      dut.io.inPort(1).address.poke(20.U)
      dut.io.inPort(1).data.data.poke(222.U)
      dut.io.inPort(1).data.flag.poke(false.B)

      // The stand-in routing logic swaps the two ports.
      dut.io.outPort(0).address.expect(20.U)
      dut.io.outPort(0).data.data.expect(222.U)
      dut.io.outPort(0).data.flag.expect(false.B)
      dut.io.outPort(1).address.expect(10.U)
      dut.io.outPort(1).data.data.expect(111.U)
      dut.io.outPort(1).data.flag.expect(true.B)
    }
  }

  // The two-parallel-vectors version routes the payload only; the address rides
  // in its own vector.
  "NocRouter" should "route a Payload with a separate address vector" in {
    test(new NocRouter(new Payload, 2)) { dut =>
      dut.io.inPort(0).data.poke(111.U)
      dut.io.inPort(0).flag.poke(true.B)
      dut.io.address(0).poke(10.U)
      dut.io.inPort(1).data.poke(222.U)
      dut.io.inPort(1).flag.poke(false.B)
      dut.io.address(1).poke(20.U)

      dut.io.outPort(0).data.expect(222.U)
      dut.io.outPort(0).flag.expect(false.B)
      dut.io.outPort(1).data.expect(111.U)
      dut.io.outPort(1).flag.expect(true.B)
    }
  }
}
