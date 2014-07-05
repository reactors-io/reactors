package scala.reactive
package container



import scala.reactive.calc.RefValFun



trait PairContainer[@spec(Int, Long, Double) P, Q <: AnyRef] {
  private[reactive] var liftedContainer: PairContainer.Lifted[P, Q] = _

  def init(dummy: PairContainer[P, Q]) {
    liftedContainer = new PairContainer.Lifted[P, Q](this)
  }

  init(this)

  def react = liftedContainer

  def inserts: ReactPair[P, Q]

  def removes: ReactPair[P, Q]

  def filter1(p: P => Boolean): PairContainer[P, Q] = {
    new PairContainer.Filter1(this, p)
  }

  def filter2(p: Q => Boolean): PairContainer[P, Q] = {
    new PairContainer.Filter2(this, p)
  }

  def collect2[S <: AnyRef](pf: PartialFunction[Q, S]): PairContainer[P, S] = {
    new PairContainer.Collect2(this, pf)
  }

  def valmap2[@spec(Int, Long, Double) R <: AnyVal, @spec(Int, Long, Double) S <: AnyVal](f: RefValFun[Q, S])(implicit e: P =:= R): ValPairContainer[R, S] = {
    new PairContainer.Valmap2(this, f)
  }

}


object PairContainer {

  class Lifted[@spec(Int, Long, Double) P, Q <: AnyRef](val container: PairContainer[P, Q]) {
    def to[That <: ReactMap[P, Q]](implicit factory: PairBuilder.Factory[P, Q, That]): That = {
      val builder = factory()
      val result = builder.container
  
      result.subscriptions += container.inserts.mutate(builder) {
        pair => builder.insertPair(pair._1, pair._2)
      }
      result.subscriptions += container.removes.mutate(builder) {
        pair => builder.removePair(pair._1, pair._2)
      }
  
      result
    }
    def mutate(m: ReactMutable)(insert: ReactPair.Signal[P, Q] => Unit)(remove: ReactPair.Signal[P, Q] => Unit): Reactive.Subscription = {
      new Mutate(container, m, insert, remove)
    }
  }

  class Emitter[@spec(Int, Long, Double) P, Q <: AnyRef]
  extends PairContainer[P, Q] {
    private[reactive] var insertsEmitter: ReactPair.Emitter[P, Q] = _
    private[reactive] var removesEmitter: ReactPair.Emitter[P, Q] = _

    def init(dummy: Emitter[P, Q]) {
      insertsEmitter = new ReactPair.Emitter[P, Q]
      removesEmitter = new ReactPair.Emitter[P, Q]
    }

    init(this)

    def inserts = insertsEmitter

    def removes = removesEmitter
  }

  class Filter1[@spec(Int, Long, Double) P, Q <: AnyRef]
    (val container: PairContainer[P, Q], val p: P => Boolean)
  extends PairContainer[P, Q] {
    val inserts = container.inserts.filter1(p)
    val removes = container.removes.filter1(p)
  }

  class Filter2[@spec(Int, Long, Double) P, Q <: AnyRef]
    (val container: PairContainer[P, Q], val p: Q => Boolean)
  extends PairContainer[P, Q] {
    val inserts = container.inserts.filter2(p)
    val removes = container.removes.filter2(p)
  }

  class Collect2[@spec(Int, Long, Double) P, Q <: AnyRef, S <: AnyRef]
    (val container: PairContainer[P, Q], val pf: PartialFunction[Q, S])
  extends PairContainer[P, S] {
    val inserts = container.inserts.collect2(pf)
    val removes = container.removes.collect2(pf)
  }

  class Valmap2[@spec(Int, Long, Double) P, Q <: AnyRef, @spec(Int, Long, Double) R <: AnyVal, @spec(Int, Long, Double) S <: AnyVal]
    (val container: PairContainer[P, Q], val f: RefValFun[Q, S])(implicit e: P =:= R)
  extends ValPairContainer[R, S] {
    val inserts = container.inserts.valmap2(f)
    val removes = container.removes.valmap2(f)
  }

  class Mutate[@spec(Int, Long, Double) P, Q <: AnyRef, M <: ReactMutable]
    (val container: PairContainer[P, Q], val m: M, val ins: ReactPair.Signal[P, Q] => Unit, val rem: ReactPair.Signal[P, Q] => Unit)
  extends Reactive.ProxySubscription {
    val subscription = Reactive.CompositeSubscription(
      container.inserts.mutate(m)(ins),
      container.removes.mutate(m)(rem)
    )
  }

}
