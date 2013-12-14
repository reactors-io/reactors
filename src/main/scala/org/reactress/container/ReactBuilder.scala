package org.reactress
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ReactBuilder[@spec(Int, Long, Double) -T, +Repr] {

  def +=(value: T): this.type

  def -=(value: T): this.type

  def result: Repr

}


object ReactBuilder {

  @implicitNotFound(msg = "Cannot construct a reactive container of type ${That} with elements of type ${S} from a container of type ${Repr}.")
  trait Factory[-Repr, @spec(Int, Long, Double) -S, +That] {
    protected def create(): ReactBuilder[S, That]
    def apply(from: ReactContainer[_, _]): ReactBuilder[S, That] = {
      val b = create()
      from.adjustBuilder(b)
      b
    }
  }

}