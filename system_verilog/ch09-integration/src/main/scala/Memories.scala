import chisel3._

// Section 2 - the memory that becomes an SRAM macro.
class Sram extends Module {
  val io = IO(new Bundle {
    val wen   = Input(Bool())
    val waddr = Input(UInt(4.W))
    val wdata = Input(UInt(8.W))
    val raddr = Input(UInt(4.W))
    val rdata = Output(UInt(8.W))
  })
  val mem = SyncReadMem(16, UInt(8.W))
  when(io.wen) { mem.write(io.waddr, io.wdata) }
  io.rdata := mem.read(io.raddr)
}
