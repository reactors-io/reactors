package scala.reactive



import scala.reflect.ClassTag



class ReactValPair[@spec(Int, Long, Double) P <: AnyVal, @spec(Int, Long, Double) Q <: AnyVal] {
  self =>

  private[reactive] var _1: P = _
  private[reactive] var _2: Q = _
  private[reactive] var asSignal: ReactValPair.Signal[P, Q] = _
  private[reactive] var subscription: Reactive.Subscription = Reactive.Subscription.empty
  private[reactive] val changes = new Reactive.Emitter[Unit]

  def init(dummy: ReactValPair[P, Q]) {
    asSignal = new ReactValPair.AsSignal(this)
  }

  init(this)

  def filter1(p: P => Boolean): ReactValPair[P, Q] = {
    val r = new ReactValPair[P, Q]
    r.subscription = changes.onAnyReaction { _ =>
      if (p(_1)) {
        r._1 = _1
        r._2 = _2
        r.changes += ()
      }
    } {
      r.changes.close()
    }
    r
  }

  def filter2(p: Q => Boolean): ReactValPair[P, Q] = {
    val r = new ReactValPair[P, Q]
    r.subscription = changes.onAnyReaction { _ =>
      if (p(_2)) {
        r._1 = _1
        r._2 = _2
        r.changes += ()
      }
    } {
      r.changes.close()
    }
    r
  }

  def map1[@spec(Int, Long, Double) R <: AnyVal](f: P => R): ReactValPair[R, Q] = {
    val r = new ReactValPair[R, Q]
    r.subscription = changes.onAnyReaction { _ =>
      r._1 = f(_1)
      r._2 = _2
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def map2[@spec(Int, Long, Double) S <: AnyVal](f: Q => S): ReactValPair[P, S] = {
    val r = new ReactValPair[P, S]
    r.subscription = changes.onAnyReaction { _ =>
      r._1 = _1
      r._2 = f(_2)
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def mutate[M <: ReactMutable](mutable: M)(mutation: ReactValPair.Signal[P, Q] => Unit): Reactive.Subscription = {
    changes on {
      mutation(asSignal)
      mutable.onMutated()
    }
  }

  def to1: Reactive[P] with Reactive.Subscription = {
    changes.map(_ => _1)
  }

  def to2: Reactive[Q] with Reactive.Subscription = {
    changes.map(_ => _2)
  }

  def boxToTuples: Reactive[(P, Q)] with Reactive.Subscription = {
    changes.map(_ => (_1, _2))
  }

  override def toString = s"ReactValPair(${_1}, ${_2})"

}


object ReactValPair {

  trait Signal[@spec(Int, Long, Double) P <: AnyVal, @spec(Int, Long, Double) Q <: AnyVal] {
    def _1: P
    def _2: Q
  }

  class AsSignal[@spec(Int, Long, Double) P <: AnyVal, @spec(Int, Long, Double) Q <: AnyVal](val pair: ReactValPair[P, Q]) extends Signal[P, Q] {
    def _1 = pair._1
    def _2 = pair._2
  }
}
