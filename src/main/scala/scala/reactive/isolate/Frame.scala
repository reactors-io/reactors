package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



final class Frame(
  val uid: Long,
  val scheduler: Scheduler,
  val isolateSystem: IsoSystem
) extends Identifiable {
  private val channelNameCounter = new AtomicLong(0L)
  private val channels = mutable.Map[String, Channel[_]]()

  @volatile var name: String = _
  @volatile var defaultConnector: Connector[_] = _
  @volatile var systemConnector: Connector[_] = _

  def reserveChannelId(): Long = channelNameCounter.getAndIncrement()
}
