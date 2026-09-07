package wildcat.pipeline

import chisel3._
import chisel3.util._

// A small on-chip data memory (scratchpad) for the data-memory port: four
// byte-wide SyncReadMems, one per byte lane, so a store can write single bytes
// (sb) or a half word (sh) without a read-modify-write. WriteFirst forwards a
// read-during-write, and the registered address gives the same one-cycle read
// latency as the instruction ROM - that latency is the execute stage's
// pipeline register.
// Upstream Wildcat's ScratchPadMem additionally preloads its content from hex
// files; this one starts empty, which is all the tutorial's programs need.
class ScratchPadMem(nrBytes: Int = 4096) extends Module {
  val io = IO(Flipped(new MemIO()))

  val nrWords = nrBytes / 4
  val mems = Seq.fill(4)(SyncReadMem(nrWords, UInt(8.W), SyncReadMem.WriteFirst))

  // Word index: drop the two byte-select bits, keep log2Up(nrWords) bits.
  val hi = log2Up(nrWords) + 1
  val rdWord = io.rdAddress(hi, 2)
  val wrWord = io.wrAddress(hi, 2)

  io.rdData := mems(3).read(rdWord) ## mems(2).read(rdWord) ##
               mems(1).read(rdWord) ## mems(0).read(rdWord)

  for (i <- 0 until 4) {
    when(io.wrEnable(i)) {
      mems(i).write(wrWord, io.wrData(8 * i + 7, 8 * i))
    }
  }

  io.stall := false.B
}
