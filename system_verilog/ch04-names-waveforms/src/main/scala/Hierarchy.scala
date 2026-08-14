import chisel3._

// Section 4 of "Five Things" - module and instance names in the hierarchy.
class Leaf(width: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val y = Output(UInt(width.W))
  })
  io.y := ~io.a
}

class Hierarchy extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(16.W))
    val out = Output(UInt(16.W))
  })
  val small = Module(new Leaf(8))    // instance named `small`
  val big   = Module(new Leaf(16))   // same class, different parameter
  small.io.a := io.a
  big.io.a := io.b
  io.out := big.io.y | small.io.y
}
