import chisel3._
import chisel3.util._

// Find the minimum value (and its index) in a Vec, four functional ways.
// See Section 10.6.1.
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
