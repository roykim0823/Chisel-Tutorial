import chisel3._

// Section 5, Exercise 1 - the design you annotate.
class SimpleALU extends Module {
  val io = IO(new Bundle {
    val op     = Input(UInt(2.W))
    val a      = Input(UInt(8.W))
    val b      = Input(UInt(8.W))
    val result = Output(UInt(8.W))
  })

  val res = Wire(UInt(8.W))
  when(io.op === 0.U)      { res := io.a + io.b }
  .elsewhen(io.op === 1.U) { res := io.a - io.b }
  .elsewhen(io.op === 2.U) { res := io.a & io.b }
  .otherwise               { res := io.a | io.b }

  io.result := res
}

// Print the SV to the console instead of writing a file, so you can compare
// the clean and the raw form side by side:
//   sbt "runMain Emit"        the full output, scaffolding and all
//   sbt "runMain Emit clean"  with locators and randomization stripped
object Emit extends App {
  val clean = args.contains("clean")
  val opts = if (clean) Array("-strip-debug-info", "--disable-all-randomization")
             else Array.empty[String]
  println(_root_.circt.stage.ChiselStage.emitSystemVerilog(new SimpleALU, firtoolOpts = opts))
}
