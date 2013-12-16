package org.reactress
package container






trait ReactCatamorph[@spec(Int, Long, Double) T, @spec(Int, Long, Double) S]
extends Signal.Default[T] with ReactContainer[S] {

  def +=(v: S): Boolean

  def -=(v: S): Boolean

  def push(v: S): Boolean

}


object ReactCatamorph {

}