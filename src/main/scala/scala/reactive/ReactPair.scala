package scala.reactive



import scala.reflect.ClassTag



trait ReactPair[@spec(Int, Long, Double) P, Q <: AnyRef] {
  self =>

  private[reactive] var _1: P = _
  private[reactive] var _2: Q = _
  private[reactive] var asSignal: ReactPair.Signal[P, Q] = _
  private[reactive] var subscription: Reactive.Subscription = Reactive.Subscription.empty
  private[reactive] val changes = new Reactive.Emitter[Unit]

  def init(dummy: ReactPair[P, Q]) {
    asSignal = new ReactPair.Signal(this)
  }

  init(this)

  def filter1(p: P => Boolean): ReactPair[P, Q] = {
    val r = new ReactPair.Default[P, Q]
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

  def filter2(p: Q => Boolean): ReactPair[P, Q] = {
    val r = new ReactPair.Default[P, Q]
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

  def map1[@spec(Int, Long, Double) R](f: P => R): ReactPair[R, Q] = {
    val r = new ReactPair.Default[R, Q]
    r.subscription = changes.onAnyReaction { _ =>
      r._1 = f(_1)
      r._2 = _2
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def map2[S <: AnyRef](f: Q => S): ReactPair[P, S] = {
    val r = new ReactPair.Default[P, S]
    r.subscription = changes.onAnyReaction { _ =>
      r._1 = _1
      r._2 = f(_2)
      r.changes += ()
    } {
      r.changes.close()
    }
    r
  }

  def collect2[S <: AnyRef](pf: PartialFunction[Q, S]): ReactPair[P, S] = {
    val r = new ReactPair.Default[P, S]
    r.subscription = changes.onAnyReaction { _ =>
      if (pf.isDefinedAt(_2)) {
        r._2 = pf(_2)
        r.changes += ()
      }
    } {
      r.changes.close()
    }
    r
  }

  def valmap2[@spec(Int, Long, Double) R <: AnyVal, @spec(Int, Long, Double) S <: AnyVal](f: calc.ValFun[Q, S])(implicit e: P =:= R): ReactValPair[R, S] = {
    val r = new ReactValPair.Default[R, S]
    r.subscription = changes.onAnyReaction { _ =>
      r._1 = e(_1)
      r._2 = f(_2)
    } {
      r.changes.close()
    }
    r
  }

  def mutate[M <: ReactMutable](mutable: M)(mutation: ReactPair.Signal[P, Q] => Unit): Reactive.Subscription = {
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

  override def toString = s"ReactPair(${_1}, ${_2})"

}


object ReactPair {

  class Emitter[@spec(Int, Long, Double) P, Q <: AnyRef] extends ReactPair[P, Q] {
    def emit(p: P, q: Q) {
      _1 = p
      _2 = q
      changes += ()
    }
  }
  
  class Default[@spec(Int, Long, Double) P, Q <: AnyRef] extends ReactPair[P, Q]

  class Signal[@spec(Int, Long, Double) P, Q <: AnyRef](val pair: ReactPair[P, Q]) {
    def _1 = pair._1
    def _2 = pair._2
  }

}
