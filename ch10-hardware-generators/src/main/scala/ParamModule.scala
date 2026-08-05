import chisel3._

// Parameterizing a MODULE by a Chisel TYPE (Section 10.4, "Modules with Type
// Parameters"). The companion file ParamBundle.scala takes the next step and
// parameterizes a Bundle instead.
//
// A module parameterized by a Chisel TYPE and by a port count: the payload type
// dt is whatever the user passes in. Address and data travel in two separate
// parallel vectors.
class NocRouter[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val address = Input(Vec(n, UInt(8.W)))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address; a plain swap of the two ports
  // stands in for real routing here, just enough to elaborate (n = 2).
  io.outPort(0) := io.inPort(1)
  io.outPort(1) := io.inPort(0)
}

// The payload we want to route. Also used by ParamBundle.scala - everything here
// is in Scala's default package, so no import is needed.
class Payload extends Bundle {
  val data = UInt(16.W)
  val flag = Bool()
}

// Instantiating the router: pass an instance of the payload Bundle plus the
// number of ports.
class UseParamRouter extends Module {
  val io = IO(new Bundle {
    val in = Input(new Payload)
    val inAddr = Input(UInt(8.W))
    val outA = Output(new Payload)
    val outB = Output(new Payload)
  })

  val router = Module(new NocRouter(new Payload, 2))

  // Dummy connections, so there is something to generate Verilog for.
  router.io.inPort(0) := io.in
  router.io.address(0) := io.inAddr
  router.io.inPort(1) := io.in
  router.io.address(1) := io.inAddr + 3.U
  io.outA := router.io.outPort(0)
  io.outB := router.io.outPort(1)
}
