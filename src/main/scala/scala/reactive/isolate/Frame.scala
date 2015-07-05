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
  private val channels = new UniqueMap[Chan[_]]("channel", monitor)

  @volatile var name: String = _
  @volatile var defaultConnector: Conn[_] = _
  @volatile var systemConnector: Conn[_] = _

  def openConnector[@spec(Int, Long, Double) Q: Arrayable](
    name: String,
    f: EventQ.Factory,
  ) = {
    val uid = channels.reserveId()
    val queue = 
    val chan = new Chan.Local(uid, )
  }

  def enqueueEvent[@spec(Int, Long, Double) Q](queue: EventQ[T], x: T): Unit = {
    ???
  }

  def isChannelSealed(uid: Long): Boolean = {
    ???
  }

}
