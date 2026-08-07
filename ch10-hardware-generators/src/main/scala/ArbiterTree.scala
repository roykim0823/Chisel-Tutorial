import chisel3._
import chisel3.util._

// An arbitration tree built with reduceTree: n ready/valid inputs are reduced
// to a single ready/valid output by a function that arbitrates between exactly
// TWO requests. The tree itself is generated, not written out by hand.
//
// The two arbitration functions live OUTSIDE the modules, in the Arbitration
// object below, and the class that builds the tree takes one of them as a
// parameter. The book instead defines each function inside its own subclass and
// repeats the reduceTree line in both; keeping the functions separate says the
// same thing more directly - the combining function is just a value, and it is
// what distinguishes one arbiter from the other.
//
// Each function derives its data type from its own arguments with
// `chiselTypeOf(a.bits)`, so it needs no `gen` parameter and no enclosing class.

object Arbitration {

  // A PRIORITY 2:1 arbiter: input `a` always wins when both are pending, so a
  // busy high-priority input can starve the other one.
  //
  // Assumes a requester holds `valid` until it is acknowledged by `ready`, and
  // that `ready` may be asserted one cycle after `valid` is seen. The winning
  // data must be registered: with a ready/valid interface a combinational path
  // from `ready` to `valid` is not allowed.
  def arbitrateSimp[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {

    val regData = Reg(chiselTypeOf(a.bits))
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))

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

  // A FAIR 2:1 arbiter: a small state machine remembers whose turn it is. The
  // two idle states give each input a turn, the two "has" states hold data
  // until the consumer takes it.
  //
  // Only one input will be ready, as we cannot take two values in one cycle.
  // A shadow register would be a reasonable optimisation; without it one channel
  // can only take one data item every 2 clock cycles.
  def arbitrateFair[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {
    object State extends ChiselEnum {
      val idleA, idleB, hasA, hasB = Value
    }
    import State._
    val regData = Reg(chiselTypeOf(a.bits))
    val regState = RegInit(idleA)
    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))
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
}

import Arbitration._

// The whole generator: the interface, plus the one line that turns a 2:1
// arbitration function into an n-input tree. `arbitrate` is an ordinary
// function value, so this single class covers both arbiters:
//
//   new Arbiter(4, UInt(8.W), arbitrateSimp)   // priority
//   new Arbiter(4, UInt(8.W), arbitrateFair)   // fair
//
// `gen` is a plain constructor parameter, with no `val`: a Module does not
// reflect over its fields the way a Bundle does, so the spelling makes no
// difference to the generated hardware here. The book writes
// `private val gen: T`; see ParamBundle.scala for the Bundle case, where
// public/private really does change the emitted Verilog.
class Arbiter[T <: Data: Manifest](
    n: Int,
    gen: T,
    arbitrate: (DecoupledIO[T], DecoupledIO[T]) => DecoupledIO[T]
) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new DecoupledIO(gen)))
    val out = new DecoupledIO(gen)
  })

  io.out <> io.in.reduceTree((a, b) => arbitrate(a, b))
}

// Named variants, so the two trees can be built (and tested, and emitted) by
// name. Each is nothing but the base class with one of the two functions
// plugged in.
class ArbiterSimpleTree[T <: Data: Manifest](n: Int, gen: T)
  extends Arbiter(n, gen, arbitrateSimp[T])

class ArbiterTree[T <: Data: Manifest](n: Int, gen: T)
  extends Arbiter(n, gen, arbitrateFair[T])
