import chisel3._
import chisel3.util._

// A multi-clock memory: one memory, several ports, each clocked by its own
// clock. The memory itself is created outside any withClock block; each port's
// access logic goes inside its own withClock block.
class MemoryIO(val n: Int, val w: Int) extends Bundle {
  val clk   = Input(Bool())
  val addr  = Input(UInt(log2Up(n).W))
  val datai = Input(UInt(w.W))
  val datao = Output(UInt(w.W))
  val en    = Input(Bool())
  val we    = Input(Bool())
}

class MultiClockMemory(ports: Int, n: Int = 1024, w: Int = 32) extends Module {
  val io = IO(new Bundle {
    val ps = Vec(ports, new MemoryIO(n, w))
  })

  val ram = SyncReadMem(n, UInt(w.W))   // the memory: outside every withClock block

  for (i <- 0 until ports) {
    val p = io.ps(i)
    val clk = p.clk.asClock
    withClock(clk) {                    // this port's own withClock block
      val datao = WireDefault(0.U(w.W))
      when(p.en) {
        // Chisel 6 wants the port's clock passed explicitly - see the note in
        // the chapter README; ram(p.addr) alone is deprecated here.
        datao := ram.read(p.addr, clk)
        when(p.we) {
          ram.write(p.addr, p.datai, clk)
        }
      }
      p.datao := datao
    }
  }
}
