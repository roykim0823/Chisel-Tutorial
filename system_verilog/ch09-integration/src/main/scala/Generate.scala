import chisel3._
import _root_.circt.stage.ChiselStage

object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
  val designs: Seq[(String, () => Unit)] = Seq(
    "UseExtAnd" -> (() => emitVerilog(new UseExtAnd(), opts)),
    "Probed"    -> (() => emitVerilog(new Probed(), opts)),
    "Sram"      -> (() => emitVerilog(new Sram(), opts))
  )
  val names = designs.map(_._1)
  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val sel = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((n,g) <- sel) { println("emitting " + targetDir + "/" + n + ".sv"); g() }
  }
}

// Section 2 - the same memory with --repl-seq-mem, which extracts it for
// replacement by a foundry macro.
//   sbt "runMain SramMacro"
object SramMacro extends App {
  println(ChiselStage.emitSystemVerilog(new Sram,
    firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization",
                        "--repl-seq-mem", "--repl-seq-mem-file=generated/sram.conf")))
}
