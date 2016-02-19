package scala.reactive
package calc



import scala.util.Random



/** Reactive random value.
 *
 *  It can be used as a regular random generator that produces random values:
 *
 *  {{{
 *  val r = new ReactRandom(1L)
 *  r.double()
 *  }}}
 *
 *  Or, as a reactive random value, by subscribing to events produced when `emit` is called:
 *
 *  {{{
 *  val evens = r.react.int().filter(_ % 2 == 0)
 *  }}}
 *
 *  Above, `evens` contains random values.
 */
class ReactRandom(private val jucRandom: java.util.Random) {
  self =>

  private val rand = new Random(jucRandom)

  def boolean() = rand.nextBoolean()

  def int() = rand.nextInt()

  def int(n: Int) = rand.nextInt(n)

  def float() = rand.nextFloat()

  def long() = rand.nextLong()

  def long(n: Int) = math.abs(rand.nextLong()) % n

  def double() = rand.nextDouble()

  def string(length: Int) = rand.nextString(length)

  def this(seed: Long) = this(new java.util.Random(seed))

  def generate() = react.randomEmitter.react()

  def unreact() = react.randomEmitter.unreact()

  object react {
    private[calc] val randomEmitter = new Events.SideEffectEmitter[Unit](() => ())
    def int() = randomEmitter.map(_ => self.int())
    def int(n: Int) = randomEmitter.map(_ => self.int(n))
    def long() = randomEmitter.map(_ => self.long())
    def long(n: Int) = randomEmitter.map(_ => self.long(n))
    def double() = randomEmitter.map(_ => self.double())
    def string(length: Int) = randomEmitter.map(_ => self.string(length))
  }

}


