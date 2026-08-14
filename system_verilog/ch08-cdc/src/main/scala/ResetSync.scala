import chisel3._

// Section 3 - reset synchronizer: assert asynchronously, deassert synchronously.
// The reset input is asynchronous; the output deasserts only on a clock edge.
class ResetSynchronizer extends Module {
  val io = IO(new Bundle {
    val asyncResetIn = Input(AsyncReset())
    val syncResetOut = Output(Bool())
  })
  withReset(io.asyncResetIn) {
    val r1 = RegInit(true.B)
    val r2 = RegInit(true.B)
    r1 := false.B
    r2 := r1
    io.syncResetOut := r2
  }
}
