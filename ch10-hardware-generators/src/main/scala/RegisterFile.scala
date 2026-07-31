import chisel3._

// Optional ports: a register file for a 32-bit RISC processor with a debug port
// that exposes ALL registers. The port is handy in the tester but expensive in
// the final design, so a Scala Boolean decides whether it exists at all.
//
// The trick is Scala's Option: `Some(port)` when debug is on, `None` when it is
// off. Because the decision is made during elaboration, a `None` port leaves no
// trace whatsoever in the generated Verilog.
class RegisterFile(debug: Boolean) extends Module {
  val io = IO(new Bundle {
    val rs1 = Input(UInt(5.W))
    val rs2 = Input(UInt(5.W))
    val rd = Input(UInt(5.W))
    val wrData = Input(UInt(32.W))
    val wrEna = Input(Bool())
    val rs1Val = Output(UInt(32.W))
    val rs2Val = Output(UInt(32.W))
    val dbgPort = if (debug)
      Some(Output(Vec(32, UInt(32.W)))) else None
  })
  val regfile = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  io.rs1Val := regfile(io.rs1)
  io.rs2Val := regfile(io.rs2)
  when(io.wrEna) {
    regfile(io.rd) := io.wrData
  }
  // The port is unwrapped with .get - only ever reached when it exists.
  if (debug) {
    io.dbgPort.get := regfile
  }
}
