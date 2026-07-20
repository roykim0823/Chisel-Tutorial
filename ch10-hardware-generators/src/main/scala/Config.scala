// A case class is a lightweight way to package several parameters. Fields are
// immutable and read by name. Great for passing a config through constructors.
case class Config(txDepth: Int, rxDepth: Int, width: Int)

// A case class can also validate its parameters.
case class SaveConf(txDepth: Int, rxDepth: Int, width: Int) {
  assert(txDepth > 0 && rxDepth > 0 && width > 0, "parameters must be larger than 0")
}

// Run with:  sbt "runMain ConfigDemo"
object ConfigDemo extends App {
  val param = Config(4, 2, 16)
  println("The width is " + param.width)
}
