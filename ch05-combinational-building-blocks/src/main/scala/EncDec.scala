import chisel3._
import chisel3.util._   // needed for switch/is

// Decoder and encoder in one module.
//
// NOTE ON LAST-CONNECT: the decoder section assigns `result` three different
// ways (switch on UInt, switch on binary literals, and a single shift). Chisel
// uses *last-connect* semantics, so only the final assignment
// (`result := 1.U << sel`) actually drives the output — the two switch blocks
// are shown to illustrate equivalent formulations of the same truth table.
class EncDec extends Module {
  val io = IO(new Bundle {
    val decin = Input(UInt(2.W))
    val decout = Output(UInt(4.W))
    val encin = Input(UInt(4.W))
    val encout = Output(UInt(2.W))
    val largeEncIn = Input(UInt(16.W))
    val largeEncOut = Output(UInt(4.W))
  })

  val sel = io.decin
  val result = Wire(UInt(4.W))

  // (a) truth table with a switch (a default is still required)
  result := 0.U
  switch(sel) {
    is (0.U) { result := 1.U }
    is (1.U) { result := 2.U }
    is (2.U) { result := 4.U }
    is (3.U) { result := 8.U }
  }

  // (b) the same, using binary literals for a clearer picture
  switch (sel) {
    is ("b00".U) { result := "b0001".U }
    is ("b01".U) { result := "b0010".U }
    is ("b10".U) { result := "b0100".U }
    is ("b11".U) { result := "b1000".U }
  }

  // (c) the compact form: shift a 1 left by sel (this is what actually drives)
  result := 1.U << sel

  io.decout := result

  // Encoder: one-hot input -> binary output (default catches illegal inputs).
  val a = io.encin
  val b = Wire(UInt(2.W))
  b := "b00".U
  switch (a) {
    is ("b0001".U) { b := "b00".U }
    is ("b0010".U) { b := "b01".U }
    is ("b0100".U) { b := "b10".U }
    is ("b1000".U) { b := "b11".U }
  }
  io.encout := b

  // A generated 16-bit encoder using a Scala for-loop and OR-reduction.
  val hotIn = io.largeEncIn
  val v = Wire(Vec(16, UInt(4.W)))
  v(0) := 0.U
  for (i <- 1 until 16) {
    v(i) := Mux(hotIn(i), i.U, 0.U) | v(i - 1)
  }
  val encOut = v(15)
  io.largeEncOut := encOut
}
