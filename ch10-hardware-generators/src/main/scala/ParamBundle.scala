import chisel3._
import circt.stage.ChiselStage

// Parameterizing a BUNDLE by a Chisel TYPE (Section 10.4, "Parameterized
// Bundles"). The router in ParamModule.scala needs two parallel vectors for
// address and data; a parameterized Bundle carries both in one vector.
//
// `Payload` comes from ParamModule.scala (same default package, no import).

// The type parameter is written without a `val`, so it never becomes a public
// member - Chisel builds a Bundle's field list by reflecting over the public
// members whose type is Data, and this Bundle has exactly two of them.
class Port[T <: Data](dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}

// The book's spelling. `private val` keeps `dt` as a real field of the class -
// so the Bundle's own methods can read it, even off another instance, which a
// bare private[this] parameter does not allow - while still keeping it out of
// the PUBLIC members Chisel reflects over. Same two fields as `Port`.
class PortPrivate[T <: Data](private val dt: T) extends Bundle {
  val address = UInt(8.W)
  val data = dt.cloneType
}

// The same Bundle with a PUBLIC val parameter: Chisel reflects over the public
// members whose type is Data, so `dt` silently becomes a third field. This is
// the only one of the three spellings that does damage.
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

// The same instantiation as UseParamRouter, now wrapping the payload type in a
// Port. The near-copy is deliberate: the two wrappers side by side are what make
// the "two parallel vectors" and "one parameterized Bundle" designs comparable.
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

// Shows what keeping the type parameter out of the public members buys us.
// Run with:  sbt "runMain PortDemo"
object PortDemo extends App {

  // The field list Chisel builds for a Bundle, in declaration order.
  def fields(b: Bundle): String = b.elements.keys.mkString(", ")

  // Generate a router and keep only the module's port list, dropping the body.
  // -strip-debug-info suppresses the `// ParamBundle.scala:33:14` source locators.
  def portList[T <: Data](dt: T): String = {
    // Emit SystemVerilog for a router with the given payload type
    val sv = ChiselStage.emitSystemVerilog(
      new NocRouter2(dt, 2), firtoolOpts = Array("-strip-debug-info"))

    // Drop everything before the first `module` and after the closing `);` of the
    // port list. The rest is the module's port list, which is what we want to see.
    val lines = sv.linesIterator.dropWhile(!_.startsWith("module")).toList
    lines.take(lines.indexWhere(_.trim.startsWith(");")) + 1).mkString("\n")
  }

  println(s"Port         (dt, no val)      fields: ${fields(new Port(new Payload))}")
  println(s"PortPrivate  (private val dt)  fields: ${fields(new PortPrivate(new Payload))}")
  println(s"PortPublic   (val dt)          fields: ${fields(new PortPublic(new Payload))}")

  println("\n--- dt without val (correct) ---")
  println(portList(new Port(new Payload)))

  println("\n--- dt with private val (correct) ---")
  println(portList(new PortPrivate(new Payload)))

  println("\n--- val dt (public: extra dt field) ---")
  println(portList(new PortPublic(new Payload)))

  println("\n--- val dt without cloneType (aliased) ---")
  try portList(new PortAliased(new Payload))
  catch { case e: Exception => println(s"${e.getClass.getName}: ${e.getMessage}") }
}
