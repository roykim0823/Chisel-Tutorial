import chisel3._

// The simplest parameterization: a bit width passed to the module constructor.
class ParamAdder(n: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(n.W))
    val b = Input(UInt(n.W))
    val c = Output(UInt(n.W))
  })

  io.c := io.a + io.b
}

// Instantiate two differently-sized adders from the same generator.
class UseAdder extends Module {
  val io = IO(new Bundle {
    val x = Input(UInt(16.W))
    val y = Input(UInt(16.W))
    val res = Output(UInt(16.W))
  })

  val add8 = Module(new ParamAdder(8))
  val add16 = Module(new ParamAdder(16))

  add16.io.a := io.x
  add16.io.b := io.y
  io.res := add16.io.c | add8.io.c

  add8.io.a := io.x
  add8.io.b := io.y
}
