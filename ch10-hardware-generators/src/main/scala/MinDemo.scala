import chisel3._
import circt.stage.ChiselStage

// Emit and compare the SystemVerilog of the four minimum-search variants.
// Run with:  sbt "runMain MinDemo"        (4 inputs, the default)
//            sbt "runMain MinDemo 8"     (8 inputs)
object MinDemo extends App {
  val n = if (args.isEmpty) 4 else args(0).toInt
  val w = 8

  val variants: Seq[(String, () => Module)] = Seq(
    "(a) MinValueOnly  reduceTree + Mux" -> (() => new MinValueOnly(n, w)),
    "(b) MinBundle     Bundle + reduceTree" -> (() => new MinBundle(n, w)),
    "(c) MinTuple      Scala tuple + reduce" -> (() => new MinTuple(n, w)),
    "(d) MinMixedVec   MixedVec + reduceTree" -> (() => new MinMixedVec(n, w))
  )

  // Just the module body: strip the header comment and the source locators.
  def verilog(gen: () => Module): String =
    ChiselStage
      .emitSystemVerilog(gen(), firtoolOpts = Array("-strip-debug-info"))
      .linesIterator
      .dropWhile(!_.startsWith("module"))
      .mkString("\n")

  // Crude but telling size metrics: how many comparators and multiplexers the
  // emitted module contains, and how many named wires it needs.
  def metrics(sv: String): String = {
    val cmp = "<".r.findAllIn(sv).size
    val mux = raw"\?".r.findAllIn(sv).size
    val wires = sv.linesIterator.count(_.trim.startsWith("wire"))
    f"comparators: $cmp%2d   muxes: $mux%2d   wires: $wires%2d"
  }

  println(s"=== minimum search over $n inputs of $w bits ===\n")

  for ((name, gen) <- variants) {
    val sv = verilog(gen)
    println(s"--- $name ---")
    println(metrics(sv))
    println(sv)
    println()
  }

  println("=== summary ===")
  for ((name, gen) <- variants) println(f"${name.take(20)}%-22s ${metrics(verilog(gen))}")
}
