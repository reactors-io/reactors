package scala.reactive
package isolate






final class Frame(
  val uid: Long,
  val scheduler: Scheduler,
  val isolateSystem: IsoSystem
) {
  @volatile var name: String = _
  @volatile var defaultConnector: Connector[_] = _
  @volatile var systemConnector: Connector[_] = _

  
}
