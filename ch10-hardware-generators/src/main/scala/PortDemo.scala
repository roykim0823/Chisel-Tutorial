import chisel3._
import circt.stage.ChiselStage

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

// The payload we want to route (Section 10.4, "Modules with Type Parameters"
// and "Parameterized Bundles").
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

// ------------------------------------------- 

// The book's version: `private` keeps the constructor parameter out of the
// field list Chisel builds by reflection, so the Bundle has exactly two fields.
class Port[T <: Data](private val dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}

// Same Bundle with a *public* val parameter. Chisel reflects over the public
// members whose type is Data, so `dt` silently becomes a third field.
class PortPublic[T <: Data](val dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}

// Public val AND no cloneType: `data` and `dt` are then the same object, which
// Chisel rejects outright instead of silently widening the Bundle.
class PortAliased[T <: Data](val dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt
}

// With a parameterized Bundle, address and data travel together: one vector of
// ports instead of two parallel vectors.
class NocRouter2[T <: Data](dt: T, n: Int) extends Module {
  val io = IO(new Bundle {
    val inPort = Input(Vec(n, dt))
    val outPort = Output(Vec(n, dt))
  })

  // Route the payload according to the address; again a swap stands in for the
  // real routing logic.
  io.outPort(0) := io.inPort(1)
  io.outPort(1) := io.inPort(0)
}

// The same instantiation, now wrapping the payload type in a Port.
class UseParamRouter2 extends Module {
  val io = IO(new Bundle {
    val in = Input(new Payload)
    val inAddr = Input(UInt(8.W))
    val outA = Output(new Payload)
    val outB = Output(new Payload)
  })

  val router = Module(new NocRouter2(new Port(new Payload), 2))

  // Dummy connections, so there is something to generate Verilog for.
  router.io.inPort(0).data := io.in
  router.io.inPort(0).address := io.inAddr
  router.io.inPort(1).data := io.in
  router.io.inPort(1).address := io.inAddr + 3.U
  io.outA := router.io.outPort(0).data
  io.outB := router.io.outPort(1).data
}

// Shows what the `private` in `Port` buys us.
// Run with:  sbt "runMain PortDemo"
object PortDemo extends App {

  // The field list Chisel builds for a Bundle, in declaration order.
  def fields(b: Bundle): String = b.elements.keys.mkString(", ")

  // Generate a router and keep only the module's port list, dropping the body.
  // -strip-debug-info suppresses the `// PortDemo.scala:33:14` source locators.
  def portList[T <: Data](dt: T): String = {
    val sv = ChiselStage.emitSystemVerilog(
      new NocRouter2(dt, 2), firtoolOpts = Array("-strip-debug-info"))
    val lines = sv.linesIterator.dropWhile(!_.startsWith("module")).toList
    lines.take(lines.indexWhere(_.trim.startsWith(");")) + 1).mkString("\n")
  }

  println(s"Port        fields: ${fields(new Port(new Payload))}")
  println(s"PortPublic  fields: ${fields(new PortPublic(new Payload))}")

  println("\n--- private val dt (correct) ---")
  println(portList(new Port(new Payload)))

  println("\n--- val dt (public: extra dt field) ---")
  println(portList(new PortPublic(new Payload)))

  println("\n--- val dt without cloneType (aliased) ---")
  try portList(new PortAliased(new Payload))
  catch { case e: Exception => println(s"${e.getClass.getName}: ${e.getMessage}") }
}
