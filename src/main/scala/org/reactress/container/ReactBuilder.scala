package org.reactress
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ReactBuilder[@spec(Int, Long, Double) -T, +Repr] extends ReactMutable {

  def +=(value: T): this.type

  def -=(value: T): this.type

  def container: Repr

}


object ReactBuilder {

  @implicitNotFound(msg = "Cannot construct a reactive container of type ${That} with elements of type ${S}.")
  trait Factory[@spec(Int, Long, Double) -S, +That] {
    protected def create(): ReactBuilder[S, That]
    def apply(from: ReactContainer[_]): ReactBuilder[S, That] = {
      val b = create()
      from.adjustBuilder(b)
      b
    }
  }

}