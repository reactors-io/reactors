package io.reactors.common.concurrent






class CacheTrie[K, V] {
  import CacheTrie.Node

  @volatile
  private var cache: Array[Node[K, V]] = null

  def spread(h: Int): Int = {
    h ^ (h >>> 16)
  }

  def cacheAt(hash: Int): Node[K, V] = ???

  def lookup(key: K): V = {
    val hash = spread(key.##)
    val node = cacheAt(hash)
    ???
  }
}


object CacheTrie {
  abstract class Node[K, V]
}
