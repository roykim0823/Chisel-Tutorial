import chisel3._

// Section 1.1 - how Chisel val names reach the generated SystemVerilog.
class Naming extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  val namedWire = io.a & io.b        // a named val
  val namedReg  = RegNext(namedWire) // a named register

  // An anonymous intermediate: nothing names (a|b), so it has no name to keep.
  io.out := namedReg | (io.a | io.b)
}

// Section 1.3 - the name-control APIs.
class NameControl extends Module {
  override val desiredName = "RenamedByDesiredName"
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val plain = RegNext(io.in)
  val hinted = RegNext(io.in).suggestName("chosen_name")
  io.out := plain | hinted
}
