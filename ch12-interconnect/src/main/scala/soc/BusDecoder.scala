package soc

import chisel3._
import chisel3.util.log2Ceil

// The on-chip bus of Figure 12.2, with the handshaking left out: an address
// decoder turns the upper address bits into one-hot chip selects, and that same
// selection drives the read multiplexer which replaces the off-chip tri-state
// data bus.
//
// There is deliberately no state here. This is the *wiring* of Figure 12.2 --
// who is selected and whose data comes back -- not a protocol. Handshaking, and
// therefore the question of when a transfer completes, arrives in Section 12.2.
class BusDecoder(val devices: Int = 4, val addrWidth: Int = 8,
                 val deviceBytes: Int = 16) extends Module {
  require(devices >= 2, "a decoder needs at least two devices to choose between")

  private val lo = log2Ceil(deviceBytes)
  private val sel = log2Ceil(devices)
  require(addrWidth >= lo + sel,
    s"$addrWidth address bits cannot select $devices devices of $deviceBytes bytes")

  val io = IO(new Bundle {
    val address = Input(UInt(addrWidth.W))
    val deviceRdData = Input(Vec(devices, UInt(32.W)))  // one input per device
    val cs = Output(Vec(devices, Bool()))               // chip selects
    val rdData = Output(UInt(32.W))                     // the read mux output
  })

  // Each device owns `deviceBytes` of the address space, so the bits below that
  // window address *within* a device, and only the bits above it choose one.
  private val index = io.address(lo + sel - 1, lo)

  for (i <- 0 until devices) {
    io.cs(i) := index === i.U
  }
  io.rdData := io.deviceRdData(index)
}
