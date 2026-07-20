package uart

import chisel3._

// Tutorial addition (not from the book): wire a Tx directly into an Rx so we
// can test the serial link end-to-end in simulation. Feed a byte into `in`,
// and it comes out of `out` after being serialized and deserialized.
class UartLoopback(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(new UartIO())
    val out = new UartIO()
  })
  val tx = Module(new Tx(frequency, baudRate))
  val rx = Module(new Rx(frequency, baudRate))

  tx.io.channel <> io.in       // producer (test) -> Tx
  rx.io.rxd := tx.io.txd       // the serial wire, looped back
  io.out <> rx.io.channel      // Rx -> consumer (test)
}
