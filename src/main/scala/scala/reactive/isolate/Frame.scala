package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._



final class Frame(
  val uid: Long,
  val scheduler: Scheduler,
  val isolateSystem: IsoSystem
) extends Identifiable {
  private val monitor = new Monitor
  private val channels = new NameMap[Channel[_]]("channel", monitor)

  @volatile var name: String = _
  @volatile var defaultConnector: Connector[_] = _
  @volatile var systemConnector: Connector[_] = _

  def openConnector[@spec(Int, Long, Double) Q: Arrayable]() = ???
}
