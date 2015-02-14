package scala.reactive



import scala.reflect.ClassTag



trait RValPair[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q] {
  self =>

  private[reactive] var p: P = _
  private[reactive] var q: Q = _
  private[reactive] var asSignal: RValPair.Signal[P, Q] = _
  private[reactive] var subscription: Reactive.Subscription =
    Reactive.Subscription.empty
  private[reactive] val changes = new Reactive.Emitter[Unit]

  def init(dummy: RValPair[P, Q]) {
    asSignal = new RValPair.Signal(this)
  }

  init(this)

  private[reactive] def _1: P = p

  private[reactive] def _1_=(v: P) = p = v

  private[reactive] def _2: Q = q

  private[reactive] def _2_=(v: Q) = q = v

  def ultimately(reactor: =>Unit): Reactive.Subscription =
    changes.ultimately(reactor)

  def filter1(p: P => Boolean): RValPair[P, Q] = {
    val r = new RValPair.Default[P, Q]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        if (p(_1)) {
          r._1 = _1
          r._2 = _2
          r.changes.react(())
        }
      }
      def except(t: Throwable) {
        r.changes.except(t)
      }
      def unreact() {
        r.changes.unreact()
      }
    })
    r
  }

  def filter2(p: Q => Boolean): RValPair[P, Q] = {
    val r = new RValPair.Default[P, Q]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        if (p(_2)) {
          r._1 = _1
          r._2 = _2
          r.changes.react(())
        }
      }
      def except(t: Throwable) {
        r.changes.except(t)
      }
      def unreact() {
        r.changes.unreact()
      }
    })
    r
  }

  def map1[@spec(Int, Long, Double) R](f: P => R): RValPair[R, Q] = {
    val r = new RValPair.Default[R, Q]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        r._1 = f(_1)
        r._2 = _2
        r.changes.react(())
      }
      def except(t: Throwable) {
        r.changes.except(t)
      }
      def unreact() {
        r.changes.unreact()
      }
    })
    r
  }

  def map2[@spec(Int, Long, Double) S](f: Q => S): RValPair[P, S] = {
    val r = new RValPair.Default[P, S]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        r._1 = _1
        r._2 = f(_2)
        r.changes.react(())
      }
      def except(t: Throwable) {
        r.changes.except(t)
      }
      def unreact() {
        r.changes.unreact()
      }
    })
    r
  }

  def swap: RValPair[Q, P] = {
    val r = new RValPair.Default[Q, P]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        r._1 = _2
        r._2 = _1
        r.changes.react(())
      }
      def except(t: Throwable) {
        r.changes.except(t)
      }
      def unreact() {
        r.changes.unreact()
      }
    })
    r
  }

  def mutate[M <: ReactMutable](mutable: M)
    (mutation: RValPair.Signal[P, Q] => Unit): Reactive.Subscription = {
    changes foreach { _ =>
      try mutation(asSignal)
      catch {
        case t if isNonLethal(t) =>
          mutable.except(t)
      }
      mutable.react()
    }
  }

  def merge[@spec(Int, Long, Double) R <: AnyVal](f: (P, Q) => R):
    Reactive[R] with Reactive.Subscription = {
    changes map { _ =>
      f(_1, _2)
    }
  }

  def fst: Reactive[P] with Reactive.Subscription = {
    changes.map(_ => _1)
  }

  def snd: Reactive[Q] with Reactive.Subscription = {
    changes.map(_ => _2)
  }

  def boxToTuples: Reactive[(P, Q)] with Reactive.Subscription = {
    changes.map(_ => (_1, _2))
  }

  override def toString = s"RValPair(${_1}, ${_2})"

}


object RValPair {

  class Emitter[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q]
  extends RValPair[P, Q] with EventSource {
    def react(p: P, q: Q) {
      _1 = p
      _2 = q
      changes.react(())
    }
    def except(t: Throwable) {
      changes.except(t)
    }
    def unreact() {
      changes.unreact()
    }
  }

  class Default[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q]
  extends RValPair[P, Q]

  class Signal[@spec(Int, Long, Double) P, @spec(Int, Long, Double) Q]
    (val pair: RValPair[P, Q]) {
    def _1 = pair._1
    def _2 = pair._2
  }
}
