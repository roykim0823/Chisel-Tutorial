// A runnable tour of the Scala the rest of this tutorial relies on.
// Every numbered block matches a subsection of README.md section 1.2.
// This file is pure Scala: there is no Chisel and no hardware here.

object ScalaIntro extends App {

  def section(n: String, title: String): Unit =
    println(s"\n--- $n $title " + "-" * (46 - title.length))

  // 1.2.1 val vs var
  section("1.2.1", "val vs var")
  val fixed = 42
  var running = 0
  running += 1
  running += 1
  println(s"fixed = $fixed (can never be reassigned)")
  println(s"running = $running (reassigned twice)")

  // 1.2.2 Type inference
  section("1.2.2", "type inference")
  val inferred = 42          // Int, inferred
  val stated: Int = 42       // the same thing, spelled out
  val text = "forty-two"     // String, inferred
  println(s"inferred=$inferred stated=$stated text=$text")

  // 1.2.3 Literals
  section("1.2.3", "literals")
  val hex = 0x2a
  val big = 0x00ffffffffL
  val ch = 'A'
  val sep = 1_000_000
  val dbl = 100.0
  println(s"hex=$hex big=$big char=$ch separated=$sep double=$dbl")

  // 1.2.4 Everything is an expression
  section("1.2.4", "if and blocks are expressions")
  val parity = if (fixed % 2 == 0) "even" else "odd"
  val blockValue = {
    val a = 3
    val b = 4
    a * a + b * b        // the block's value is its last expression
  }
  println(s"parity=$parity blockValue=$blockValue")

  // 1.2.5 def methods
  section("1.2.5", "def methods")
  def square(x: Int): Int = x * x
  def shout(msg: String): Unit = println(msg.toUpperCase)
  println(s"square(7) = ${square(7)}")
  shout("methods end with the value of their body")

  // 1.2.6 Named and default arguments
  section("1.2.6", "named and default arguments")
  def uart(frequency: Int, baudRate: Int = 115200): String =
    s"frequency=$frequency baudRate=$baudRate"
  println(uart(50000000))                              // default baud rate
  println(uart(baudRate = 10, frequency = 1000))       // named, reordered

  // 1.2.7 Classes, new, extends
  section("1.2.7", "classes, new, extends")
  class Greeter(name: String) {
    def greet(): String = s"Hello, $name!"
  }
  class LoudGreeter(name: String) extends Greeter(name) {
    override def greet(): String = super.greet().toUpperCase
  }
  println(new Greeter("Scala").greet())
  println(new LoudGreeter("Chisel").greet())

  // 1.2.8 object: the singleton
  section("1.2.8", "object as singleton")
  println(f"Constants.NOP = 0x${Constants.NOP}%02x (one shared instance, no `new`)")
  println("and ScalaIntro itself is an object too - that is why it can be run")

  // 1.2.9 Operators are method calls
  section("1.2.9", "operators are method calls")
  println(s"1 + 2      = ${1 + 2}")
  println(s"1.+(2)     = ${1.+(2)}")
  println(s"6 & 3      = ${6 & 3}   (6.&(3))")
  println(s"'x' concat = ${"ab".concat("cd")} == ${"ab" concat "cd"}")

  // 1.2.10 apply
  section("1.2.10", "apply, the omitted method name")
  val squares = Seq(1, 4, 9, 16)
  println(s"squares(2)        = ${squares(2)}")
  println(s"squares.apply(2)  = ${squares.apply(2)}   (identical)")
  val double = (x: Int) => x * 2
  println(s"double(21)        = ${double(21)} == double.apply(21) = ${double.apply(21)}")

  // 1.2.11 Collections
  section("1.2.11", "Seq, List, Array")
  val numbers = Seq(1, 15, -2, 0)
  val zeros = Seq.fill(4)(0)
  val boxes = Array.fill(3) { new Greeter("box") }
  println(s"numbers = $numbers, numbers(1) = ${numbers(1)}")
  println(s"Seq.fill(4)(0) = $zeros")
  println(s"Array.fill(3){...} made ${boxes.length} distinct objects")

  // 1.2.12 Ranges and for
  section("1.2.12", "ranges and for")
  println(s"0 until 4 = ${(0 until 4).toList}   (exclusive)")
  println(s"0 to 4    = ${(0 to 4).toList}   (inclusive)")
  for (i <- 1 until 4) println(s"  iteration i = $i")

  // 1.2.13 while
  section("1.2.13", "while")
  var countdown = 3
  while (countdown > 0) {
    println(s"  countdown = $countdown")
    countdown -= 1
  }

  // 1.2.14 Tuples
  section("1.2.14", "tuples")
  val city = (2000, "Frederiksberg")
  println(s"city._1 = ${city._1}, city._2 = ${city._2}")
  val (zipCode, name) = city          // destructuring
  println(s"destructured: zipCode=$zipCode name=$name")

  // 1.2.15 String interpolation
  section("1.2.15", "string interpolation")
  val n = 7
  println(s"s: $n squared is ${square(n)}")
  println(f"f: pi is about ${math.Pi}%.3f")
  println(raw"raw: a backslash-n stays literal: \n")

  // 1.2.16 println and the standard library
  section("1.2.16", "the standard library")
  val rnd = new scala.util.Random(1)   // fixed seed: reproducible output
  println(s"three random ints: ${rnd.nextInt(100)}, ${rnd.nextInt(100)}, ${rnd.nextInt(100)}")

  println("\nDone. Now go build some hardware.")
}

// A companion-free namespace object: constants and helper defs live in one of
// these because Scala 2 has no top-level `def` or `val`.
object Constants {
  val NOP = 0x00
}
