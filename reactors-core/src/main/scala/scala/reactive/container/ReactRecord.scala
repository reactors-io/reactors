package scala.reactive
package container



import language.dynamics
import scala.collection._



trait ReactRecord extends ReactMutable {
  private[reactive] lazy val declarations = mutable.Set[AnyRef]()
  private[reactive] lazy val mutables = mutable.Set[ReactMutable]()
  def mutation() {
    for (rm <- mutables) rm.mutation()
  }
  def exception(t: Throwable) {}
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
