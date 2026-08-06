import chisel3._
import chisel3.util._
// `_root_.` is required here: `chisel3.util._` above puts `chisel3.util.circt`
// in scope, so a plain `import circt.stage...` would resolve against that.
import _root_.circt.stage.ChiselStage

// Find the minimum value (and its index) in a Vec, four functional ways.
// See Section 10.6.1.
//
// This file holds the topic end to end: the book's combined module first, then
// a pure-Scala reference model, then the same four variants split one per
// module, and finally FunctionalMinDemo, which emits and measures those four.
class FunctionalMin(n: Int, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(w.W)))
    val min = Output(UInt(w.W))
    val resA = Output(UInt(w.W))
    val idxA = Output(UInt(8.W))
    val resB = Output(UInt(w.W))
    val idxB = Output(UInt(8.W))
    val resC = Output(UInt(w.W))
    val idxC = Output(UInt(8.W))
  })

  val vec = io.in

  // (a) minimum value only: reduceTree with a Mux.
  val min = vec.reduceTree((x, y) => Mux(x < y, x, y))

  // (b) value AND index, using a Bundle to carry both.
  class Two extends Bundle {
    val v = UInt(w.W)
    val idx = UInt(8.W)
  }
  val vecTwo = Wire(Vec(n, new Two()))
  for (i <- 0 until n) {
    vecTwo(i).v := vec(i)
    vecTwo(i).idx := i.U
  }
  val res = vecTwo.reduceTree((x, y) => Mux(x.v < y.v, x, y))

  // (c) value AND index, using Scala tuples + zipWithIndex + reduce.
  val resFun = vec.zipWithIndex
    .map((x) => (x._1, x._2.U))
    .reduce((x, y) => (Mux(x._1 < y._1, x._1, y._1),
      Mux(x._1 < y._1, x._2, y._2)))

  // (d) a Chisel MixedVec carries value and index like a tuple would, but IS a
  //     Chisel collection - so reduceTree is available again.
  val scalaVector = vec.zipWithIndex
    .map((x) => MixedVecInit(x._1, x._2.U(8.W)))
  val resFun2 = VecInit(scalaVector)
    .reduceTree((x, y) => Mux(x(0) < y(0), x, y))

  io.min := min
  io.resA := res.v
  io.idxA := res.idx
  io.resB := resFun._1
  io.idxB := resFun._2
  io.resC := resFun2(0)
  io.idxC := resFun2(1)
}

// A pure-Scala reference model, used to check the hardware in the test.
object ScalaFunctionalMin {
  def findMin(v: Seq[Int]) = {
    v.zip((0 until v.length).toList).reduce((x, y) => if (x._1 <= y._1) x else y)
  }
}

// ---------------------------------------------------------------------------
// The same four variants, one per module, so that the SystemVerilog generated
// by each can be compared. Same logic as in FunctionalMin above, just isolated:
// FunctionalMin builds all four at once, which makes its emitted Verilog
// impossible to read variant by variant.
// ---------------------------------------------------------------------------

// (a) minimum value only: reduceTree with a Mux.
class MinValueOnly(n: Int, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(w.W)))
    val min = Output(UInt(w.W))
  })

  io.min := io.in.reduceTree((x, y) => Mux(x < y, x, y))
}

// (b) value AND index, using a Bundle to carry both, reduceTree.
class MinBundle(n: Int, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(w.W)))
    val min = Output(UInt(w.W))
    val idx = Output(UInt(8.W))
  })

  class Two extends Bundle {
    val v = UInt(w.W)
    val idx = UInt(8.W)
  }
  val vecTwo = Wire(Vec(n, new Two()))
  for (i <- 0 until n) {
    vecTwo(i).v := io.in(i)
    vecTwo(i).idx := i.U
  }
  val res = vecTwo.reduceTree((x, y) => Mux(x.v < y.v, x, y))

  io.min := res.v
  io.idx := res.idx
}

// (c) value AND index, using Scala tuples + zipWithIndex + reduce (a CHAIN).
class MinTuple(n: Int, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(w.W)))
    val min = Output(UInt(w.W))
    val idx = Output(UInt(8.W))
  })

  val resFun = io.in.zipWithIndex
    .map((x) => (x._1, x._2.U))
    .reduce((x, y) => (Mux(x._1 < y._1, x._1, y._1),
      Mux(x._1 < y._1, x._2, y._2)))

  io.min := resFun._1
  io.idx := resFun._2
}

// (d) value AND index, using a MixedVec + reduceTree.
class MinMixedVec(n: Int, w: Int) extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(n, UInt(w.W)))
    val min = Output(UInt(w.W))
    val idx = Output(UInt(8.W))
  })

  val scalaVector = io.in.zipWithIndex
    .map((x) => MixedVecInit(x._1, x._2.U(8.W)))
  val resFun2 = VecInit(scalaVector)
    .reduceTree((x, y) => Mux(x(0) < y(0), x, y))

  io.min := resFun2(0)
  io.idx := resFun2(1)
}

// Emit and compare the SystemVerilog of the four minimum-search variants.
// Run with:  sbt "runMain FunctionalMinDemo"        (4 inputs, the default)
//            sbt "runMain FunctionalMinDemo 8"     (8 inputs)
object FunctionalMinDemo extends App {
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
