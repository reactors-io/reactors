package org.reactress



import java.lang.ref.{WeakReference => WeakRef}



class WeakBuffer[M <: AnyRef](initialSize: Int = 4) {
  protected[reactress] var array = new Array[WeakRef[M]](initialSize)
  protected[reactress] var size = 0

  def apply(idx: Int) = array(idx).get

  def addEntry(elem: M) {
    if (size == array.length) growArray()
    array(size) = new WeakRef(elem)
    size += 1
  }

  def removeEntry(elem: M) {
    var i = 0
    while (i < size) {
      if (array(i).get eq elem) {
        removeEntryAt(i)
        i = size
      }
      i += 1
    }
  }

  protected[reactress] def addRef(ref: WeakRef[M]) {
    if (size == array.length) growArray()
    array(size) = ref
    size += 1
  }

  def removeEntryAt(idx: Int) {
    if (idx < size) {
      array(idx) = array(size - 1)
      array(size - 1) = null
      size -= 1
    } else throw new IndexOutOfBoundsException
  }

  private def growArray() {
    val oldarray = array
    array = new Array[WeakRef[M]](array.length * 2)
    size = 0
    while (size < oldarray.length) {
      val entry = oldarray(size)
      if (entry != null && entry.get != null) array(size) = entry
      size += 1
    }
  }

  override def toString = getClass.getSimpleName + array.filter(_ != null).map(_.get).mkString("(", ", ", ")")
}
