package org.reactress



import java.util.concurrent.atomic.AtomicLong
import scala.collection._
import scala.reflect._



package object util {

  val unsafe = scala.concurrent.util.Unsafe.instance

  private val counterMap = concurrent.TrieMap[Class[_], AtomicLong]()

  final def freshId[C: ClassTag]: Long = {
    val cls = implicitly[ClassTag[C]].erasure
    if (!(counterMap contains cls)) counterMap.putIfAbsent(cls, new AtomicLong)
    val counter = counterMap(cls)
    counter.incrementAndGet()
  }

}
