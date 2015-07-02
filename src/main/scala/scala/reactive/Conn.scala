package scala.reactive



import scala.collection._



trait Conn[@spec(Int, Long, Double) T] extends Identifiable {

  def channel: Chan[T]

  def events: Events[T]

  def seal(): Unit

}
