import chisel3._
import _root_.circt.stage.ChiselStage

// Emit SystemVerilog for this chapter's designs.
// Run with:  sbt "runMain Generate"
//
// Note the contrast between the two adders: `Assert`'s assertion is a tautology
// and is optimized away entirely, while `AssertOverflow`'s is not provably true
// and survives into the output.
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  emitVerilog(new Assert(), opts)
  emitVerilog(new AssertOverflow(), opts)
  emitVerilog(new TickGenTestTop(), opts)

  // §13.4's formal examples. Saturate and SaturateFixed differ by one constant,
  // so diffing their .sv shows the off-by-one the solver found. AssumeNoOverflow
  // is here because `assume` survives into the output as a real SystemVerilog
  // `assume` statement, which is worth seeing next to an assertion's
  // $error/$fatal pair.
  emitVerilog(new Saturate(), opts)
  emitVerilog(new SaturateFixed(), opts)
  emitVerilog(new AssumeNoOverflow(), opts)

  // NOT emitted: MonotonicCounter. `past` is not a hardware construct - it is a
  // chiseltest FIRRTL transform, so it attaches annotations firtool rejects with
  // `error: Unhandled annotation: ... chiseltest.simulator.Firrtl2AnnotationWrapper`.
  // It runs under `verify` (which uses chiseltest's own firrtl2 pipeline) only.
}

// The same assertion emitted as a concurrent SystemVerilog assertion instead of
// a $error/$fatal pair, which is the form formal tools consume.
// Run with:  sbt "runMain GenerateSva"
object GenerateSva extends App {
  println(ChiselStage.emitSystemVerilog(new AssertOverflow,
    firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization",
                        "--emit-chisel-asserts-as-sva")))
}
