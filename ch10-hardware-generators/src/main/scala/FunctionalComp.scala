import chisel3._

// A function returning TWO outputs via a Scala tuple, then decomposed.
// Used by Section 10.1 (tuples) and Section 10.2 (functions as components).
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
