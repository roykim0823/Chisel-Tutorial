import chisel3._
import chisel3.util._

// A single module that exercises most of Chapter 2's combinational features.
// Each output port lets a test observe the result of one construct so the
// whole chapter can be checked with a single test bench (see LogicTest).
class Logic extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(1.W))
    val b = Input(UInt(1.W))
    val c = Input(UInt(1.W))
    val out = Output(UInt(1.W))
    val cat = Output(UInt(16.W))
    val ch = Output(UInt(8.W))
    val word = Output(UInt(16.W))
    val result = Output(UInt(4.W))
  })

  // --- Types (these lines just show syntax; they create no named hardware) ---
  Bits(8.W)
  UInt(8.W)
  SInt(10.W)

  val n = 10
  n.W
  Bits(n.W)

  // --- Constants ---
  0.U  // defines a UInt constant of 0
  -3.S // defines a SInt constant of -3

  3.U(4.W) // a 4-bit constant of 3

  "hff".U        // hexadecimal representation of 255
  "o377".U       // octal representation of 255
  "b1111_1111".U // binary representation of 255

  val aChar = 'A'.U
  io.ch := aChar

  // --- Bool ---
  Bool()
  true.B
  false.B

  val a = io.a
  val b = io.b
  val c = io.c

  // --- Combinational logic: (a & b) | c ---
  val logic = (a & b) | c
  io.out := logic

  // --- Bitwise operators ---
  val a_and_b = a & b // bitwise and of a and b
  val a_or_b = a | b  // bitwise or of a and b
  val a_xor_b = a ^ b // bitwise xor of a and b
  val a_not = ~a      // bitwise negation of a

  // --- Arithmetic operators ---
  val a_plus_b = a + b  // addition of a and b
  val a_minus_b = a - b // subtraction of b from a
  val neg_a = -a        // negate a
  val a_mul_b = a * b   // multiplication of a and b
  val a_div_b = a / b   // division of a by b
  val a_mod_b = a % b   // modulo operation of a by b

  // --- Wire and the := update operator ---
  val w = Wire(UInt())
  w := a & b

  val x = 123.U
  val sign = x(31)          // single-bit extraction

  val largeWord = 1.U
  val lowByte = largeWord(7, 0) // sub-field extraction

  val highByte = 0xff.U
  val word = highByte ## lowByte // concatenation
  io.cat := word

  // --- Multiplexer ---
  val sel = b === c
  val result = Mux(sel, a, b)

  // --- Partial-assignment workaround: build a bundle, then asUInt ---
  val assignWord = Wire(UInt(16.W))

  class Split extends Bundle {
    val high = UInt(8.W)
    val low = UInt(8.W)
  }

  val split = Wire(new Split())
  split.low := lowByte
  split.high := highByte
  assignWord := split.asUInt
  io.word := assignWord

  // --- Building a UInt from individual Bools via a Vec ---
  val data = 5.U(4.W)
  val vecResult = Wire(Vec(4, Bool()))
  vecResult(0) := data(0)
  vecResult(1) := data(1)
  vecResult(2) := data(2)
  vecResult(3) := data(3)
  val uintResult = vecResult.asUInt
  io.result := uintResult
}
