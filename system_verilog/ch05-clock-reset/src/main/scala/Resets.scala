import chisel3._

// Section 1.1 - the Chisel default: synchronous, active-high reset.
class SyncResetExample extends Module {
  val io = IO(new Bundle { val out = Output(UInt(8.W)) })
  val reg = RegInit(0.U(8.W))
  reg := reg + 1.U
  io.out := reg
}

// Section 1.2 - asynchronous reset.
class AsyncResetExample extends Module {
  val io = IO(new Bundle { val out = Output(UInt(8.W)) })
  withReset(reset.asAsyncReset) {
    val reg = RegInit(0.U(8.W))
    reg := reg + 1.U
    io.out := reg
  }
}

// Section 1.3 - a register with no reset value at all.
class NoResetExample extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val reg = Reg(UInt(8.W))   // no RegInit: no reset arm
  reg := io.in
  io.out := reg
}
