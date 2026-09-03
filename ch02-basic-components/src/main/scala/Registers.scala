import chisel3._

// Chapter 2's register forms and the counting example, plus the "give it a
// default / give it a reset value" best practices from §2.8. Split into two
// small modules so each construct keeps the book's own name.

class Registers extends Module {
  val io = IO(new Bundle {
    val d = Input(UInt(8.W))
    val q = Output(UInt(8.W))
    val next = Output(UInt(8.W))
    val nextInit = Output(UInt(8.W))
    val cnt = Output(UInt(8.W))
  })

  val d = io.d

  // --- The standard register forms ---
  val reg = RegInit(0.U(8.W)) // 8-bit register, resets to 0
  reg := d                    // drive its input; read it just by name (reg)

  val r2 = RegNext(d)         // register whose input is d (no reset value)
  val r3 = RegNext(d, 0.U)    // input d, resets to 0

  io.q := reg
  io.next := r2
  io.nextInit := r3

  // --- Counting: 0 to 9, then wrap ---
  val cntReg = RegInit(0.U(8.W))
  cntReg := cntReg + 1.U
  when(cntReg === 9.U) {
    cntReg := 0.U
  }

  io.cnt := cntReg
}

class Defaults extends Module {
  val io = IO(new Bundle {
    val number = Output(UInt(4.W))
    val reg = Output(SInt(8.W))
  })

  // A combinational Wire with a default value, so it is driven on every path.
  val number = WireDefault(10.U(4.W))

  // A register with a reset value, so simulation and verification start from a
  // known state.
  val reg = RegInit(0.S(8.W))

  io.number := number
  io.reg := reg
}
