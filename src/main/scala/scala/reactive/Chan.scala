package scala.reactive



import scala.collection._



trait Chan[@spec(Int, Long, Double) T] extends Identifiable {

  def !(x: T): Unit

  def isSealed: Boolean

  def seals: Events[Throwable]

}
