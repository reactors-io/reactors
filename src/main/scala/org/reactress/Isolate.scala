package org.reactress






trait Isolate[@spec(Int, Long, Double) T] {
  def bind(r: Reactive[T]): Reactive.Subscription
}


object Isolate {

  private[reactress] val selfIsolate = new ThreadLocal[Isolate[_]] {
    override def initialValue = null
  }

  /** Returns the current isolate.
   *
   *  If the caller is not executing in an isolate,
   *  throws an `IllegalStateException`.
   */
  def self[@spec(Int, Long, Double) T]: Isolate[T] = {
    val i = selfIsolate.get
    if (i == null) throw new IllegalStateException("${Thread.currentThread.getName} not in an isolate.")
    i.asInstanceOf[Isolate[T]]
  }

}
