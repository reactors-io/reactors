package org.reactress



class ReactiveApi {

  implicit class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val tuple: (Signal[T], Signal[S])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S) => Unit) = {
      val s = new Tuple2Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.onReaction(s.m1),
        tuple._2.onReaction(s.m2)
      )
      s
    }
  }

  object Tuple2Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, M <: ReactMutable](tuple: (Signal[T], Signal[S]), mutable: M, f: (T, S) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

  implicit class Tuple3Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S,  @spec(Int, Long, Double) U](val tuple: (Signal[T], Signal[S], Signal[U])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (T, S, U) => Unit) = {
      val s = new Tuple3Extensions.Mutate(tuple, mutable, f)
      s.subscription = Reactive.CompositeSubscription(
        tuple._1.onReaction(s.m1),
        tuple._2.onReaction(s.m2),
        tuple._3.onReaction(s.m3)
      )
      s
    }
  }

  object Tuple3Extensions {
    class Mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, @spec(Int, Long, Double) U, M <: ReactMutable](tuple: (Signal[T], Signal[S], Signal[U]), mutable: M, f: (T, S, U) => Unit)
    extends Reactive.ProxySubscription {
      val m1 = new Reactor[T] {
        def react(value: T) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m2 = new Reactor[S] {
        def react(value: S) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      val m3 = new Reactor[U] {
        def react(value: U) {
          f(tuple._1(), tuple._2(), tuple._3())
          mutable.onMutated()
        }
        def unreact() {
        }
      }
      var subscription = Reactive.Subscription.empty
    }
  }

}
