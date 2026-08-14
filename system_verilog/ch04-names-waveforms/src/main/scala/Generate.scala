import chisel3._

// Emit SystemVerilog for every design in this part.
//   sbt "runMain Generate"          all of them
//   sbt "runMain Generate list"     show the available names
object Generate extends App {
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)

  val designs: Seq[(String, () => Unit)] = Seq(
    "Naming"                -> (() => emitVerilog(new Naming(), opts)),
    "RenamedByDesiredName"  -> (() => emitVerilog(new NameControl(), opts)),
    "Keywords"              -> (() => emitVerilog(new Keywords(), opts)),
    "VecNames"              -> (() => emitVerilog(new VecNames(), opts)),
    "Hierarchy"             -> (() => emitVerilog(new Hierarchy(), opts))
  )
  val names = designs.map(_._1)

  if (args.contains("list")) println("Available designs: " + names.mkString(", "))
  else {
    val unknown = args.filterNot(names.contains)
    if (unknown.nonEmpty) {
      println("Unknown design(s): " + unknown.mkString(", "))
      println("Available designs: " + names.mkString(", ")); sys.exit(1)
    }
    val selected = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((name, gen) <- selected) { println("emitting " + targetDir + "/" + name + ".sv"); gen() }
  }
}
