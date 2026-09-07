package leros.util

import leros.shared.Constants._

import scala.io.Source

// A two-pass assembler for Leros, written in plain Scala. Pass 1 collects the
// addresses of the branch destinations into a symbol table; pass 2 assembles
// the program using those symbols. It runs at hardware-generation time, from
// InstrMem's constructor, so the assembler and the hardware share one set of
// opcode constants (leros.shared.Constants).
object Assembler {

  // Destination addresses, collected in the first pass.
  val symbols = collection.mutable.Map[String, Int]()

  def getProgram(prog: String) = assemble(prog)

  def assemble(prog: String): Array[Int] = {
    symbols.clear() // one symbol table per program
    assemble(prog, false)
    assemble(prog, true)
  }

  def assemble(prog: String, pass2: Boolean): Array[Int] = {

    val source = Source.fromFile(prog)
    var program = List[Int]()
    var pc = 0

    // An integer constant, decimal or hexadecimal.
    def toInt(s: String): Int = {
      if (s.startsWith("0x")) {
        Integer.parseInt(s.substring(2), 16)
      } else {
        Integer.parseInt(s)
      }
    }

    // A register number, written r0..r255.
    def regNumber(s: String): Int = {
      assert(s.startsWith("r"), "Register numbers shall start with 'r'")
      s.substring(1).toInt
    }

    // A branch operand is a label from the symbol table (or a plain number).
    // The encoded field is the distance from the branch to its destination,
    // a signed 12-bit value.
    def branchOff(s: String): Int = {
      if (!pass2) {
        0 // destinations are not known yet in pass 1
      } else {
        val off = if (symbols.contains(s)) symbols(s) - pc else toInt(s)
        assert(off >= -2048 && off < 2048, "Branch offset out of range: " + off)
        off & 0x0fff
      }
    }

    for (line <- source.getLines()) {
      val tokens = line.trim.split(" +")
      val Pattern = "(.*:)".r
      val instr = tokens(0) match {
        case "//" => // comment
        case Pattern(l) =>
          if (!pass2) symbols += (l.substring(0, l.length - 1) -> pc)
        case "nop" => NOP << 8
        case "add" => (ADD << 8) + regNumber(tokens(1))
        case "sub" => (SUB << 8) + regNumber(tokens(1))
        case "and" => (AND << 8) + regNumber(tokens(1))
        case "or" => (OR << 8) + regNumber(tokens(1))
        case "xor" => (XOR << 8) + regNumber(tokens(1))
        case "load" => (LD << 8) + regNumber(tokens(1))
        case "store" => (ST << 8) + regNumber(tokens(1))
        case "addi" => (ADDI << 8) + toInt(tokens(1))
        case "subi" => (SUBI << 8) + toInt(tokens(1))
        case "andi" => (ANDI << 8) + toInt(tokens(1))
        case "ori" => (ORI << 8) + toInt(tokens(1))
        case "xori" => (XORI << 8) + toInt(tokens(1))
        case "loadi" => (LDI << 8) + toInt(tokens(1))
        case "loadhi" => (LDHI << 8) + toInt(tokens(1))
        case "loadh2i" => (LDH2I << 8) + toInt(tokens(1))
        case "loadh3i" => (LDH3I << 8) + toInt(tokens(1))
        case "shr" => SHR << 8
        case "sra" => SHR << 8
        case "ldaddr" => LDADDR << 8
        case "loadind" => (LDIND << 8) + toInt(tokens(1))
        case "loadindb" => (LDINDB << 8) + toInt(tokens(1))
        case "storeind" => (STIND << 8) + toInt(tokens(1))
        case "storeindb" => (STINDB << 8) + toInt(tokens(1))
        case "br" => (BR << 8) + branchOff(tokens(1))
        case "brz" => (BRZ << 8) + branchOff(tokens(1))
        case "brnz" => (BRNZ << 8) + branchOff(tokens(1))
        case "brp" => (BRP << 8) + branchOff(tokens(1))
        case "brn" => (BRN << 8) + branchOff(tokens(1))
        case "scall" => (SCALL << 8) + toInt(tokens(1))
        case "" => // empty line
        case t: String => throw new Exception("Assembler error: unknown instruction: " + t)
        case _ => throw new Exception("Assembler error")
      }
      // Labels and comments produce no instruction word.
      instr match {
        case i: Int =>
          if (pass2) program = i :: program
          pc += 1
        case _ =>
      }
    }
    source.close()
    program.reverse.toArray
  }
}
