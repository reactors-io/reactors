package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



class RArray[@spec(Int, Long, Double) T: Arrayable]
extends RContainer[T] {
  private var array = implicitly[Arrayable[T]].newArray(8)
  private var len = 0

  val inserts = new Reactive.Emitter[T]
  val removes = new Reactive.Emitter[T]

  val react = new RArray.Lifted(this)

  def +=(value: T) = {
    if (len == array.length) {
      val narray = implicitly[Arrayable[T]].newArray(array.length * 2)
      System.arraycopy(array, 0, narray, 0, array.length)
      array = narray
    }
    
    array(len) = value
    len += 1
    inserts += value

    true
  }

  def apply(idx: Int) = array(idx)

  def length = len

  def size = len

  def foreach(f: T => Unit) {
    var i = 0
    while (i < len) {
      f(array(i))
      i += 1
    }
  }

  def clear() {
    array = implicitly[Arrayable[T]].newArray(8)
    len = 0
  }

  def container = this

}


object RArray {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new RArray[T]

  class Lifted[@spec(Int, Long, Double) T](val container: RArray[T]) extends RContainer.Lifted[T]

  val initSize = 16

  val loadFactor = 450

}