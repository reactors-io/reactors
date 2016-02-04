package org.reactors






package common {

  class Ref[M >: Null <: AnyRef](private var x: M) {
    def get = x
    def clear() = x = null
  }

}
