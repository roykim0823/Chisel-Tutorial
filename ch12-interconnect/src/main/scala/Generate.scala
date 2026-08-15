import chisel3._

// Emit SystemVerilog for this chapter's devices.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  // Collect the emitted .sv files in one folder instead of the project root.
  // Chisel's own default target directory is "." - `--target-dir` overrides it.
  val opts = Array("--target-dir", "generated")

  // The wiring of Figure 12.2: address decoder plus read mux, no handshaking.
  emitVerilog(new soc.BusDecoder(), opts)

  // The same four counters under each of the three handshake schemes, all on
  // ReqAckIO, so the generated code isolates what the handshake costs.
  emitVerilog(new soc.CounterDeviceComb(2), opts)   // Figure 12.3 timing
  emitVerilog(new soc.CounterDeviceReg(), opts)
  emitVerilog(new soc.CounterDevice(), opts)        // pipelined
  emitVerilog(new soc.UseMemMappedRV(UInt(16.W)), opts)

  // The same four counters behind the three protocols the chapter compares.
  // Both Wishbone slaves are emitted: the generated code is the evidence that
  // the asynchronous and synchronous styles really are different hardware.
  emitVerilog(new wishbone.WishboneCounter(), opts)
  emitVerilog(new wishbone.WishboneCounterWait(2), opts)
  emitVerilog(new wishbone.WishboneCounterSync(), opts)
  emitVerilog(new axilite.AxiLiteCounter(), opts)

  // The bridge is emitted through the system that wires it to a device, so the
  // Wishbone master side is driven and nothing is left dangling. One
  // emitVerilog writes the whole hierarchy, so ReqAckToWishbone and
  // WishboneCounter are both inside BridgedWishboneCounter.sv.
  emitVerilog(new wishbone.BridgedWishboneCounter(), opts)

  // Appendix - full AXI4. See APPENDIX-AXI4.md.
  emitVerilog(new axi4.Axi4Memory(), opts)
  emitVerilog(new axi4.Axi4OooReadMemory(), opts)
}
