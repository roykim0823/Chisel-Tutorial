/*
 * This code is a minimal hardware described in Chisel.
 *
 * Copyright: 2013, Technical University of Denmark, DTU Compute
 * Author: Martin Schoeberl (martin@jopdesign.com)
 * License: Simplified BSD License
 *
 * Blinking LED: the FPGA version of Hello World
 */

import chisel3._

/**
 * The blinking LED component.
 */
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

/**
 * An object extending App to generate the Verilog code.
 * Run with:  sbt "runMain Hello"
 * Produces:  Hello.v  in the current directory.
 */
object Hello extends App {
  emitVerilog(new Hello())
}

/**
 * Same as Hello, but writes the Verilog into a chosen output directory.
 * Run with:  sbt "runMain HelloOption"
 * Produces:  generated/Hello.v
 */
object HelloOption extends App {
  emitVerilog(new Hello(), Array("--target-dir", "generated"))
}

/**
 * Generates the Verilog and prints it to the console instead of a file.
 * Run with:  sbt "runMain HelloString"
 */
object HelloString extends App {
  val s = getVerilogString(new Hello())
  println(s)
}
