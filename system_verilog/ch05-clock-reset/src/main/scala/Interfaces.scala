import chisel3._
import chisel3.util._

// Section 3 - Flipped and the ready/valid handshake, and what they do to
// port directions in the emitted SystemVerilog.
class PlainIO extends Bundle {
  val data  = Output(UInt(8.W))
  val valid = Output(Bool())
  val ready = Input(Bool())
}

class FlipDemo extends Module {
  val io = IO(new Bundle {
    val producer = new PlainIO           // as declared
    val consumer = Flipped(new PlainIO)  // every direction reversed
  })
  // Flipped swapped the directions, so on `consumer` it is data/valid that are
  // inputs and ready that is an output - the mirror image of `producer`.
  io.producer.data  := io.consumer.data
  io.producer.valid := io.consumer.valid
  io.consumer.ready := io.producer.ready
}

// The standard Decoupled pattern: a one-entry skid buffer.
class DecoupledDemo extends Module {
  val io = IO(new Bundle {
    val in  = Flipped(Decoupled(UInt(8.W)))
    val out = Decoupled(UInt(8.W))
  })
  val full = RegInit(false.B)
  val data = Reg(UInt(8.W))

  io.in.ready  := !full
  io.out.valid := full
  io.out.bits  := data

  when(io.in.fire)  { data := io.in.bits; full := true.B }
  when(io.out.fire) { full := false.B }
}
