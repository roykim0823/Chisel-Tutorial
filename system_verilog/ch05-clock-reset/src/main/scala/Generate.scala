import chisel3._

// Emit SystemVerilog for every design in this part.
//   sbt "runMain Generate"        all of them
//   sbt "runMain Generate list"   show the available names
object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)

  val designs: Seq[(String, () => Unit)] = Seq(
    "SyncResetExample"  -> (() => emitVerilog(new SyncResetExample(), opts)),
    "AsyncResetExample" -> (() => emitVerilog(new AsyncResetExample(), opts)),
    "NoResetExample"    -> (() => emitVerilog(new NoResetExample(), opts)),
    "TwoClocks"         -> (() => emitVerilog(new TwoClocks(), opts)),
    "FlipDemo"          -> (() => emitVerilog(new FlipDemo(), opts)),
    "DecoupledDemo"     -> (() => emitVerilog(new DecoupledDemo(), opts))
  )
  val names = designs.map(_._1)
  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val unknown = args.filterNot(names.contains)
    if (unknown.nonEmpty) { println("Unknown: " + unknown.mkString(", ")); sys.exit(1) }
    val sel = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((n, g) <- sel) { println("emitting " + targetDir + "/" + n + ".sv"); g() }
  }
}
