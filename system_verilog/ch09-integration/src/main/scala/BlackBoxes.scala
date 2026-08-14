import chisel3._
import chisel3.util.HasBlackBoxResource

// Section 1 - wrapping an existing SystemVerilog module.
// Port names must match the SV exactly - there is no `io_` prefix on a BlackBox.
class ExtAnd(width: Int) extends BlackBox(Map("WIDTH" -> width))
    with HasBlackBoxResource {
  val io = IO(new Bundle {
    val a = Input(UInt(width.W))
    val b = Input(UInt(width.W))
    val y = Output(UInt(width.W))
  })
  addResource("/ExtAnd.sv")
}

class UseExtAnd extends Module {
  val io = IO(new Bundle {
    val a   = Input(UInt(8.W))
    val b   = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })
  val ext = Module(new ExtAnd(8))
  ext.io.a := io.a
  ext.io.b := io.b
  io.out := ext.io.y
}
