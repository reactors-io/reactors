package scala.reactive
package container



import scala.reflect.ClassTag



class ReactCell[@spec(Int, Long, Double) T](private var value: T)
extends Signal.Default[T] with ReactMutable {
  self =>

  def apply(): T = value
  
  def :=(v: T): Unit = {
    value = v
    reactAll(v)
  }

  override def toString = s"ReactCell($value)"
}


object ReactCell {

  def apply[@spec(Int, Long, Double) T](x: T) = new ReactCell[T](x)
}
