import chisel3._

// A comparator: two outputs (equal, greater-than) cover every comparison.
// e.g. a >= b  is  (equ || gt);  a <= b  is  !gt.
// Compares are so short they are usually inlined, not wrapped in a module.
class Comparator extends Module {
  val io = IO(new Bundle() {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val equ = Output(Bool())
    val gt = Output(Bool())
  })

  val a = io.a
  val b = io.b

  val equ = a === b
  val gt = a > b

  io.equ := equ
  io.gt := gt
}
