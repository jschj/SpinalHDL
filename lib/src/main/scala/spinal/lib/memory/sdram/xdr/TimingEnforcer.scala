package spinal.lib.memory.sdram.xdr

import spinal.core._
import spinal.lib._

case class TimingEnforcer(cp : CoreParameter) extends Component{
  def pl = cp.pl
  def ml = pl.ml


  val io = new Bundle {
    val config = in(CoreConfig(cp))
    val backendFull = in Bool()
    val input = slave(Stream(Fragment(FrontendCmdOutput(cp))))
    val output = master(Flow(Fragment(FrontendCmdOutput(cp))))
  }


  def Timing(loadValid : Bool, loadValue : UInt) = new Area{
    val value = Reg(UInt(cp.timingWidth bits)) init(0)
    val busy = value =/= 0
    value := value - busy.asUInt
    when(loadValid) { value := loadValue }
  }


  //Request to load timings counters
  val trigger = new Area{
    val WR,RAS,RP,RCD,WTR,CCD,RFC,RTP = False
  }

  //Banks timing counters
  val timing = new Area {
    val WTR = Timing(trigger.WTR, io.config.WTR)
    val CCD = Timing(trigger.CCD, io.config.CCD)
    val RFC = Timing(trigger.RFC, io.config.RFC)
    val banks = for (bankId <- 0 until ml.bankWidth) yield new Area {
      val hit = io.input.address.bank === bankId
      val WR = Timing(hit && trigger.WR, io.config.WR)
      val RAS = Timing(hit && trigger.RAS, io.config.RAS)
      val RP = Timing(hit && trigger.RP, io.config.RP)
      val RCD = Timing(hit && trigger.RCD, io.config.RCD)
      val RTP = Timing(hit && trigger.RTP, io.config.RTP)
    }
    val WR = banks.map(_.WR.busy).read(io.input.address.bank)
    val RAS = banks.map(_.RAS.busy).read(io.input.address.bank)
    val RP = banks.map(_.RP.busy).read(io.input.address.bank)
    val RCD = banks.map(_.RCD.busy).read(io.input.address.bank)
    val RTP = banks.map(_.RTP.busy).read(io.input.address.bank)
  }

  val timingIssue = False
  val backendIssue = False
  timingIssue.setWhen(timing.RFC.busy)
  switch(io.input.kind) {
    is(FrontendCmdOutputKind.READ) {
      timingIssue.setWhen(timing.RCD || timing.CCD.busy || timing.WTR.busy)
      backendIssue setWhen(io.backendFull)
    }
    is(FrontendCmdOutputKind.WRITE) {
      timingIssue.setWhen(timing.RCD || timing.CCD.busy || timing.RTP)
      backendIssue setWhen(io.backendFull)
    }
    is(FrontendCmdOutputKind.ACTIVE) {
      timingIssue.setWhen(timing.RP)
    }
    is(FrontendCmdOutputKind.PRECHARGE) {
      timingIssue.setWhen(timing.WR || timing.RAS)
    }
    is(FrontendCmdOutputKind.REFRESH) {
      timingIssue.setWhen(timing.RP)
    }
  }

  when(io.input.fire){
    switch(io.input.kind) {
      is(FrontendCmdOutputKind.READ) {
        trigger.CCD := True
        trigger.RTP := True
      }
      is(FrontendCmdOutputKind.WRITE) {
        trigger.CCD := True
        trigger.WTR := True
        trigger.WR := True
      }
      is(FrontendCmdOutputKind.ACTIVE) {
        trigger.RAS := True
        trigger.RCD := True
      }
      is(FrontendCmdOutputKind.PRECHARGE) {
        trigger.RP := True
      }
      is(FrontendCmdOutputKind.REFRESH) {
        trigger.RFC := True
      }
    }
  }

  io.output << io.input.haltWhen(timingIssue || backendIssue).toFlow
}