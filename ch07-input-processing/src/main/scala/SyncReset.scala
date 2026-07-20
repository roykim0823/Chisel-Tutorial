import chisel3._

// A reset is also an asynchronous input, so its *release* must be synchronized.
// A top-level module synchronizes the external reset with two flip-flops and
// drives the contained module's reset input with the synchronized signal.
class SyncReset extends Module {
  val io = IO(new Bundle() {
    val value = Output(UInt(1.W))
  })

  val syncReset = RegNext(RegNext(reset))
  val cnt = Module(new WhenCounter(5))
  cnt.reset := syncReset

  io.value := cnt.io.cnt
}
