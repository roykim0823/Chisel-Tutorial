import chisel3._

// An incrementer meant to SATURATE at the largest 32-bit value instead of
// wrapping around to zero. The bound is off by one - it holds at 0xfffffffe
// instead of 0xffffffff - so exactly one input out of 2^32 makes the output
// wrap, and the assertion catches it. That is a bug simulation is very
// unlikely to stumble on and formal verification finds at once (§13.4).
class Saturate extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  io.out := Mux(io.in === "hffff_fffe".U, io.in, io.in + 1.U)

  assert(io.out >= io.in, "a saturating increment must never wrap")
}

// The same circuit with the bound corrected, which the same bounded check
// then proves for every input.
class SaturateFixed extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  io.out := Mux(io.in === "hffff_ffff".U, io.in, io.in + 1.U)

  assert(io.out >= io.in, "a saturating increment must never wrap")
}

// §13.4: `assume` constrains the environment. AssertOverflow's assertion is
// false in general, but it becomes provable once the caller promises not to
// overflow - which is what an assumption states. `+&` is the widening add, so
// the sum in the assumption itself cannot wrap.
class AssumeNoOverflow extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val sum = Output(UInt(8.W))
  })
  io.sum := io.a + io.b

  assume(io.a +& io.b <= 255.U)

  assert(io.sum >= io.a, "8-bit add must not overflow")
}
