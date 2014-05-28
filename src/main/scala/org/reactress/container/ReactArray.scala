package org.reactress
package container



import scala.collection._
import scala.annotation.implicitNotFound



class ReactArray[@spec(Int, Long, Double) T: Arrayable]
extends ReactContainer[T] with ReactBuilder[T, ReactArray[T]] {
  private var array = implicitly[Arrayable[T]].newArray(8)
  private var len = 0

  val inserts = new Reactive.Emitter[T]
  val removes = new Reactive.Emitter[T]

  val react = new ReactArray.Lifted(this)

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

  def -=(value: T) = {
    throw new Exception("Elements cannot be removed from a reactive array.")
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


object ReactArray {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new ReactArray[T]

  class Lifted[@spec(Int, Long, Double) T](val container: ReactArray[T]) extends ReactContainer.Lifted[T]

  val initSize = 16

  val loadFactor = 450

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new ReactBuilder.Factory[T, ReactArray[T]] {
    def apply() = ReactArray[T]
  }

}