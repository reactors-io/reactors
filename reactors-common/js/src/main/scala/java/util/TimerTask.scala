package java.util



import scala.scalajs.js.timers



abstract class TimerTask {
  private[util] var cancelled: Boolean = false
  private[util] var lastScheduled: Long = 0L

  def run(): Unit

  def cancel(): Unit = {
    cancelled = true
  }

  def scheduledExecutionTime(): Long = lastScheduled

  private[util] def doRun() {
    if (!cancelled) {
      lastScheduled = System.currentTimeMillis()
      try {
        run()
      } catch {
        case t: Throwable =>
          cancelled = true
          throw t
      }
    }
  }

}
