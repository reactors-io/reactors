package org.reactress
package container



import scala.reflect.ClassTag



class ReactSet[@spec(Int, Long) T](
  implicit val emptyElem: ReactSet.Empty[T]
) extends ReactContainer[T] with ReactBuilder[T, ReactSet[T]] {
  self =>

  private var table: Array[T] = null
  private var sz = 0
  private[reactress] var insertsEmitter: Reactive.Emitter[T] = null
  private[reactress] var removesEmitter: Reactive.Emitter[T] = null
  private[reactress] var clearsEmitter: Reactive.Emitter[Unit] = null

  protected def init(ee: ReactSet.Empty[T]) {
    table = emptyElem.newEmptyArray(ReactSet.initSize)
    insertsEmitter = new Reactive.Emitter[T]
    removesEmitter = new Reactive.Emitter[T]
    clearsEmitter = new Reactive.Emitter[Unit]
  }

  init(emptyElem)

  def inserts: Reactive[T] = insertsEmitter
  def removes: Reactive[T] = removesEmitter
  def clears: Reactive[Unit] = clearsEmitter

  def builder: ReactBuilder[T, ReactSet[T]] = this

  def +=(elem: T) = {
    self add elem
    this
  }

  def -=(elem: T) = {
    self remove elem
    this
  }

  def container = self

  private def lookup(k: T): Boolean = {
    var pos = index(k)
    val nil = emptyElem.nil
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
    val nil = emptyElem.nil
    var curr = table(pos)
    assert(k != nil)

    while (curr != nil && curr != k) {
      pos = (pos + 1) % table.length
      curr = table(pos)
    }

    table(pos) = k
    val added = curr == nil
    if (added) {
      sz += 1
      if (notify) insertsEmitter += k
    }

    added
  }

  private def delete(k: T): Boolean = {
    var pos = index(k)
    val nil = emptyElem.nil
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

      table(h0) = emptyElem.nil
      sz -= 1
      removesEmitter += k

      true
    }
  }

  private def checkResize() {
    if (sz * 1000 / ReactSet.loadFactor > table.length) {
      val otable = table
      val ncapacity = table.length * 2
      table = emptyElem.newEmptyArray(ncapacity)
      sz = 0

      var pos = 0
      val nil = emptyElem.nil
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

  def add(key: T): Unit = insert(key)

  def remove(key: T): Boolean = delete(key)

  def clear() {
    var pos = 0
    val nil = emptyElem.nil
    while (pos < table.length) {
      if (table(pos) != nil) {
        table(pos) = emptyElem.nil
        sz -= 1
      }

      pos += 1
    }

    clearsEmitter += ()
  }

  def size: Int = sz

}


object ReactSet {

  def apply[@spec(Int, Long) T: Empty]() = new ReactSet[T]

  val initSize = 16

  val loadFactor = 450

  abstract class Empty[@spec(Int, Long) T] {
    val classTag: ClassTag[T]
    val nil: T
    def newEmptyArray(sz: Int): Array[T]
  }

  implicit def emptyRef[T >: Null <: AnyRef: ClassTag]: Empty[T] = new Empty[T] {
    val classTag = implicitly[ClassTag[T]]
    val nil: T = null
    def newEmptyArray(sz: Int) = Array.fill[T](sz)(nil)
  }

  implicit val emptyLong: Empty[Long] = new Empty[Long] {
    val classTag = implicitly[ClassTag[Long]]
    val nil = Long.MinValue
    def newEmptyArray(sz: Int) = Array.fill[Long](sz)(nil)
  }

  implicit val emptyInt: Empty[Int] = new Empty[Int] {
    val classTag = implicitly[ClassTag[Int]]
    val nil = Int.MinValue
    def newEmptyArray(sz: Int) = Array.fill[Int](sz)(nil)
  }

  implicit def factory[@spec(Int, Long) S: Empty] = new ReactBuilder.Factory[S, ReactSet[S]] {
    def create() = new ReactSet[S]
  }

}





