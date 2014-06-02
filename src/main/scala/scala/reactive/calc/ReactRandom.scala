package scala.reactive
package calc



import scala.util.Random



/** Reactive random value.
 *
 *  It can be used as a regular `Random` object to obtain random values:
 *
 *  {{{
 *  val r = new ReactRandom(1L)
 *  r.nextDouble()
 *  }}}
 *
 *  Or, as a reactive random value, by subscribing to events produced when `emit` is called:
 *
 *  {{{
 *  val evens = r.react.ints.filter(_ % 2 == 0)
 *  }}}
 *
 *  Above, `evens` contains random values.
 */
class ReactRandom(private val jucRandom: java.util.Random) extends Random(jucRandom) {
  self =>

  def this(seed: Long) = this(new java.util.Random(seed))

  def emit() = react.randomEmitter.emit()

  def close() = react.randomEmitter.close()

  object react {
    private[calc] val randomEmitter = new Reactive.SideEffectEmitter[Unit](() => ())
    def ints = randomEmitter.map(_ => self.nextInt())
    def longs = randomEmitter.map(_ => self.nextLong())
    def doubles = randomEmitter.map(_ => self.nextDouble())
  }

}


