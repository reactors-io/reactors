package org.reactress
package container



import language.dynamics
import scala.collection._



trait ReactRecord extends ReactMutable {
  private[reactress] lazy val declarations = mutable.Set[AnyRef]()
  private[reactress] lazy val mutables = mutable.Set[ReactMutable]()
  override def onMutated() {
    for (rm <- mutables) rm.onMutated()
  }
  object react {
    def <<=[S <: AnyRef](s: S): S = {
      declarations += s
      s
    }
  }
  object recorded {
    def <<=[R <: ReactMutable](rm: R): R = {
      mutables += rm
      rm
    }
  }
}


object ReactRecord {

}
