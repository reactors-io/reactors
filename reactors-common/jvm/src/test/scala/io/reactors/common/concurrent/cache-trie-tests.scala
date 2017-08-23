package io.reactors.common.concurrent



import io.reactors.common.concurrent.CacheTrie.ANode
import io.reactors.common.concurrent.CacheTrie.CacheNode
import io.reactors.test._
import org.scalacheck.Prop.forAllNoShrink
import org.scalacheck.Properties
import org.scalatest.FunSuite
import scala.collection._
import scala.concurrent._
import scala.concurrent.duration._



class CacheTrieTest extends FunSuite {
  test("insert many elements") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 1000) {
      trie.insert(i.toString, i)
    }
  }

  test("insert, then find the element") {
    val trie = new CacheTrie[String, Integer]
    trie.insert("key", 11)
    assert(trie.lookup("key") == 11)
  }

  test("insert 17 elements, and find them") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 17) {
      trie.insert(i.toString, i)
    }
    for (i <- 0 until 17) {
      assert(trie.lookup(i.toString) == i)
    }
    println(trie.debugTree)
  }

  test("insert 1000 elements, and find them while inserting") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 1000) {
      trie.insert(i.toString, i)
      assert(trie.lookup(i.toString) == i)
    }
    for (i <- 0 until 1000) {
      assert(trie.lookup(i.toString) == i)
    }
  }

  test("remove a single element") {
    val trie = new CacheTrie[String, Integer]
    trie.insert("single", 7)
    assert(trie.lookup("single") == 7)
    assert(trie.remove("single") == 7)
    assert(trie.get("single") == None)
  }

  test("insert 20 elements, and remove them") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 20) {
      trie.insert(i.toString, i)
      assert(trie.lookup(i.toString) == i)
    }
    for (i <- 0 until 20) {
      assert(trie.remove(i.toString) == i)
      assert(trie.get(i.toString) == None)
    }
  }

  test("insert 1000 elements, and remove them") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 1000) trie.insert(i.toString, i)
    for (i <- 0 until 1000) assert(trie.remove(i.toString) == i)
    for (i <- 0 until 1000) assert(trie.get(i.toString) == None)
  }

  test("remove an int does not return 0") {
    val trie = new CacheTrie[String, Integer]
    assert(trie.remove("key") == null)
  }

  test("remove a lot of elements correctly") {
    val trie = new CacheTrie[String, Integer]
    for (i <- 0 until 100000) {
      trie.insert("special", -1)
      trie.insert(i.toString, i)
      assert(trie.remove("special") == -1)
    }
    for (i <- 0 until 100000) {
      assert(trie.remove(i.toString) == i)
      assert(trie.get(i.toString) == None)
    }
  }

  test("trie cache must find the elements correctly") {
    val trie = new CacheTrie[String, Integer]
    val size = 1000000
    for (i <- 0 until size) {
      trie.insert(i.toString, i)
    }
    for (i <- 0 until size) {
      assert(trie.lookup(i.toString) == i)
    }
    for (i <- 0 until size) {
      assert(trie.lookup(i.toString) == i)
    }
  }
}


class CacheTrieCheck extends Properties("CacheTrie") with ExtendedProperties {
  val sizes = detChoose(0, 65535)
  val smallSizes = detChoose(0, 512)
  val numThreads = detChoose(2, 32)

  class PoorHash(val x: Int) {
    override def hashCode = x & 0xff
    override def equals(that: Any) = that match {
      case that: PoorHash => that.x == x
      case _ => false
    }
  }

  private def validateCache[K <: AnyRef, V <: AnyRef](
    trie: CacheTrie[K, V], sz: Int, allowNull: Boolean
  ): Unit = {
    return
    var cache = trie.debugReadCache
    if (sz > 32000 || cache != null) {
      do {
        val info = cache(0).asInstanceOf[CacheNode]
        val cacheLevel = info.level
        def str(x: AnyRef): String = x match {
          case an: Array[AnyRef] => ANode.toString(an)
          case null => "null"
          case _ => x.toString
        }
        def check(node: Array[AnyRef], hash: Int, level: Int): Unit = {
          if (level == cacheLevel) {
            val pos = 1 + hash
            val cachee = cache(pos)
            assert((allowNull && cachee == null) || cachee == node,
              s"at level $level, ${str(cachee)} vs ${str(node)}")
            return
          }
          var i = 0
          while (i < node.length) {
            val old = node(i)
            if (old.isInstanceOf[Array[AnyRef]]) {
              val an = old.asInstanceOf[Array[AnyRef]]
              check(an, (i << level) + hash, level + 4)
            }
            i += 1
          }
        }
        check(trie.debugReadRoot, 0, 0)
        cache = info.parent
      } while (cache != null)
    }
  }

