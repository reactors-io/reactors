package scala.reactive
package calc



import scala.util.Random



class ReactRandom(val jucRandom: java.util.Random) extends Random(jucRandom) {
  self =>

  def this(seed: Long) = this(new java.util.Random(seed))

  object react {
    def ints = new ReactRandom.Emitter[Int](() => self.nextInt())
    def longs = new ReactRandom.Emitter[Long](() => self.nextLong())
    def doubles = new ReactRandom.Emitter[Double](() => self.nextDouble())
    def apply[@spec(Int, Long, Double) T](r: Random => T) = new ReactRandom.Emitter[T](() => r(self))
  }

}


object ReactRandom {

  class Emitter[@spec(Int, Long, Double) T](f: () => T) extends Reactive.Default[T] {
    private var closed = false
    final def emit() = if (!closed) reactAll(f())
    final def close() = closed = true
  }

}
