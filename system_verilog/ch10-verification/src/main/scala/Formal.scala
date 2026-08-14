import chisel3._
import chisel3.ltl._
import chisel3.ltl.Sequence.BoolSequence

// A small request/grant arbiter carrying formal properties.
class ReqGrant extends Module {
  val io = IO(new Bundle {
    val req   = Input(Bool())
    val grant = Output(Bool())
  })
  val busy = RegInit(false.B)
  when(io.req && !busy) { busy := true.B }
    .elsewhen(busy)     { busy := false.B }
  io.grant := busy

  // An immediate assertion: checked every cycle.
  assert(!(io.grant && !busy), "grant only while busy")

  // A cover point: did we ever actually grant?
  cover(io.grant)

  // A multi-cycle SVA property: a request implies a grant 1 to 2 cycles later.
  AssertProperty(io.req.implication(io.grant.delayRange(1, 2)))

  // An assumption constrains the environment rather than checking the design.
  AssumeProperty(io.req | !io.req)
}
