package scala.reactive
package container



import scala.reflect.ClassTag



class RHashSet[@spec(Int, Long, Double) T](
  implicit val arrayable: Arrayable[T]
) extends RSet[T] with RBuilder[T, RHashSet[T]] {
  self =>

  private var table: Array[T] = null
  private var sz = 0
  private[reactive] var insertsEmitter: Reactive.Emitter[T] = null
  private[reactive] var removesEmitter: Reactive.Emitter[T] = null

  protected def init(ee: Arrayable[T]) {
    table = arrayable.newArray(RHashSet.initSize)
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
  }

  init(arrayable)

  def inserts: Reactive[T] = insertsEmitter
  def removes: Reactive[T] = removesEmitter

  def builder: RBuilder[T, RHashSet[T]] = this

  def +=(elem: T) = {
    self add elem
  }

  def -=(elem: T) = {
    self remove elem
  }

  def container = self

  val react = new RHashSet.Lifted[T](this)

  def foreach(f: T => Unit) {
    var i = 0
    while (i < table.length) {
      val k = table(i)
      if (k != arrayable.nil) {
        f(k)
      }
      i += 1
    }
  }

  private def lookup(k: T): Boolean = {
    var pos = index(k)
    val nil = arrayable.nil
    var curr = table(pos)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % table.length
      curr = table(pos)
    }

    if (curr == nil) false
    else true
  }

  private def insert(k: T, notify: Boolean = true): Boolean = {
    checkResize()

    var pos = index(k)
    val nil = arrayable.nil
    var curr = table(pos)
    assert(k != nil)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % table.length
      curr = table(pos)
    }

    table(pos) = k
    val added = curr == nil
    if (added) sz += 1
    else removesEmitter += k
    if (notify) insertsEmitter += k

    added
  }

  private def delete(k: T): Boolean = {
    var pos = index(k)
    val nil = arrayable.nil
    var curr = table(pos)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % table.length
      curr = table(pos)
    }

    if (curr == nil) false
    else {
      var h0 = pos
      var h1 = (h0 + 1) % table.length
      while (table(h1) != nil) {
        val h2 = index(table(h1))
        if (h2 != h1 && before(h2, h0)) {
          table(h0) = table(h1)
          h0 = h1
        }
        h1 = (h1 + 1) % table.length
      }

      table(h0) = arrayable.nil
      sz -= 1
      removesEmitter += k

      true
    }
  }

  private def checkResize() {
    if (sz * 1000 / RHashSet.loadFactor > table.length) {
      val otable = table
      val ncapacity = table.length * 2
      table = arrayable.newArray(ncapacity)
      sz = 0

      var pos = 0
      val nil = arrayable.nil
      while (pos < otable.length) {
        val curr = otable(pos)
        if (curr != nil) {
          insert(curr, false)
        }

        pos += 1
      }
    }
  }

  private def before(i: Int, j: Int) = {
    val d = table.length >> 1
    if (i <= j) j - i < d
    else i - j > d
  }

  private def index(k: T): Int = {
    val hc = k.##
    math.abs(scala.util.hashing.byteswap32(hc)) % table.length
  }

  def apply(key: T): Boolean = lookup(key)

  def contains(key: T): Boolean = lookup(key)

  def add(key: T): Boolean = insert(key)

  def remove(key: T): Boolean = delete(key)

  def clear() {
    var pos = 0
    val nil = arrayable.nil
    while (pos < table.length) {
      val elem = table(pos)
      if (elem != nil) {
        table(pos) = arrayable.nil
        sz -= 1
        removesEmitter += elem
      }

      pos += 1
    }
  }

  def size: Int = sz

}


object RHashSet {

  def apply[@spec(Int, Long, Double) T: Arrayable]() = new RHashSet[T]

  class Lifted[@spec(Int, Long, Double) T](val container: RHashSet[T]) extends RSet.Lifted[T]

  val initSize = 16

  val loadFactor = 450

  implicit def factory[@spec(Int, Long, Double) T: Arrayable] = new RBuilder.Factory[T, RHashSet[T]] {
    def apply() = RHashSet[T]
  }

}





