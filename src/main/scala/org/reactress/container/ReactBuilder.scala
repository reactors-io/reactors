package org.reactress
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ReactBuilder[@spec(Int, Long, Double) -T, +Repr] extends ReactMutable {

  def +=(value: T): Boolean

  def -=(value: T): Boolean

  def container: Repr

}


object ReactBuilder {

  @implicitNotFound(msg = "Cannot construct a reactive container of type ${That} with elements of type ${S}.")
  trait Factory[@spec(Int, Long, Double) -S, +That] {
    def apply(): ReactBuilder[S, That]
  }

}