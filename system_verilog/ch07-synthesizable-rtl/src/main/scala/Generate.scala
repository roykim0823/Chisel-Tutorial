import chisel3._

object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
  val designs: Seq[(String, () => Unit)] = Seq(
    "DefaultFirst"     -> (() => emitVerilog(new DefaultFirst(), opts)),
    "WireDefaultForm"  -> (() => emitVerilog(new WireDefaultForm(), opts)),
    "OtherwiseForm"    -> (() => emitVerilog(new OtherwiseForm(), opts)),
    "DelayChain"       -> (() => emitVerilog(new DelayChain(4), opts)),
    "ConfigurableReg"  -> (() => emitVerilog(new Configurable(false), opts)),
    "AnalogPort"       -> (() => emitVerilog(new AnalogPort(), opts))
  )
  val names = designs.map(_._1)
  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val sel = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((n,g) <- sel) { println("emitting " + targetDir + "/" + n + ".sv"); g() }
  }
}
