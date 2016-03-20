package io.reactors
package algebra



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
class PowerRandom(private val jucRandom: java.util.Random) {
  self =>

  private val rand = new Random(jucRandom)

  def this(seed: Long) = this(new java.util.Random(seed))

  def boolean() = rand.nextBoolean()

  def int() = rand.nextInt()

  def int(n: Int) = rand.nextInt(n)

  def float() = rand.nextFloat()

  def long() = rand.nextLong()

  def long(n: Int) = math.abs(rand.nextLong()) % n

  def double() = rand.nextDouble()

  def string(length: Int) = rand.nextString(length)

}


