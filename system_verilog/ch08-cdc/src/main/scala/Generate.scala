import chisel3._

object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
  val designs: Seq[(String, () => Unit)] = Seq(
    "TwoFlopSync"        -> (() => emitVerilog(new TwoFlopSync(), opts)),
    "BadBusSync"         -> (() => emitVerilog(new BadBusSync(), opts)),
    "ResetSynchronizer"  -> (() => emitVerilog(new ResetSynchronizer(), opts)),
    "EnableFlop"         -> (() => emitVerilog(new EnableFlop(), opts)),
    "GatedRegister"      -> (() => emitVerilog(new GatedRegister(), opts))
  )
  val names = designs.map(_._1)
  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val sel = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((n,g) <- sel) { println("emitting " + targetDir + "/" + n + ".sv"); g() }
  }
}
