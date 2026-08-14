import chisel3._

// Emit SystemVerilog for every design in this part.
//   sbt "runMain Generate"            all of them
//   sbt "runMain Generate list"       show the available names
//   sbt "runMain Generate VecExample" just one
object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
  val designs: Seq[(String, () => Unit)] = Seq(
    "VecExample"      -> (() => emitVerilog(new VecExample(), opts)),
    "NestedExample"   -> (() => emitVerilog(new NestedExample(), opts)),
    "SignedExample"   -> (() => emitVerilog(new SignedExample(), opts)),
    "MemExample"      -> (() => emitVerilog(new MemExample(), opts)),
    "AsyncMemExample" -> (() => emitVerilog(new AsyncMemExample(), opts)),
    "Fsm"             -> (() => emitVerilog(new Fsm(), opts)),
    "DontCareExample" -> (() => emitVerilog(new DontCareExample(), opts))
  )
  val names = designs.map(_._1)
  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val unknown = args.filterNot(names.contains)
    if (unknown.nonEmpty) {
      println("Unknown design(s): " + unknown.mkString(", "))
      println("Available designs: " + names.mkString(", ")); sys.exit(1)
    }
    val sel = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((n,g) <- sel) { println("emitting " + targetDir + "/" + n + ".sv"); g() }
  }
}
