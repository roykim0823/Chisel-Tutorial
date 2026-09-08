import chisel3._

// Chisel `assert` states an assumption about the hardware. It is checked during
// simulation (the sim stops with a message if it fails) and is ignored during
// hardware generation.
class Assert extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  /* These two are NOT always true: an 8-bit add can overflow, so the sum can be
     smaller than an input. (This is the classic bug formal verification finds.)
  assert(io.sum >= io.a)
  assert(io.sum >= io.b)
   */
  assert(io.sum === io.a + io.b)
}

// The same adder carrying the assertion the comment above describes. Unlike
// `Assert`'s tautology, this one is not provably true, so it survives into the
// generated SystemVerilog - which is what lets you see what an assert emits.
// Formal verification finds the counterexample (§13.4).
class AssertOverflow extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  assert(io.sum >= io.a, "8-bit add must not overflow")
}
