package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait PairBuilder[@spec(Int, Long, Double) -P, -Q <: AnyRef, +Repr] extends ReactMutable.Subscriptions {

  def insertPair(_1: P, _2: Q): Boolean

  def removePair(_1: P, _2: Q): Boolean

  def container: Repr

}


object PairBuilder {

  @implicitNotFound(msg = "Cannot construct a reactive container of type ${That} with elements of types ${P} and ${Q}.")
  trait Factory[@spec(Int, Long, Double) -P, -Q <: AnyRef, +That] {
    def apply(): PairBuilder[P, Q, That]
  }

}