import chisel3._

// Three ways to use the module `Abc` that lives in package `mypack`.

// (1) Fully-qualified name: no import, refer to it as `mypack.Abc`.
class AbcUser2 extends Module {
  val io = IO(new Bundle {})
  val abc = new mypack.Abc()
}

// (2) Import just the one class, then use its short name.
import mypack.Abc

class AbcUser3 extends Module {
  val io = IO(new Bundle {})
  val abc = new Abc()
}

// (3) Wildcard import: `_` brings in every public name from mypack.
import mypack._

class AbcUser extends Module {
  val io = IO(new Bundle {})
  val abc = new Abc()
}
