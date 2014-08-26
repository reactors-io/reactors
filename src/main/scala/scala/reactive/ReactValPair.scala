package scala.reactive



import scala.reflect.ClassTag



trait ReactValPair[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q] {
  self =>

  private[reactive] var p: P = _
  private[reactive] var q: Q = _
  private[reactive] var asSignal: ReactValPair.Signal[P, Q] = _
  private[reactive] var subscription: Reactive.Subscription = Reactive.Subscription.empty
  private[reactive] val changes = new Reactive.Emitter[Unit]

  def init(dummy: ReactValPair[P, Q]) {
    asSignal = new ReactValPair.Signal(this)
  }

  init(this)

  private[reactive] def _1: P = p

  private[reactive] def _1_=(v: P) = p = v

  private[reactive] def _2: Q = q

  private[reactive] def _2_=(v: Q) = q = v

  def onUnreact(reactor: =>Unit): Reactive.Subscription = changes.onUnreact(reactor)

  def filter1(p: P => Boolean): ReactValPair[P, Q] = {
    val r = new ReactValPair.Default[P, Q]
    r.subscription = changes.onReactUnreact { _ =>
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
    val r = new ReactValPair.Default[P, Q]
    r.subscription = changes.onReactUnreact { _ =>
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

  def map1[@spec(Int, Long, Double) R](f: P => R): ReactValPair[R, Q] = {
    val r = new ReactValPair.Default[R, Q]
    r.subscription = changes.onReactUnreact { _ =>
      r._1 = f(_1)
      r._2 = _2
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def map2[@spec(Int, Long, Double) S](f: Q => S): ReactValPair[P, S] = {
    val r = new ReactValPair.Default[P, S]
    r.subscription = changes.onReactUnreact { _ =>
      r._1 = _1
      r._2 = f(_2)
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def swap: ReactValPair[Q, P] = {
    val r = new ReactValPair.Default[Q, P]
    r.subscription = changes.onReactUnreact { _ =>
      r._1 = _2
      r._2 = _1
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

  def merge[@spec(Int, Long, Double) R <: AnyVal](f: (P, Q) => R): Reactive[R] with Reactive.Subscription = {
    changes map { _ =>
      f(_1, _2)
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

  class Emitter[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q] extends ReactValPair[P, Q] with EventSource {
    def emit(p: P, q: Q) {
      _1 = p
      _2 = q
      changes += ()
    }
    def close() {
      changes.close()
    }
  }

  class Default[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q] extends ReactValPair[P, Q]

  class Signal[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q](val pair: ReactValPair[P, Q]) {
    def _1 = pair._1
    def _2 = pair._2
  }
}
