import chisel3._

// Section 1 - printf, unconditional and conditional.
class PrintfExample extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val cnt = RegInit(0.U(8.W))
  cnt := cnt + 1.U
  io.out := cnt + io.in

  printf("cnt=%d in=%d\n", cnt, io.in)          // every cycle
  when(cnt === 3.U) {
    printf(p"reached three: cnt=$cnt\n")        // guarded by a when
  }
}
