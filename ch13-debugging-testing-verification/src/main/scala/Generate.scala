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
}

// The same assertion emitted as a concurrent SystemVerilog assertion instead of
// a $error/$fatal pair, which is the form formal tools consume.
// Run with:  sbt "runMain GenerateSva"
object GenerateSva extends App {
  println(ChiselStage.emitSystemVerilog(new AssertOverflow,
    firtoolOpts = Array("-strip-debug-info", "--disable-all-randomization",
                        "--emit-chisel-asserts-as-sva")))
}
