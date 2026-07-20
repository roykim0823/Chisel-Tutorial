/*
 * The blinking LED from Chapter 1, reused here to demonstrate the build/
 * generate/tool-flow steps of Chapter 3.
 *
 * Copyright: 2013, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 */

import chisel3._

class Hello extends Module {
  val io = IO(new Bundle {
    val led = Output(UInt(1.W))
  })
  val CNT_MAX = (50000000 / 2 - 1).U

  val cntReg = RegInit(0.U(32.W))
  val blkReg = RegInit(0.U(1.W))

  cntReg := cntReg + 1.U
  when(cntReg === CNT_MAX) {
    cntReg := 0.U
    blkReg := ~blkReg
  }
  io.led := blkReg
}

// Generate SystemVerilog into the project root:  sbt "runMain Hello"
object Hello extends App {
  emitVerilog(new Hello())
}

// Generate into a chosen folder:  sbt "runMain HelloOption"  -> generated/Hello.sv
object HelloOption extends App {
  emitVerilog(new Hello(), Array("--target-dir", "generated"))
}

// Print the SystemVerilog to the console:  sbt "runMain HelloString"
object HelloString extends App {
  val s = getVerilogString(new Hello())
  println(s)
}