  // property("insert and lookup") = forAllNoShrink(sizes) {
  //   sz =>
  //   stackTraced {
  //     val trie = new CacheTrie[String, Integer]
  //     for (i <- 0 until sz) {
  //       trie.insert(i.toString, i)
  //       assert(trie.apply(i.toString) == i)
  //     }
  //     for (i <- 0 until sz) {
  //       assert(trie.apply(i.toString) == i)
  //     }
  //     for (i <- 0 until sz) {
  //       assert(trie.apply(i.toString) == i)
  //     }
  //     validateCache(trie, sz, false)
  //     true
  //   }
  // }

  // property("insert and slow lookup") = forAllNoShrink(sizes) {
  //   sz =>
  //   stackTraced {
  //     val trie = new CacheTrie[String, Integer]
  //     for (i <- 0 until sz) {
  //       trie.insert(i.toString, i)
  //       assert(trie.slowLookup(i.toString) == i)
  //     }
  //     for (i <- 0 until sz) {
  //       assert(trie.slowLookup(i.toString) == i)
  //     }
  //     true
  //   }
  // }

  // property("insert and remove") = forAllNoShrink(sizes) {
  //   sz =>
  //   stackTraced {
  //     val trie = new CacheTrie[String, Integer]
  //     for (i <- 0 until sz) trie.insert(i.toString, i)
  //     for (i <- 0 until sz) assert(trie.remove(i.toString) == i)
  //     true
  //   }
  // }

  // property("concurrent insert and slow lookup") = forAllNoShrink(sizes) {
  //   sz =>
  //   stackTraced {
  //     val completed = Promise[Seq[String]]()
  //     val trie = new CacheTrie[String, String]
  //     val inserter = thread {
  //       for (i <- 0 until sz) {
  //         trie.insert(i.toString, i.toString)
  //       }
  //     }
  //     val looker = thread {
  //       val found = mutable.Buffer[String]()
  //       for (i <- (0 until sz).reverse) {
  //         val result = trie.slowLookup(i.toString)
  //         if (result != null) found += result
  //       }
  //       completed.success(found)
  //     }
  //     inserter.join()
  //     looker.join()
  //     val seen = Await.result(completed.future, 10.seconds)
  //     seen.reverse == (0 until seen.length).map(_.toString)
  //   }
  // }

  // property("two threads concurrent inserts") = forAllNoShrink(sizes) {
  //   sz =>
  //   stackTraced {
  //     val trie = new CacheTrie[Integer, Integer]
  //     val inserter1 = thread {
  //       for (i <- 0 until sz) {
  //         trie.insert(i, i)
  //       }
  //     }
  //     val inserter2 = thread {
  //       for (i <- 0 until sz) {
  //         trie.insert(sz + i, sz + i)
  //       }
  //     }
  //     inserter1.join()
  //     inserter2.join()
  //     for (i <- 0 until sz) {
  //       val first = trie.apply(i)
  //       assert(first == i, first)
  //       val second = trie.apply(sz + i)
  //       assert(second == (sz + i), second)
  //     }
  //     validateCache(trie, sz, false)
  //     true
  //   }
  // }

  // property("many threads slow concurrent inserts") = forAllNoShrink(numThreads, sizes) {
  //   (n, sz) =>
  //   stackTraced {
  //     val trie = new CacheTrie[Integer, Integer]
  //     val separated = (0 until sz).grouped(sz / n + 1).toSeq
  //     val batches = separated ++ Array.fill(n - separated.size)(Nil)
  //     assert(batches.size == n)
  //     val threads = for (k <- 0 until n) yield thread {
  //       for (i <- batches(k)) {
  //         trie.slowInsert(i, i)
  //       }
  //     }
  //     threads.foreach(_.join())
  //     for (i <- 0 until sz) {
  //       assert(trie.apply(i) == i)
  //     }
  //     validateCache(trie, sz, false)
  //     true
  //   }
  // }

  // property("many threads concurrent inserts") = forAllNoShrink(numThreads, sizes) {
  //   (n, sz) =>
  //   stackTraced {
  //     val trie = new CacheTrie[Integer, Integer]
  //     val separated = (0 until sz).grouped(sz / n + 1).toSeq
  //     val batches = separated ++ Array.fill(n - separated.size)(Nil)
  //     assert(batches.size == n)
  //     val threads = for (k <- 0 until n) yield thread {
  //       for (i <- batches(k)) {
  //         trie.insert(i, i)
  //       }
  //     }
  //     threads.foreach(_.join())
  //     for (i <- 0 until sz) {
  //       assert(trie.apply(i) == i)
  //     }
  //     validateCache(trie, sz, false)
  //     true
  //   }
  // }

