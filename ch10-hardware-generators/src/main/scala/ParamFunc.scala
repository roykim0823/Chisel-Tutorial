import chisel3._

// A user-defined bundle type, used to show type-parameterized muxing.
class ComplexIO extends Bundle {
  val d = UInt(10.W)
  val b = Bool()
}

class ParamFunc extends Module {
  val io = IO(new Bundle {
    val selA = Input(Bool())
    val resA = Output(UInt(5.W))
    val selB = Input(Bool())
    val resB = Output(new ComplexIO())
  })

  // A multiplexer parameterized by a Chisel TYPE: [T <: Data] accepts any
  // Chisel type (Data is the root of the type system). Same function works for
  // a UInt or a whole Bundle.
  def myMux[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
    val ret = WireDefault(fPath)
    when (sel) {
      ret := tPath
    }
    ret
  }

  // Alternative using fPath.cloneType when no default value is wanted.
  def myMuxAlt[T <: Data](sel: Bool, tPath: T, fPath: T): T = {
    val ret = Wire(fPath.cloneType)
    ret := fPath
    when (sel) {
      ret := tPath
    }
    ret
  }

  // Use with a simple UInt type.
  val resA = myMux(io.selA, 5.U, 10.U)

  // Use with a complex Bundle type (build Bundle constants with a Wire).
  val tVal = Wire(new ComplexIO)
  tVal.b := true.B
  tVal.d := 42.U
  val fVal = Wire(new ComplexIO)
  fVal.b := false.B
  fVal.d := 13.U
  val resB = myMux(io.selB, tVal, fVal)

  io.resA := resA
  io.resB := resB
}
