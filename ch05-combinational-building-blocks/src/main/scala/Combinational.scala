import chisel3._

// Purely combinational: a named Boolean expression, reused in another.
class Combinational extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(4.W))
    val b = Input(UInt(4.W))
    val c = Input(UInt(4.W))
    val out = Output(UInt(4.W))
    val out2 = Output(UInt(4.W))
  })

  val a = io.a
  val b = io.b
  val c = io.c

  val e = (a & b) | c   // a named Boolean expression
  val f = ~e            // reused in another expression

  io.out := e
  io.out2 := f
}

// A Wire with a default, conditionally updated by `when`. This is a mux:
// select `cond` between the default (0) and 3.
class CombWhen extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out = Output(UInt(4.W))
  })

  val cond = io.cond
  val w = Wire(UInt())

  w := 0.U
  when (cond) {
    w := 3.U
  }
  io.out := w
}

// `when`/`.otherwise`: assigning in every branch means no default is needed.
class CombOther extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out = Output(UInt(4.W))
  })

  val cond = io.cond
  val w = Wire(UInt())

  when (cond) {
    w := 1.U
  } .otherwise {
    w := 2.U
  }
  io.out := w
}

// `when`/`.elsewhen`/`.otherwise` builds a *priority* chain of multiplexers.
class CombElseWhen extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val cond2 = Input(Bool())
    val out = Output(UInt(4.W))
  })

  val cond = io.cond
  val cond2 = io.cond2

  val w = Wire(UInt())

  when (cond) {
    w := 1.U
  } .elsewhen (cond2) {
    w := 2.U
  } .otherwise {
    w := 3.U
  }
  io.out := w
}

// WireDefault combines the wire declaration with its default value.
class CombWireDefault extends Module {
  val io = IO(new Bundle {
    val cond = Input(Bool())
    val out = Output(UInt(4.W))
  })

  val cond = io.cond
  val w = WireDefault(0.U)

  when (cond) {
    w := 3.U
  }
  // ... and some more complex conditional assignments
  io.out := w
}
