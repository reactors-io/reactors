package java.util



import scala.collection._
import scala.scalajs.js.timers



class Timer(val name: String, val isDaemon: Boolean) {
  private[util] var cancelled = false
  private[util] val tasks = mutable.Set[TimerTask]()

  def this() = this("timer", false)

  def this(isDaemon: Boolean) = this("timer", isDaemon)

  def this(name: String) = this(name, false)

  def schedule(task: TimerTask, delay: Long): Unit = ???

  def schedule(task: TimerTask, time: Date): Unit = ???

  def schedule(task: TimerTask, delay: Long, period: Long): Unit = ???

  def schedule(task: TimerTask, firstTime: Date, period: Long): Unit = ???

  def scheduleAtFixedRate(task: TimerTask, delay: Long, period: Long): Unit = ???

  def scheduleAtFixedRate(task: TimerTask, firstTime: Date, period: Long): Unit = ???

  def cancel(): Unit = {
    if (!cancelled) {
      cancelled = true
      for (task <- tasks) {
        task.cancelled = true
      }
    }
  }

  def purge(): Int = 0

}
