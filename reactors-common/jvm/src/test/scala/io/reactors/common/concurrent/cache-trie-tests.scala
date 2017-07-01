package io.reactors.common.concurrent



import io.reactors.test._
import org.scalacheck.Properties
import org.scalacheck.Prop.forAllNoShrink
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


class CacheTrieCheck extends Properties("CacheTrie") with ExtendedProperties {
  val sizes = detChoose(0, 512)

  property("insert and lookup") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val trie = new CacheTrie[String, Int]
      for (i <- 0 until sz) {
        trie.insert(i.toString, i)
        assert(trie.lookup(i.toString) == i)
      }
      for (i <- 0 until sz) {
        assert(trie.lookup(i.toString) == i)
      }
      true
    }
  }
}
