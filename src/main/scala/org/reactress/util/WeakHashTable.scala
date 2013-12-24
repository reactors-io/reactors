package org.reactress
package util



import java.lang.ref.{WeakReference => WeakRef}



class WeakHashTable[M <: AnyRef] {
  protected[reactress] var table = new Array[WeakRef[M]](32)
  protected[reactress] var size = 0
  protected[reactress] var threshold = 2

  private def index(h: Int) = h & (table.length - 1)

  private def findEntryImpl(mux: M): WeakRef[M] = {
    var h = index(mux.hashCode)
    var entry = table(h)
    while (null != entry && entry.get != mux) {
      h = (h + 1) % table.length
      entry = table(h)
    }
    entry
  }

  def addEntry(mux: M) : Boolean = {
    var h = index(mux.hashCode)
    var entry = table(h)
    while (null != entry) {
      if (entry.get == mux) return false
      h = (h + 1) % table.length
      entry = table(h)
    }
    table(h) = new WeakRef(mux)
    size = size + 1
    if (size >= threshold) growTable()
    true
  }

  protected[reactress] def removeEntryAt(idx: Int, mux: M): Boolean = {
    def precedes(i: Int, j: Int) = {
      val d = table.length >> 1
      if (i <= j) j - i < d
      else i - j > d
    }
    var h = idx
    var entry = table(h)
    while (null != entry) {
      if (entry.get == mux) {
        var h0 = h
        var h1 = (h0 + 1) % table.length
        var found = false
        while (!found) {
          val elem = if (table(h1) == null) null else table(h1).get
          if (elem != null) {
            val h2 = index(elem.hashCode)
            if (h2 != h1 && precedes(h2, h0)) {
              table(h0) = table(h1)
              h0 = h1
            }
            h1 = (h1 + 1) % table.length
          } else found = true
        }
        table(h0) = null
        size -= 1
        return true
      }
      h = (h + 1) % table.length
      entry = table(h)
    }
    false
  }

  def removeEntry(mux: M): Boolean = {
    val h = index(mux.hashCode)
    removeEntryAt(h, mux)
  }

  private def growTable() {
    val oldtable = table
    table = new Array[WeakRef[M]](table.length * 2)
    size = 0
    threshold = (table.length * 0.45).toInt
    var i = 0
    while (i < oldtable.length) {
      val entry = oldtable(i)
      if (null != entry && null != entry.get) addEntry(entry.get)
      i += 1
    }
  }

  override def toString = getClass.getSimpleName + table.filter(_ != null).map(_.get).mkString("(", ", ", ")")
}
