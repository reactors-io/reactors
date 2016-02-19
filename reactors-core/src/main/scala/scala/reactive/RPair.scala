package scala.reactive



import scala.reflect.ClassTag
import scala.reactive.calc.RVFun
import scala.reactive.calc.VRVFun



trait RPair[@spec(Int, Long, Double) P, Q <: AnyRef] {
  self =>

  private[reactive] var p: P = _
  private[reactive] var q: Q = _
  private[reactive] var asSignal: RPair.Signal[P, Q] = _
  private[reactive] var subscription: Events.Subscription =
    Events.Subscription.empty
  private[reactive] val changes = new Events.Emitter[Unit]

  def init(dummy: RPair[P, Q]) {
    asSignal = new RPair.Signal(this)
  }

  init(this)

  private[reactive] def _1: P = p

  private[reactive] def _1_=(v: P) = p = v

  private[reactive] def _2: Q = q

  private[reactive] def _2_=(v: Q) = q = v

  def ultimately(reactor: =>Unit): Events.Subscription =
    changes.ultimately(reactor)

  def filter1(p: P => Boolean): RPair[P, Q] = {
    val r = new RPair.Default[P, Q]
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

  def filter2(p: Q => Boolean): RPair[P, Q] = {
    val r = new RPair.Default[P, Q]
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

  def map1[@spec(Int, Long, Double) R](f: P => R): RPair[R, Q] = {
    val r = new RPair.Default[R, Q]
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

  def map2[S <: AnyRef](f: Q => S): RPair[P, S] = {
    val r = new RPair.Default[P, S]
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

  def collect2[S <: AnyRef](pf: PartialFunction[Q, S]): RPair[P, S] = {
    val r = new RPair.Default[P, S]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        if (pf.isDefinedAt(_2)) {
          r._1 = _1
          r._2 = pf(_2)
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

  def rvmap2[
    @spec(Int, Long, Double) R <: AnyVal,
    @spec(Int, Long, Double) S <: AnyVal
  ](f: RVFun[Q, S])(implicit e: P =:= R): RValPair[R, S] = {
    val r = new RValPair.Default[R, S]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        r._1 = e(_1)
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

  def vrvmap1[@spec(Int, Long, Double) S <: AnyVal](f: VRVFun[P, Q, S])
    (implicit e: P <:< AnyVal): RPair[S, Q] = {
    val r = new RPair.Default[S, Q]
    r.subscription = changes.observe(new Reactor[Unit] {
      def react(u: Unit) {
        r._1 = f(_1, _2)
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

  def mutate[M <: ReactMutable](mutable: M)
    (mutation: RPair.Signal[P, Q] => Unit): Events.Subscription = {
    changes foreach { _ =>
      try mutation(asSignal)
      catch {
        case t if isNonLethal(t) =>
          mutable.exception(t)
      }
      mutable.mutation()
    }
  }

  def fst: Events[P] with Events.Subscription = {
    changes.map(_ => _1)
  }

  def snd: Events[Q] with Events.Subscription = {
    changes.map(_ => _2)
  }

  def boxToTuples: Events[(P, Q)] with Events.Subscription = {
    changes.map(_ => (_1, _2))
  }

  override def toString = s"RPair(${_1}, ${_2})"

}


object RPair {

  class Emitter[@spec(Int, Long, Double) P, Q <: AnyRef]
  extends RPair[P, Q] with EventSource {
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
  
  class Default[@spec(Int, Long, Double) P, Q <: AnyRef] extends RPair[P, Q]

  class Signal[@spec(Int, Long, Double) P, Q <: AnyRef](val pair: RPair[P, Q]) {
    def _1 = pair._1
    def _2 = pair._2
  }

}
