package org.reactors



import java.util.concurrent.atomic.AtomicLong
import scala.collection.concurrent.TrieMap
import scala.reflect.ClassTag



package common {

  class Ref[M >: Null <: AnyRef](private var x: M) {
    def get = x
    def clear() = x = null
  }

}


package object common {

  private val counterMap = TrieMap[Class[_], AtomicLong]()

  final def freshId[C: ClassTag]: Long = {
    val cls = implicitly[ClassTag[C]].runtimeClass
    if (!(counterMap contains cls)) counterMap.putIfAbsent(cls, new AtomicLong)
    val counter = counterMap(cls)
    counter.incrementAndGet()
  }

}
