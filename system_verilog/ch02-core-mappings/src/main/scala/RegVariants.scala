import chisel3._
import chisel3.util.RegEnable

// Section 2.3 - RegNext / RegEnable. Each output is read so that none of the
// registers is deleted as dead logic.
class RegVariants extends Module {
  val io = IO(new Bundle {
    val in  = Input(UInt(8.W))
    val en  = Input(Bool())
    val o1  = Output(UInt(8.W))
    val o2  = Output(UInt(8.W))
    val o3  = Output(UInt(8.W))
  })
  val d1 = RegNext(io.in)               // 1-cycle delay, no reset value
  val d2 = RegNext(io.in, 0.U)          // 1-cycle delay, reset to 0
  val d3 = RegEnable(io.in, io.en)      // update only when en

  io.o1 := d1
  io.o2 := d2
  io.o3 := d3
}
