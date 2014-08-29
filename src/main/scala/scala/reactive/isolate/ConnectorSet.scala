package scala.reactive
package isolate






class ConnectorSet {

  def areEmpty: Boolean = ???

  def areUnreacted: Boolean = ???

  def +=(connector: Connector[_]): Unit = ???

  def markUnreacted(connector: Connector[_]): Unit = ???

}


