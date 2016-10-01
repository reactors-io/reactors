package java.util



import scala.collection._
import scala.concurrent.duration._
import scala.scalajs.js.timers._



class Timer(val name: String, val isDaemon: Boolean) {
  private[util] var cancelled = false
  private[util] val tasks = mutable.Set[TimerTask]()

  def this() = this("timer", false)

  def this(isDaemon: Boolean) = this("timer", isDaemon)

  def this(name: String) = this(name, false)

  private[util] def scheduleOnce(task: TimerTask, delay: Long): Unit = {
    tasks += task
    setTimeout(delay.millis) {
      task.doRun()
    }
  }

  private[util] def getMillisUntil(time: Date): Long = {
    math.max(0, time.getTime - (new Date()).getTime)
  }

  def schedule(task: TimerTask, delay: Long): Unit = {
    scheduleOnce(task, delay)
  }

  def schedule(task: TimerTask, time: Date): Unit = {
    val delay = getMillisUntil(time)
    scheduleOnce(task, delay)
  }

  private[util] def schedulePeriodically(
      task: TimerTask, delay: Long, period: Long): Unit = {
    tasks += task
    setTimeout(delay.millis) {
      def loop() {
        task.doRun()
        setTimeout(period.millis) {
          loop()
        }
      }
      loop()
    }
  }

  def schedule(task: TimerTask, delay: Long, period: Long): Unit = {
    schedulePeriodically(task, delay, period)
  }

  def schedule(task: TimerTask, firstTime: Date, period: Long): Unit = {
    val delay = getMillisUntil(firstTime)
    schedulePeriodically(task, delay, period)
  }

  private[util] def scheduleFixed(
      task: TimerTask, delay: Long, period: Long): Unit = {
    tasks += task
    setTimeout(delay.millis) {
      def loop(lastTime: Long) {
        var nowTime = (new Date()).getTime
        task.doRun()
        var left = nowTime - lastTime - period
        while (left > period) {
          nowTime = (new Date()).getTime
          task.doRun()
          left -= period
        }
        setTimeout((period - left).millis) {
          loop(nowTime)
        }
      }
      val startTime = (new Date()).getTime
      task.doRun()
      setTimeout(period.millis) {
        loop(startTime)
      }
    }
  }

  def scheduleAtFixedRate(task: TimerTask, delay: Long, period: Long): Unit = {
    scheduleFixed(task, delay, period)
  }

  def scheduleAtFixedRate(task: TimerTask, firstTime: Date, period: Long): Unit = {
    val delay = getMillisUntil(firstTime)
    scheduleFixed(task, delay, period)
  }

  def cancel(): Unit = {
    if (!cancelled) {
      cancelled = true
      for (task <- tasks) {
        task.cancelled = true
      }
      tasks.clear()
    }
  }

  def purge(): Int = {
    var count = 0
    for (task <- tasks.toArray) {
      if (task.cancelled) {
        tasks.remove(task)
        count += 1
      }
    }
    count
  }

}
