package scala.reactive
package isolate



import java.util.concurrent.atomic._
import scala.collection._
import scala.reactive.util.Monitor



final class Frame(
  val uid: Long,
  val scheduler: Scheduler,
  val isolateSystem: IsoSystem
) extends Identifiable {
  private[reactive] val monitor = new Monitor
  private[reactive] val connectors = new UniqueStore[Conn[_]]("channel", monitor)

  @volatile var name: String = _
  @volatile var defaultConnector: Conn[_] = _
  @volatile var systemConnector: Conn[_] = _

  def openConnector[@spec(Int, Long, Double) Q: Arrayable](
    name: String,
    factory: EventQ.Factory,
    isDaemon: Boolean
  ): Conn[Q] = {
    // 1. prepare and ensure a unique id
    val uid = connectors.reserveId()
    val queue = factory.newInstance[Q]
    val chan = new Chan.Local[Q](uid, queue, this)
    val events = new Events.Emitter[Q]
    val conn = new Conn(chan, queue, events, this, isDaemon)

    // 2. acquire a unique name or break
    val uname = connectors.tryStore(name, conn)

    // 3. return connector
    conn
  }

  def enqueueEvent[@spec(Int, Long, Double) Q](uid: Long, queue: EventQ[Q], x: Q) {
    ???
  }

  def isConnectorSealed(uid: Long): Boolean = {
    ???
  }

  def sealConnector(uid: Long): Boolean = {
    ???
  }

}
