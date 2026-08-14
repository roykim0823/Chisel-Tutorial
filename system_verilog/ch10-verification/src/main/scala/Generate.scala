import chisel3._
import _root_.circt.stage.ChiselStage

object Generate extends App {
  println(ChiselStage.emitSystemVerilog(new ReqGrant,
    firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization",
                        "--emit-chisel-asserts-as-sva")))
}
