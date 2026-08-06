import chisel3._
import chisel3.util._

// An arbitration tree built with reduceTree: n ready/valid inputs are reduced
// to a single ready/valid output by a function that arbitrates between exactly
// TWO requests. The tree itself is generated, not written out by hand.
//
// The base class only fixes the interface; a subclass supplies the 2:1
// arbitration function and wires up the tree. `gen` is a plain constructor
// parameter, with no `val`: a Module does not reflect over its fields the way a
// Bundle does, so the spelling makes no difference to the generated hardware
// here. The book writes `private val gen: T`; see ParamBundle.scala for the
// Bundle case, where public/private really does change the emitted Verilog.
class Arbiter[T <: Data: Manifest](n: Int, gen: T) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new DecoupledIO(gen)))
    val out = new DecoupledIO(gen)
  })
}

// Only one input will be ready, as we cannot take two values in one cycle.
// A shadow register would be a reasonable optimisation; without it one channel
// can only take one data item every 2 clock cycles.
class ArbiterTree[T <: Data: Manifest](n: Int, gen: T) extends Arbiter(n, gen) {

  // A FAIR 2:1 arbiter: a small state machine remembers whose turn it is. The
  // two idle states give each input a turn, the two "has" states hold data
  // until the consumer takes it.
  def arbitrateFair(a: DecoupledIO[T], b: DecoupledIO[T]) = {
    object State extends ChiselEnum {
      val idleA, idleB, hasA, hasB = Value
    }
    import State._
    val regData = Reg(gen)
    val regState = RegInit(idleA)
    val out = Wire(new DecoupledIO(gen))
    a.ready := regState === idleA
    b.ready := regState === idleB
    out.valid := (regState === hasA || regState === hasB)
    switch(regState) {
      is (idleA) {
        when (a.valid) {
          regData := a.bits
          regState := hasA
        } otherwise {
          regState := idleB
        }
      }
      is (idleB) {
        when (b.valid) {
          regData := b.bits
          regState := hasB
        } otherwise {
          regState := idleA
        }
      }
      is (hasA) {
        when (out.ready) {
          regState := idleB
        }
      }
      is (hasB) {
        when (out.ready) {
          regState := idleA
        }
      }
    }
    out.bits := regData
    out
  }

  io.out <> io.in.reduceTree((a, b) => arbitrateFair(a, b))
}

// The same tree built from a PRIORITY 2:1 arbiter: input `a` always wins when
// both are pending, so a busy high-priority input can starve the other one.
class ArbiterSimpleTree[T <: Data: Manifest](n: Int, gen: T) extends Arbiter(n, gen) {

  // Assumes a requester holds `valid` until it is acknowledged by `ready`, and
  // that `ready` may be asserted one cycle after `valid` is seen. The winning
  // data must be registered: with a ready/valid interface a combinational path
  // from `ready` to `valid` is not allowed.
  def arbitrateSimp(a: DecoupledIO[T], b: DecoupledIO[T]) = {

    val regData = Reg(gen)
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(gen))

    when (a.valid & regEmpty & !regReadyB) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & !regReadyA) {
      regReadyB := true.B
    }
    a.ready := regReadyA
    b.ready := regReadyB

    when (regReadyA) {
      regData := a.bits
      regEmpty := false.B
      regReadyA := false.B
    }
    when (regReadyB) {
      regData := b.bits
      regEmpty := false.B
      regReadyB := false.B
    }

    out.valid := !regEmpty
    when (out.ready) {
      regEmpty := true.B
    }

    out.bits := regData
    out
  }

  io.out <> io.in.reduceTree((a, b) => arbitrateSimp(a, b))
}
