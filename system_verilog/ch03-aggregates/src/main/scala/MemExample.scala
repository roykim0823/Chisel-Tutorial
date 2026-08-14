import chisel3._

// Section 2.10 - memories.
class MemExample extends Module {
  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val raddr = Input(UInt(4.W))
    val rdata = Output(UInt(8.W))
  })
  val mem = SyncReadMem(16, UInt(8.W))   // 16 entries, synchronous read
  when(io.wen) {
    mem.write(io.waddr, io.wdata)
  }
  io.rdata := mem.read(io.raddr)
}

// The combinational-read counterpart, for contrast.
class AsyncMemExample extends Module {
  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val raddr = Input(UInt(4.W))
    val rdata = Output(UInt(8.W))
  })
  val mem = Mem(16, UInt(8.W))           // asynchronous (combinational) read
  when(io.wen) {
    mem.write(io.waddr, io.wdata)
  }
  io.rdata := mem.read(io.raddr)
}
