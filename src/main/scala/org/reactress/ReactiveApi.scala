package org.reactress



class ReactiveApi {

  implicit class Tuple2Extensions[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S](val tuple: (Signal[T], Signal[S])) {
    def mutate[M <: ReactMutable](mutable: M)(f: (M, T, S) => Unit) = Tuple2Extensions.mutate(tuple, mutable, f)
  }

  object Tuple2Extensions {
    def mutate[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S, M](tuple: (Signal[T], Signal[S]), mutable: M, f: (M, T, S) => Unit) = {
      class Mutate extends Reactive.ProxySubscription {
        def mutate[R](r: Reactive[R]) = new Reactor[R] {
          def react(value: R) {
            f(mutable, tuple._1(), tuple._2())
          }
          def unreact() {
          }
        }
        val m1 = mutate(tuple._1)
        val m2 = mutate(tuple._2)
        val subscription = Reactive.CompositeSubscription(
          tuple._1.onReaction(m1),
          tuple._2.onReaction(m2)
        )
      }

      new Mutate
    }
  }

}
