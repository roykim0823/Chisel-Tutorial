import chisel3._
import chisel3.experimental.hierarchy._

// The leaf we want many copies of.
@instantiable
class Leaf extends Module {
  @public val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val y = Output(UInt(8.W))
  })
  io.y := ~io.a
}

// Ordinary instantiation: Module(new Leaf) is elaborated once per instance.
class ManyModules(n: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(n, UInt(8.W)))
    val out = Output(Vec(n, UInt(8.W)))
  })
  val leaves = Seq.fill(n)(Module(new Leaf))
  for (i <- 0 until n) {
    leaves(i).io.a := io.in(i)
    io.out(i) := leaves(i).io.y
  }
}

// Definition/Instance: elaborate ONCE, instantiate many times.
class ManyInstances(n: Int) extends Module {
  val io = IO(new Bundle {
    val in  = Input(Vec(n, UInt(8.W)))
    val out = Output(Vec(n, UInt(8.W)))
  })
  val defn = Definition(new Leaf)
  val leaves = Seq.fill(n)(Instance(defn))
  for (i <- 0 until n) {
    leaves(i).io.a := io.in(i)
    io.out(i) := leaves(i).io.y
  }
}
