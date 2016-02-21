package org.reactors
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait RBuilder[@spec(Int, Long, Double) T, Repr] {

  def +=(value: T): Boolean

  def -=(value: T): Boolean

  def container: Repr

}


object RBuilder {

  @implicitNotFound(
    msg = "Cannot construct a container of type ${That} with elements of type ${S}.")
  trait Factory[@spec(Int, Long, Double) S, That <: RContainer[S]] {
    def container(insert: Events[S], remove: Events[S]): That
  }

}