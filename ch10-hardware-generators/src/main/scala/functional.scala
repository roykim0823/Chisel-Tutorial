import chisel3._
import chisel3.util._

// Functional programming: combine hardware with higher-order functions.

// Sum a Vec by reducing with an adder. reduceTree builds a balanced tree (short
// combinational delay) rather than a chain.
class FunctionalAdd extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(5, UInt(10.W)))
    val res = Output(UInt(10.W))
  })

  val vec = io.in
  val sum = vec.reduceTree(_ + _)
  io.res := sum
}

// A function returning TWO outputs via a Scala tuple, then decomposed.
class FunctionalComp extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val equ = Output(UInt(8.W))
    val gt = Output(UInt(8.W))
  })

  def compare(a: UInt, b: UInt) = {
    val equ = a === b
    val gt = a > b
    (equ, gt)   // return a tuple
  }

  val (equ, gt) = compare(io.a, io.b)   // decompose the tuple
  io.equ := equ
  io.gt := gt
}

// Find the minimum value (and its index) in a Vec, three functional ways.
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
    .reduce((x, y) => (Mux(x._1 < y._1, x._1, y._1), Mux(x._1 < y._1, x._2, y._2)))

  io.min := min
  io.resA := res.v
  io.idxA := res.idx
  io.resB := resFun._1
  io.idxB := resFun._2
  // resC/idxC mirror resA/idxA here (kept for interface compatibility)
  io.resC := res.v
  io.idxC := res.idx
}

// A pure-Scala reference model, used to check the hardware in the test.
object ScalaFunctionalMin {
  def findMin(v: Seq[Int]) = {
    v.zip((0 until v.length).toList).reduce((x, y) => if (x._1 <= y._1) x else y)
  }
}
