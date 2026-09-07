package leros

import leros.util.Assembler
import org.scalatest.flatspec.AnyFlatSpec

// The assembler is plain Scala, so it needs no hardware simulation to test:
// assemble a program and compare the instruction words.
class AssemblerTest extends AnyFlatSpec {

  // The hand-encoded program of Section 14.6.
  val fixed = Array[Int](
    0x0903, // addi 0x3
    0x09ff, // -1
    0x0d02, // subi 2
    0x21ab, // ldi 0xab
    0x230f, // and 0x0f
    0x25c3, // or 0xc3
    0x0000
  )

  "The assembler" should "reproduce the hand-encoded program" in {
    assert(Assembler.getProgram("asm/fixed.s").toSeq == fixed.toSeq)
  }

  it should "resolve backward and forward branches" in {
    val code = Assembler.getProgram("asm/branch.s")
    // brnz loop: the branch is at 2, its destination at 1, so the offset is -1
    // in the low 12 bits of a 0xa0.. opcode.
    assert(code(2) == 0xa000 + (-1 & 0xfff))
    // brz cont: the branch is at 3, `cont` is at 6, so the offset is +3.
    assert(code(3) == 0x9000 + 3)
    // Labels themselves occupy no instruction word.
    assert(code.length == 16)
  }

  it should "reject an unknown mnemonic" in {
    val bad = java.io.File.createTempFile("leros", ".s")
    java.nio.file.Files.write(bad.toPath, "movl 1\n".getBytes)
    val e = intercept[Exception] { Assembler.getProgram(bad.getPath) }
    assert(e.getMessage.contains("unknown instruction: movl"))
    bad.delete()
  }
}
