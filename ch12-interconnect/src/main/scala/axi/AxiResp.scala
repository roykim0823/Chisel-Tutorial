package axi

import chisel3._

// The AXI response codes, carried on the B (write response) and R (read data)
// channels. They are identical in AXI4-Lite and in full AXI4, which is why they
// live here rather than in either protocol's package: `axilite` and `axi4` both
// use this one definition, so the two cannot drift apart.
object AxiResp {
  val okay = 0.U(2.W)
  val exOkay = 1.U(2.W)     // exclusive access; unused by AXI4-Lite
  val slvErr = 2.U(2.W)     // the slave itself failed the transfer
  val decErr = 3.U(2.W)     // no slave at that address
}
