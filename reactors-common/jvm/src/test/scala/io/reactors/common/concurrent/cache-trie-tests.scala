package io.reactors.common.concurrent



import org.scalatest.FunSuite



class CacheTrieTest extends FunSuite {
  test("should insert many elements") {
    val trie = new CacheTrie[String, Int]
    for (i <- 0 until 1000) {
      trie.insert(i.toString, i)
    }
  }

  test("should insert, then find the element") {
    val trie = new CacheTrie[String, Int]
    trie.insert("key", 11)
    assert(trie.lookup("key") == 11)
  }

  test("should insert 17 elements, and find them") {
    val trie = new CacheTrie[String, Int]
    for (i <- 0 until 17) {
      trie.insert(i.toString, i)
    }
    for (i <- 0 until 17) {
      assert(trie.lookup(i.toString) == i)
    }
    println(trie.debugTree)
  }

  test("should insert 1000 elements, and find them while inserting") {
    val trie = new CacheTrie[String, Int]
    for (i <- 0 until 1000) {
      trie.insert(i.toString, i)
      assert(trie.lookup(i.toString) == i)
    }
    for (i <- 0 until 1000) {
      assert(trie.lookup(i.toString) == i)
    }
  }
}
