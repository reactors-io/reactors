package org.reactress
package container



import scala.collection._



trait ReactRecord extends ReactMutable.SubscriptionSet {
  private val mutables = scala.collection.mutable.Set[ReactMutable]()
  def recorded[R <: ReactMutable](rm: R) = {
    mutables += rm
    rm
  }
  override def onMutated() {
    for (rm <- mutables) rm.onMutated()
  }
}


object ReactRecord {

}