  // property("many threads concurrent inserts, small") =
  //   forAllNoShrink(numThreads, smallSizes) {
  //     (n, sz) =>
  //     stackTraced {
  //       val trie = new CacheTrie[Integer, Integer]
  //       val separated = (0 until sz).grouped(sz / n + 1).toSeq
  //       val batches = separated ++ Array.fill(n - separated.size)(Nil)
  //       assert(batches.size == n)
  //       val threads = for (k <- 0 until n) yield thread {
  //         for (i <- batches(k)) {
  //           trie.insert(i, i)
  //         }
  //       }
  //       threads.foreach(_.join())
  //       for (i <- 0 until sz) {
  //         assert(trie.apply(i) == i)
  //       }
  //       validateCache(trie, sz, false)
  //       true
  //     }
  //   }

  // property("many threads concurrent removes") = forAllNoShrink(numThreads, sizes) {
  //   (n, sz) =>
  //   stackTraced {
  //     val trie = new CacheTrie[Integer, Integer]
  //     for (i <- 0 until sz) {
  //       trie.insert(i, i)
  //     }
  //     val separated = (0 until sz).grouped(sz / n + 1).toSeq
  //     val batches = separated ++ Array.fill(n - separated.size)(Nil)
  //     assert(batches.size == n)
  //     val completed = new Array[Boolean](n)
  //     val threads = for (k <- 0 until n) yield thread {
  //       for (i <- batches(k)) assert(trie.remove(i) == i)
  //       completed(k) = true
  //     }
  //     threads.foreach(_.join())
  //     assert(completed.forall(_ == true))
  //     for (i <- 0 until sz) assert(trie.get(i) == None)
  //     true
  //   }
  // }

  property("concurrent inserts and removes") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[Integer, Integer]
      val separated = (0 until sz).grouped(sz / n + 1).toSeq
      val batches = separated ++ Array.fill(n - separated.size)(Nil)
      assert(batches.size == n)
      val completed = new Array[Boolean](2 * n)
      val inserters = for (k <- 0 until n) yield thread {
        for (i <- batches(k)) trie.insert(i, i)
        completed(k) = true
      }
      val removers = for (k <- 0 until n) yield thread {
        for (i <- batches(k)) {
          while (trie.remove(i) == null) {}
        }
        completed(n + k) = true
      }
      inserters.foreach(_.join())
      removers.foreach(_.join())
      assert(completed.forall(_ == true), completed.mkString(", "))
      for (i <- 0 until sz) {
        val result = trie.lookup(i)
        assert(result == null, s"$i -> $result")
      }
      true
    }
  }

  property("concurrent inserts, poor hash code") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[PoorHash, Integer]
      val separated = (0 until sz).grouped(sz / n + 1).toSeq
      val batches = separated ++ Array.fill(n - separated.size)(Nil)
      assert(batches.size == n)
      val threads = for (b <- batches) yield thread {
        for (i <- b) trie.insert(new PoorHash(i), i)
      }
      threads.foreach(_.join())
      for (i <- 0 until sz) assert(trie.apply(new PoorHash(i)) == i)
      true
    }
  }

  property("concurrent inserts on same keys") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[Integer, Integer]
      val threads = for (k <- 0 until n) yield thread {
        for (i <- 0 until sz) {
          trie.insert(i, i)
        }
      }
      threads.foreach(_.join())
      for (i <- 0 until sz) {
        assert(trie.apply(i) == i)
      }
      validateCache(trie, sz, false)
      true
    }
  }

  property("concurrent string inserts") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[String, Integer]
      val threads = for (k <- 0 until n) yield thread {
        for (i <- 0 until sz) {
          trie.insert(i.toString, i)
        }
      }
      threads.foreach(_.join())
      for (i <- 0 until sz) {
        assert(trie.apply(i.toString) == i)
      }
      for (i <- 0 until sz) {
        assert(trie.apply(i.toString) == i)
      }
      validateCache(trie, sz, false)
      true
    }
  }

  property("concurrent inserts on rotated keys") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[Integer, Integer]
      val threads = for (k <- 0 until n) yield thread {
        val rotation = sz / n * ((k / 2) % 4)
        val values = 0 until sz
        val rotated = values.drop(rotation) ++ values.take(rotation)
        for (i <- rotated) {
          trie.insert(i, i)
        }
      }
      threads.foreach(_.join())
      for (i <- 0 until sz) {
        assert(trie.apply(i) == i)
      }
      validateCache(trie, sz, false)
      true
    }
  }

  property("concurrent overwriting inserts") = forAllNoShrink(numThreads, sizes) {
    (n, sz) =>
    stackTraced {
      val trie = new CacheTrie[Integer, Integer]
      for (i <- 0 until sz) trie.insert(i, i)
      val threads = for (k <- 0 until n) yield thread {
        for (i <- 0 until sz) {
          trie.insert(i, -i)
        }
      }
      threads.foreach(_.join())
      for (i <- 0 until sz) {
        assert(trie.apply(i) == -i)
      }
      validateCache(trie, sz, false)
      true
    }
  }

}
