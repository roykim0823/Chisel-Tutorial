import chisel3._
import chisel3.util._

class Pwm extends Module {
  val io = IO(new Bundle {
    val led = Output(UInt(4.W))
  })

  // A reusable PWM generator function. `nrCycles` sets the period; `din` sets
  // the duty cycle. Returns a Bool that is high for `din` of every `nrCycles`.
  def pwm(nrCycles: Int, din: UInt) = {
    val cntReg = RegInit(0.U(unsignedBitLength(nrCycles - 1).W))
    cntReg := Mux(cntReg === (nrCycles - 1).U, 0.U, cntReg + 1.U)
    din > cntReg
  }

  // A fixed 30 % duty cycle over a 10-cycle period.
  val din = 3.U
  val dout = pwm(10, din)

  // Modulate the duty cycle up and down (a triangular waveform) to fade an LED.
  val FREQ = 100000000 // a 100 MHz clock input
  val MAX = FREQ / 1000 // 1 kHz

  val modulationReg = RegInit(0.U(32.W))
  val upReg = RegInit(true.B)

  when (modulationReg < FREQ.U && upReg) {
    modulationReg := modulationReg + 1.U
  } .elsewhen (modulationReg === FREQ.U && upReg) {
    upReg := false.B
  } .elsewhen (modulationReg > 0.U && !upReg) {
    modulationReg := modulationReg - 1.U
  } .otherwise { // 0
    upReg := true.B
  }

  // Divide the modulation by 1024 (~ the 1 kHz PWM range) with a right shift.
  val sig = pwm(MAX, modulationReg >> 10)

  io.led := Cat(0.U, sig, dout)
}
