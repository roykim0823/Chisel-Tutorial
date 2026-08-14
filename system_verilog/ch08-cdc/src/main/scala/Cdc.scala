import chisel3._

// Section 2 - the two-flop synchronizer for a single-bit signal.
// The whole point is the pair of back-to-back registers in the DESTINATION
// domain; the first may go metastable, the second is very likely settled.
class TwoFlopSync extends Module {
  val io = IO(new Bundle {
    val async = Input(Bool())    // from another clock domain
    val sync  = Output(Bool())   // safe in this domain
  })
  val meta   = RegNext(io.async, false.B)
  val stable = RegNext(meta, false.B)
  io.sync := stable
}

// A multi-bit bus through flop synchronizers - the WRONG thing to do, kept
// here so its generated form can be compared with the single-bit version.
class BadBusSync extends Module {
  val io = IO(new Bundle {
    val async = Input(UInt(8.W))
    val sync  = Output(UInt(8.W))
  })
  io.sync := RegNext(RegNext(io.async, 0.U), 0.U)
}
