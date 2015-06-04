package scala.reactive
package container



import scala.collection._



class AbelianCatamorph[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
  (val get: S => T, val zero: T, val op: (T, T) => T, val inv: (T, T) => T)
  (implicit val canT: Arrayable[T], val canS: Arrayable[S])
extends RCatamorph[T, S] with RBuilder[S, AbelianCatamorph[T, S]] {
  import AbelianCatamorph._

  private[reactive] var elements: RHashValMap[S, T] = null
  private var insertsEmitter: Events.Emitter[S] = null
  private var removesEmitter: Events.Emitter[S] = null
  private var value: RCell[T] = null

  def inserts: Events[S] = insertsEmitter

  def removes: Events[S] = removesEmitter

  def init(z: T) {
    elements = RHashValMap[S, T]
    insertsEmitter = new Events.Emitter[S]
    removesEmitter = new Events.Emitter[S]
    value = RCell[T](zero)
  }

  init(zero)

  def signal = value

  def +=(v: S): Boolean = {
    if (!elements.contains(v)) {
      val x = get(v)
      elements(v) = x
      value := op(value(), x)
      insertsEmitter.react(v)
      true
    } else false
  }

  def -=(v: S): Boolean = {
    if (elements.contains(v)) {
      val y = elements(v)
      elements.remove(v)
      value := inv(value(), y)
      removesEmitter.react(v)
      true
    } else false
  }

  def container = this

  def push(v: S): Boolean = {
    if (elements.contains(v)) {
      val y = elements(v)
      val x = get(v)
      elements(v) = x
      value := op(inv(value(), y), x)
      true
    } else false
  }

  def size = elements.size

  def foreach(f: S => Unit) = elements.foreachPair((k, v) => f(k))
}


object AbelianCatamorph {

  def apply[@spec(Int, Long, Double) T](implicit g: Abelian[T], can: Arrayable[T]) = {
    new AbelianCatamorph[T, T](v => v, g.zero, g.operator, g.inverse)
  }

  implicit def factory[@spec(Int, Long, Double) T: Abelian: Arrayable] =
    new RBuilder.Factory[T, AbelianCatamorph[T, T]] {
      def apply() = AbelianCatamorph[T]
    }

}