import chisel3._
// NOT `chisel3.util._`: that would pull in Chisel's own `chisel3.util.Arbiter`
// and shadow this chapter's `Arbiter` class. Import only what is needed.
import chisel3.util.DecoupledIO

// Arbitration functions written purely to study ONE question: does the order of
// the `when` blocks inside an arbitration function matter?
//
// None of these is the chapter's design - the chapter's arbiters are the two in
// ArbiterTree.scala, straight from the book. Two of the three below are here to
// be compared against `arbitrateSimp`, and one of them is deliberately worse.
// The measurements are in ArbiterOrderTest.scala and in Appendix A of README.md.
//
//   arbitrateSimpSwapped     - arbitrateSimp with capture written before decide.
//                              Same statements, different hardware: the grant
//                              stays asserted a second cycle, so input `a` is
//                              acknowledged twice per word forwarded.
//   arbitrateSimpFree        - arbitrateSimp with the decide guard extended so
//                              decide and capture can never fire together. Same
//                              behaviour as the book's version, but the
//                              statements may be written in any order.
//   arbitrateSimpFreeSwapped - arbitrateSimpFree with the same swap applied, to
//                              show that it now changes nothing.
object ArbiterVariants {

  // ---- arbitrateSimp, capture written BEFORE decide ----------------------
  def arbitrateSimpSwapped[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {

    val regData = Reg(chiselTypeOf(a.bits))
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))

    // ---- capture FIRST (in arbitrateSimp this block comes second) ----------
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

    // ---- decide SECOND (in arbitrateSimp this chain comes first) ----------
    when (a.valid & regEmpty & !regReadyB) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & !regReadyA) {
      regReadyB := true.B
    }
    a.ready := regReadyA
    b.ready := regReadyB

    out.valid := !regEmpty
    when (out.ready) {
      regEmpty := true.B
    }

    out.bits := regData
    out
  }

  // ---- order-independent rewrite: `noGrant` makes the two exclusive ------
  def arbitrateSimpFree[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {

    val regData = Reg(chiselTypeOf(a.bits))
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))

    val noGrant = !regReadyA & !regReadyB          // <- the whole fix

    when (a.valid & regEmpty & noGrant) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & noGrant) {
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

  // ---- the same rewrite with the blocks swapped: no longer any effect ----
  def arbitrateSimpFreeSwapped[T <: Data](a: DecoupledIO[T], b: DecoupledIO[T]): DecoupledIO[T] = {

    val regData = Reg(chiselTypeOf(a.bits))
    val regEmpty = RegInit(true.B)
    val regReadyA = RegInit(false.B)
    val regReadyB = RegInit(false.B)

    val out = Wire(new DecoupledIO(chiselTypeOf(a.bits)))

    val noGrant = !regReadyA & !regReadyB

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

    when (a.valid & regEmpty & noGrant) {
      regReadyA := true.B
    } .elsewhen (b.valid & regEmpty & noGrant) {
      regReadyB := true.B
    }
    a.ready := regReadyA
    b.ready := regReadyB

    out.valid := !regEmpty
    when (out.ready) {
      regEmpty := true.B
    }

    out.bits := regData
    out
  }
}

// Emit the two statement orders (and the two order-free ones) as SystemVerilog
// so they can be diffed:
//
//   sbt "runMain ArbiterOrderEmit"
//   diff generated/order_asWritten.sv generated/order_swapped.sv
//
// See Appendix A of the chapter README.
object ArbiterOrderEmit extends App {
  import Arbitration.arbitrateSimp
  import ArbiterVariants._

  def emit(f: (DecoupledIO[UInt], DecoupledIO[UInt]) => DecoupledIO[UInt], name: String): Unit = {
    val sv = _root_.circt.stage.ChiselStage.emitSystemVerilog(
      new Arbiter(2, UInt(8.W), f), firtoolOpts = Array("-strip-debug-info"))
    val dir = java.nio.file.Paths.get("generated")
    java.nio.file.Files.createDirectories(dir)
    java.nio.file.Files.write(dir.resolve(name), sv.getBytes)
    println("emitting generated/" + name)
  }

  emit(arbitrateSimp[UInt], "order_asWritten.sv")
  emit(arbitrateSimpSwapped[UInt], "order_swapped.sv")
  emit(arbitrateSimpFree[UInt], "order_free.sv")
  emit(arbitrateSimpFreeSwapped[UInt], "order_freeSwapped.sv")
}
