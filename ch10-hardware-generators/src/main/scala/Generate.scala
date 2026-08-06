import chisel3._

// Emit SystemVerilog for a representative set of this chapter's generators.
//   sbt "runMain Generate"                     all of them
//   sbt "runMain Generate UseAdder"            just one
//   sbt "runMain Generate UseAdder ParamFunc"  a couple
//   sbt "runMain Generate list"                show the available names
object Generate extends App {
  // Where the .sv files go. `emitVerilog` passes its second argument straight
  // to the Chisel/CIRCT command line, and `--target-dir` is what decides the
  // output directory; without it every file would land in the project root.
  // The directory is created if it does not exist, and is in .gitignore.
  val targetDir = "generated"
  val opts = Array("--target-dir", targetDir)

  // Name -> how to build it. The right-hand side is a function value, so a
  // design is only elaborated once it has been selected.
  val designs: Seq[(String, () => Unit)] = Seq(
    "BcdTable" -> (() => emitVerilog(new BcdTable(), opts)),
    "GenHardware" -> (() => emitVerilog(new GenHardware(), opts)),
    "UseAdder" -> (() => emitVerilog(new UseAdder(), opts)),   // ParamAdder(8) and (16)
    "ParamFunc" -> (() => emitVerilog(new ParamFunc(), opts)),
    "FunctionalMin" -> (() => emitVerilog(new FunctionalMin(5, 8), opts)),
    "UpTicker" -> (() => emitVerilog(new UpTicker(5), opts)),
    "ArbiterTree" -> (() => emitVerilog(new ArbiterTree(4, UInt(8.W)), opts)),  // 4:1 tree
    "UseParamRouter" -> (() => emitVerilog(new UseParamRouter(), opts)),
    "UseParamRouter2" -> (() => emitVerilog(new UseParamRouter2(), opts)),
    "RegisterFile" -> (() => emitVerilog(new RegisterFile(false), opts))        // no debug port
  )
  val names = designs.map(_._1)

  if (args.contains("list")) {
    println("Available designs: " + names.mkString(", "))
  } else {
    val unknown = args.filterNot(names.contains)
    if (unknown.nonEmpty) {
      println("Unknown design(s): " + unknown.mkString(", "))
      println("Available designs: " + names.mkString(", "))
      sys.exit(1)
    }
    // No name given means all of them.
    val selected = if (args.isEmpty) designs else designs.filter(d => args.contains(d._1))
    for ((name, generate) <- selected) {
      println("emitting " + targetDir + "/" + name + ".sv")
      generate()
    }
  }
}
