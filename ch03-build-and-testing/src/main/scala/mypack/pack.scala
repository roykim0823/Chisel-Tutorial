// A module placed in the package `mypack`. Because it declares `package mypack`
// and lives in the folder src/main/scala/mypack/, its fully-qualified name is
// `mypack.Abc`. Packages give your Chisel code namespaces (like Java/Scala).
package mypack

import chisel3._

class Abc extends Module {
  val io = IO(new Bundle {})
}
