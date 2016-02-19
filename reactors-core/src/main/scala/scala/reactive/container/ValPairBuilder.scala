package scala.reactive
package container



import scala.collection._
import scala.annotation.implicitNotFound



trait ValPairBuilder[@spec(Int, Long, Double) -P, @spec(Int, Long, Double) -Q, +Repr] extends ReactMutable.Subscriptions {

  def insertPair(_1: P, _2: Q): Boolean

  def removePair(_1: P, _2: Q): Boolean

  def container: Repr

}


object ValPairBuilder {

  @implicitNotFound(msg = "Cannot construct a reactive container of type ${That} with elements of types ${P} and ${Q}.")
  trait Factory[@spec(Int, Long, Double) -P, @spec(Int, Long, Double) -Q, +That] {
    def apply(): ValPairBuilder[P, Q, That]
  }

}