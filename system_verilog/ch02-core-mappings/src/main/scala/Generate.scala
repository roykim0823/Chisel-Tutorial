import chisel3._

// Emit SystemVerilog for every design in this part.
//   sbt "runMain Generate"              all of them
//   sbt "runMain Generate list"         show the available names
//   sbt "runMain Generate WireExample"  just one
object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)
  val designs: Seq[(String, () => Unit)] = Seq(
    "Adder"          -> (() => emitVerilog(new Adder(8), opts)),
    "CounterExample" -> (() => emitVerilog(new CounterExample(), opts)),
    "RegVariants"    -> (() => emitVerilog(new RegVariants(), opts)),
    "WireExample"    -> (() => emitVerilog(new WireExample(), opts)),
    "WhenExample"    -> (() => emitVerilog(new WhenExample(), opts)),
    "MuxExample"     -> (() => emitVerilog(new MuxExample(), opts)),
    "SimpleALU"      -> (() => emitVerilog(new SimpleALU(), opts))
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
