import chisel3._
import chisel3.util._

// The full input-processing chain for a button: synchronize, debounce (sample
// slowly), majority-vote to filter noise, then detect the rising edge to count.
// `fac` is the clock-divide factor that sets the sample period.
class Debounce(fac: Int = 100000000 / 100) extends Module {
  val io = IO(new Bundle {
    val btnU = Input(Bool())
    val led = Output(Bool())
  })

  val btn = io.btnU

  // (1) Input synchronizer: two flip-flops to contain metastability.
  val btnSync = RegNext(RegNext(btn))

  // (2) Debounce: sample the synchronized signal once every `fac` cycles.
  val btnDebReg = RegInit(false.B)

  val cntReg = RegInit(0.U(32.W))
  val tick = cntReg === (fac - 1).U

  cntReg := cntReg + 1.U
  when (tick) {
    cntReg := 0.U
    btnDebReg := btnSync
  }

  // (3) Majority voting over the last three samples to filter short spikes.
  val shiftReg = RegInit(0.U(3.W))
  when (tick) {
    shiftReg := shiftReg(1, 0) ## btnDebReg   // shift left, new sample in LSB
  }
  val btnClean = (shiftReg(2) & shiftReg(1)) | (shiftReg(2) & shiftReg(0)) | (shiftReg(1) & shiftReg(0))

  // (4) Rising-edge detection: single-cycle pulse used to increment a counter.
  val risingEdge = btnClean & !RegNext(btnClean)

  val reg = RegInit(0.U(8.W))
  when (risingEdge) {
    reg := reg + 1.U
  }

  io.led := reg
}

// The same chain, but each stage packaged as a reusable Chisel *function* that
// returns hardware. Functions are a lightweight alternative to full modules.
class DebounceFunc(fac: Int = 100000000 / 100) extends Module {
  val io = IO(new Bundle {
    val btnU = Input(Bool())
    val led = Output(Bool())
  })

  def sync(v: Bool) = RegNext(RegNext(v))

  def rising(v: Bool) = v & !RegNext(v)

  def tickGen() = {
    val reg = RegInit(0.U(log2Up(fac).W))
    val tick = reg === (fac - 1).U
    reg := Mux(tick, 0.U, reg + 1.U)
    tick
  }

  def filter(v: Bool, t: Bool) = {
    val reg = RegInit(0.U(3.W))
    when (t) {
      reg := reg(1, 0) ## v
    }
    (reg(2) & reg(1)) | (reg(2) & reg(0)) | (reg(1) & reg(0))
  }

  val btnSync = sync(io.btnU)

  val tick = tickGen()
  val btnDeb = RegInit(false.B)
  when (tick) {
    btnDeb := btnSync
  }

  val btnClean = filter(btnDeb, tick)
  val risingEdge = rising(btnClean)

  val reg = RegInit(0.U(8.W))
  when (risingEdge) {
    reg := reg + 1.U
  }

  io.led := reg
}
