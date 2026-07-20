import chisel3._
import chisel3.util._

// A priority arbiter grants exactly one request; lower bit = higher priority.
// e.g. request 0101 -> grant 0001.

// Version 1: the chain written out by hand for 3 requests.
class Arbiter3 extends Module {
  val io = IO(new Bundle {
    val request = Input(UInt(3.W))
    val grant = Output(UInt(3.W))
  })

  val request = VecInit(io.request.asBools)

  val grant = VecInit(false.B, false.B, false.B)
  val notGranted = VecInit(false.B, false.B)

  grant(0) := request(0)
  notGranted(0) := !grant(0)
  grant(1) := request(1) && notGranted(0)
  notGranted(1) := !grant(1) && notGranted(0)
  grant(2) := request(2) && notGranted(1)

  io.grant := grant.asUInt
}

// Version 2: the same function written directly as a truth table.
class Arbiter3Direct extends Module {
  val io = IO(new Bundle {
    val request = Input(UInt(3.W))
    val grant = Output(UInt(3.W))
  })

  val request = io.request

  val grant = WireDefault("b0000".U(3.W))
  switch (request) {
    is ("b000".U) { grant := "b000".U }
    is ("b001".U) { grant := "b001".U }
    is ("b010".U) { grant := "b010".U }
    is ("b011".U) { grant := "b001".U }
    is ("b100".U) { grant := "b100".U }
    is ("b101".U) { grant := "b001".U }
    is ("b110".U) { grant := "b010".U }
    is ("b111".U) { grant := "b001".U }
  }
  io.grant := grant
}

// Version 3: parameterized generator — the chain built with a for-loop.
class Arbiter3Loop extends Module {
  val io = IO(new Bundle {
    val request = Input(UInt(3.W))
    val grant = Output(UInt(3.W))
  })

  val request = VecInit(io.request.asBools)
  val n = request.length

  val grant = VecInit.fill(n)(false.B)
  val notGranted = VecInit.fill(n)(false.B)

  grant(0) := request(0)
  notGranted(0) := !grant(0)
  for (i <- 1 until n) {
    grant(i) := request(i) && notGranted(i - 1)
    notGranted(i) := !grant(i) && notGranted(i - 1)
  }

  io.grant := grant.asUInt
}
