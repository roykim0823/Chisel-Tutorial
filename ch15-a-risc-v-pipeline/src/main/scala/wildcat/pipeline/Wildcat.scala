package wildcat.pipeline

import chisel3._

// The common abstract top level for Wildcat implementations. The interface is
// just the instruction- and data-memory ports; the actual memories, caches, and
// IO devices are wired up at the SoC top level.
abstract class Wildcat() extends Module {
  val io = IO(new Bundle {
    val imem = new InstrIO()
    val dmem = new MemIO()
  })
}
