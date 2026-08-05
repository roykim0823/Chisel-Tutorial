import chisel3._

// Summing a Vec with higher-order functions (Section 10.6). The three sums below
// are the same adder network described three ways: a named function passed to
// reduce, the same function written inline as a function literal, and the `_`
// placeholder form - the last one with reduceTree, which builds a balanced tree
// instead of a chain.
class FunctionalAdd extends Module {
  val io = IO(new Bundle {
    val in = Input(Vec(5, UInt(10.W)))
    val res = Output(UInt(10.W))
    val sumNamed = Output(UInt(10.W))
    val sumLiteral = Output(UInt(10.W))
  })

  val vec = io.in

  // (a) a named function handed to reduce: a CHAIN of adders,
  //     ((((in0 + in1) + in2) + in3) + in4).
  def add(a: UInt, b: UInt) = a + b
  val sumNamed = vec.reduce(add)

  // (b) the same combining function written inline - a function literal is
  //     (parameters) => body.
  val sumLiteral = vec.reduce((a: UInt, b: UInt) => a + b)

  // (c) `_` stands in for the two operands, and reduceTree builds a balanced
  //     TREE rather than a chain: shorter combinational delay.
  val sum = vec.reduceTree(_ + _)

  io.res := sum
  io.sumNamed := sumNamed
  io.sumLiteral := sumLiteral
}
