import chisel3._

// Chapter 2's Bundle and Vec constructs, gathered into one module whose ports
// let a test (and the generated SystemVerilog) observe each one. Same idea as
// Logic.scala, which does that for the types, operators, and multiplexer.

class Channel() extends Bundle {
  val data = UInt(32.W)
  val valid = Bool()
}

class BundleVec extends Bundle {
  val field = UInt(8.W)
  val vector = Vec(4, UInt(8.W))
}

class Structure extends Module {
  val io = IO(new Bundle {
    val x = Input(UInt(8.W))
    val y = Input(UInt(8.W))
    val z = Input(UInt(8.W))
    val d = Input(UInt(3.W))
    val e = Input(UInt(3.W))
    val f = Input(UInt(3.W))
    val sel = Input(UInt(2.W))
    val bIdx = Input(UInt(3.W)) // wide enough to index the 8-element Vec below
    val cond = Input(Bool())
    val chData = Output(UInt(32.W))
    val chValid = Output(Bool())
    val vOut = Output(UInt(4.W))
    val muxOut = Output(UInt(8.W))
    val vecOut = Output(UInt(3.W))
    val vecOutSig = Output(UInt(3.W))
    val bundleVecOut = Output(UInt(8.W))
    val regData = Output(UInt(32.W))
    val initOut = Output(UInt(3.W))
  })

  val x = io.x
  val y = io.y
  val z = io.z
  val d = io.d
  val e = io.e
  val f = io.f
  val sel = io.sel
  val select = io.sel
  val cond = io.cond

  // --- Bundle: create it, drive its fields, read one field back ---
  val ch = Wire(new Channel())
  ch.data := 123.U
  ch.valid := true.B

  val b = ch.valid

  // --- A bundle can be referenced as a whole ---
  val channel = ch
  io.chData := channel.data
  io.chValid := b

  // --- Vec in a Wire: assign elements, then index with a signal (a mux) ---
  val v = Wire(Vec(3, UInt(4.W)))

  v(0) := 1.U
  v(1) := 3.U
  v(2) := 5.U

  val index = 1.U(2.W)
  val a = v(index) // dynamic index = a multiplexer
  io.vOut := a

  // --- Three wires collected into a Vec: literally a multiplexer ---
  val m = Wire(Vec(3, UInt(8.W)))
  m(0) := x
  m(1) := y
  m(2) := z
  val muxOut = m(select)
  io.muxOut := muxOut

  // --- VecInit: a Vec with defaults, already hardware (no Wire needed) ---
  val defVec = VecInit(1.U(3.W), 2.U, 3.U)
  when(cond) {
    defVec(0) := 4.U
    defVec(1) := 5.U
    defVec(2) := 6.U
  }
  val vecOut = defVec(sel)
  io.vecOut := vecOut

  // --- VecInit fed with signals rather than constants ---
  val defVecSig = VecInit(d, e, f)
  val vecOutSig = defVecSig(sel)
  io.vecOutSig := vecOutSig

  // --- A Vec of a Bundle type ---
  val vecBundle = Wire(Vec(8, new Channel()))
  for (i <- 0 until 8) {
    vecBundle(i) := ch
  }

  // --- A Bundle containing a Vec field ---
  val bundleVec = Wire(new BundleVec())
  bundleVec.field := x
  bundleVec.vector := VecInit(x, y, z, x)
  io.bundleVecOut := bundleVec.vector(sel)

  // --- A register of a bundle type, with a reset value built in a Wire ---
  val initVal = Wire(new Channel())

  initVal.data := 0.U
  initVal.valid := false.B

  val channelReg = RegInit(initVal)
  channelReg := vecBundle(io.bIdx)
  io.regData := channelReg.data

  // --- A Vec of registers with distinct reset values ---
  val initReg = RegInit(VecInit(0.U(3.W), 1.U, 2.U))
  val resetVal = initReg(sel)
  initReg(0) := d
  initReg(1) := e
  initReg(2) := f
  io.initOut := resetVal
}
