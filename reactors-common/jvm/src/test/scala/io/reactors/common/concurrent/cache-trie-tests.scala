package io.reactors.common.concurrent



import io.reactors.test._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Properties
import org.scalatest.FunSuite
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



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
  val sizes = detChoose(0, 4096)

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

  property("concurrent insert and slow lookup") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val completed = Promise[Seq[String]]()
      val trie = new CacheTrie[String, String]
      val inserter = thread {
        for (i <- 0 until sz) {
          trie.insert(i.toString, i.toString)
        }
      }
      val looker = thread {
        val found = mutable.Buffer[String]()
        for (i <- (0 until sz).reverse) {
          val result = trie.slowLookup(i.toString)
          if (result != null) found += result
        }
        completed.success(found)
      }
      inserter.join()
      looker.join()
      val seen = Await.result(completed.future, 10.seconds)
      seen.reverse == (0 until seen.length).map(_.toString)
    }
  }

  property("two concurrent inserts") = forAllNoShrink(sizes) {
    sz =>
    stackTraced {
      val trie = new CacheTrie[Integer, Int]
      val inserter1 = thread {
        for (i <- 0 until sz) {
          trie.insert(i, i)
        }
      }
      val inserter2 = thread {
        for (i <- 0 until sz) {
          trie.insert(sz + i, sz + i)
        }
      }
      inserter1.join()
      inserter2.join()
      for (i <- 0 until sz) {
        assert(trie.lookup(i) == i)
        assert(trie.lookup(sz + i) == (sz + i))
      }
      true
    }
  }
}
