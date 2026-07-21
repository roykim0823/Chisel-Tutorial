import chisel3._
import chisel3.util._

// ---------------------------------------------------------------------------
// A counter built from two small components: an adder and a register.
// ---------------------------------------------------------------------------

// The adder component: two 8-bit inputs, one 8-bit output.
class Adder extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val y = Output(UInt(8.W))
  })

  io.y := io.a + io.b
}

// The register component: an 8-bit register with input d and output q.
class Register extends Module {
  val io = IO(new Bundle {
    val d = Input(UInt(8.W))
    val q = Output(UInt(8.W))
  })

  val reg = RegInit(0.U)
  reg := io.d
  io.q := reg
}

// Count10 wires an Adder and a Register together to count 0..9 and wrap.
class Count10 extends Module {
  val io = IO(new Bundle {
    val dout = Output(UInt(8.W))
  })

  val add = Module(new Adder())
  val reg = Module(new Register())

  // the register output
  val count = reg.io.q
  // connect the adder
  add.io.a := 1.U
  add.io.b := count
  val result = add.io.y
  // connect the Mux and the register input
  val next = Mux(count === 9.U, 0.U, result)
  reg.io.d := next
  io.dout := count
}

// ---------------------------------------------------------------------------
// Nested components: a hierarchy CompA/CompB -> CompC, plus CompD -> TopLevel.
// The bodies are left empty on purpose; this section is about *connecting*
// components, not about their function.
// ---------------------------------------------------------------------------

class CompA extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(8.W))
    val b = Input(UInt(8.W))
    val x = Output(UInt(8.W))
    val y = Output(UInt(8.W))
  })

  // function of A
}

class CompB extends Module {
  val io = IO(new Bundle {
    val in1 = Input(UInt(8.W))
    val in2 = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  // function of B
}

class CompC extends Module {
  val io = IO(new Bundle {
    val inA = Input(UInt(8.W))
    val inB = Input(UInt(8.W))
    val inC = Input(UInt(8.W))
    val outX = Output(UInt(8.W))
    val outY = Output(UInt(8.W))
  })

  // create components A and B
  val compA = Module(new CompA())
  val compB = Module(new CompB())

  // connect A
  compA.io.a := io.inA
  compA.io.b := io.inB
  io.outX := compA.io.x
  // connect B
  compB.io.in1 := compA.io.y
  compB.io.in2 := io.inC
  io.outY := compB.io.out
}

class CompD extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(8.W))
    val out = Output(UInt(8.W))
  })

  // function of D
}

class TopLevel extends Module {
  val io = IO(new Bundle {
    val inA = Input(UInt(8.W))
    val inB = Input(UInt(8.W))
    val inC = Input(UInt(8.W))
    val outM = Output(UInt(8.W))
    val outN = Output(UInt(8.W))
  })

  // create C and D
  val c = Module(new CompC())
  val d = Module(new CompD())

  // connect C
  c.io.inA := io.inA
  c.io.inB := io.inB
  c.io.inC := io.inC
  io.outM := c.io.outX
  // connect D
  d.io.in := c.io.outY
  io.outN := d.io.out
}

// ---------------------------------------------------------------------------
// An Arithmetic Logic Unit (ALU): combinational, selected by a 2-bit fn.
// Shows the switch/is construct (needs chisel3.util._).
// ---------------------------------------------------------------------------

class Alu extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(16.W))
    val b = Input(UInt(16.W))
    val fn = Input(UInt(2.W))
    val y = Output(UInt(16.W))
  })

  // some default value is needed
  io.y := 0.U

  // The ALU selection
  switch(io.fn) {
    is(0.U) { io.y := io.a + io.b }
    is(1.U) { io.y := io.a - io.b }
    is(2.U) { io.y := io.a | io.b }
    is(3.U) { io.y := io.a & io.b }
  }
}

// ---------------------------------------------------------------------------
// Bulk connections with <>. Three pipeline stages connected by field name.
// ---------------------------------------------------------------------------

class Fetch extends Module {
  val io = IO(new Bundle {
    val instr = Output(UInt(32.W))
    val pc = Output(UInt(32.W))
  })
  // ... Implementation of fetch
}

class Decode extends Module {
  val io = IO(new Bundle {
    val instr = Input(UInt(32.W))
    val pc = Input(UInt(32.W))
    val aluOp = Output(UInt(5.W))
    val regA = Output(UInt(32.W))
    val regB = Output(UInt(32.W))
  })
  // ... Implementation of decode
}

class Execute extends Module {
  val io = IO(new Bundle {
    val aluOp = Input(UInt(5.W))
    val regA = Input(UInt(32.W))
    val regB = Input(UInt(32.W))
    val result = Output(UInt(32.W))
  })
  // ... Implementation of execute
}

class Processor extends Module {
  val io = IO(new Bundle {
    val result = Output(UInt(32.W))
  })

  val fetch = Module(new Fetch())
  val decode = Module(new Decode())
  val execute = Module(new Execute)

  fetch.io <> decode.io
  decode.io <> execute.io
  io <> execute.io
}

// ---------------------------------------------------------------------------
// The same pipeline, working under Chisel 6.
//
// The Processor above is the book's original. Its `<>` lines no longer
// elaborate under Chisel 6: the operator now requires both bundles to carry
// the *same* leaf fields, whereas older Chisel silently left unmatched names
// unconnected. `fetch.io` has {instr, pc} but `decode.io` also has
// {aluOp, regA, regB}, so `fetch.io <> decode.io` fails with
// "Left Record missing field (regB)".
//
// The idiomatic fix is to group the signals that cross each stage boundary
// into their own bundle, so every `<>` connects two bundles of identical
// shape. Trivial stage logic is added so there is something to simulate.
// ---------------------------------------------------------------------------

// Signals passed from Fetch to Decode.
class FetchDecode extends Bundle {
  val instr = UInt(32.W)
  val pc = UInt(32.W)
}

// Signals passed from Decode to Execute.
class DecodeExecute extends Bundle {
  val aluOp = UInt(5.W)
  val regA = UInt(32.W)
  val regB = UInt(32.W)
}

class Fetch6 extends Module {
  val io = IO(new Bundle {
    val out = Output(new FetchDecode)
  })
  // A real fetch reads instruction memory; here we just emit constants.
  io.out.instr := 42.U
  io.out.pc := 100.U
}

class Decode6 extends Module {
  val io = IO(new Bundle {
    val in = Input(new FetchDecode)
    val out = Output(new DecodeExecute)
  })
  // A real decode cracks the instruction; here we forward the fetched values.
  io.out.aluOp := 0.U
  io.out.regA := io.in.instr
  io.out.regB := io.in.pc
}

class Execute6 extends Module {
  val io = IO(new Bundle {
    val in = Input(new DecodeExecute)
    val result = Output(UInt(32.W))
  })
  // A real execute selects an operation with aluOp; here we simply add.
  io.result := io.in.regA + io.in.regB
}

class Processor6 extends Module {
  val io = IO(new Bundle {
    val result = Output(UInt(32.W))
  })

  val fetch = Module(new Fetch6())
  val decode = Module(new Decode6())
  val execute = Module(new Execute6())

  // Each `<>` now connects two bundles of identical shape.
  decode.io.in <> fetch.io.out    // FetchDecode   (Fetch out -> Decode in)
  execute.io.in <> decode.io.out  // DecodeExecute (Decode out -> Execute in)
  // The parent port is a single field, not the whole `execute.io` bundle
  // (which also has `in`), so a plain := is clearest here.
  io.result := execute.io.result
}
