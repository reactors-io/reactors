package scala.reactive
package container



import scala.reflect.ClassTag



class RCell[@spec(Int, Long, Double) T](private var value: T)
extends Signal.Default[T] with ReactMutable {
  self =>

  def apply(): T = value
  
  def :=(v: T): Unit = {
    value = v
    reactAll(v)
  }

  override def toString = s"RCell($value)"
}


object RCell {

  def apply[@spec(Int, Long, Double) T](x: T) = new RCell[T](x)
  
}
