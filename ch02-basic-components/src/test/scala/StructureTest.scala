import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class StructureTest extends AnyFlatSpec with ChiselScalatestTester {
  "Structure" should "mux with a Vec and default with a VecInit" in {
    test(new Structure) { dut =>
      dut.io.x.poke(11.U)
      dut.io.y.poke(22.U)
      dut.io.z.poke(33.U)
      dut.io.d.poke(4.U)
      dut.io.e.poke(5.U)
      dut.io.f.poke(6.U)
      dut.io.bIdx.poke(0.U)
      dut.io.cond.poke(false.B)

      // A Vec in a Wire, indexed by a signal, is a multiplexer.
      dut.io.sel.poke(0.U); dut.io.muxOut.expect(11.U)
      dut.io.sel.poke(1.U); dut.io.muxOut.expect(22.U)
      dut.io.sel.poke(2.U); dut.io.muxOut.expect(33.U)

      // The bundle fields are driven with constants.
      dut.io.chData.expect(123.U)
      dut.io.chValid.expect(true.B)

      // v(1.U) is a constant index into 1, 3, 5.
      dut.io.vOut.expect(3.U)

      // VecInit(d, e, f) is the same mux built from signals.
      dut.io.sel.poke(1.U); dut.io.vecOutSig.expect(5.U)

      // VecInit(1, 2, 3) holds its defaults until `cond` overwrites them.
      dut.io.sel.poke(0.U)
      dut.io.vecOut.expect(1.U)
      dut.io.cond.poke(true.B)
      dut.io.vecOut.expect(4.U)
    }
  }
}
