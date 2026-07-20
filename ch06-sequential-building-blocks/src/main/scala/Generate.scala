import chisel3._

// Emit SystemVerilog for a representative set of this chapter's modules.
// Run with:  sbt "runMain Generate"
object Generate extends App {
  emitVerilog(new Registers())
  emitVerilog(new WhenCounter(10))
  emitVerilog(new Timer())
  emitVerilog(new Pwm())
  emitVerilog(new ShiftRegister())
  emitVerilog(new Memory())
  emitVerilog(new ForwardingMemory())
}
