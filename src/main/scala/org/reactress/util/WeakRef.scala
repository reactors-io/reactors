package org.reactress
package util



import java.lang.ref.WeakReference



class WeakRef[T](x: T) extends WeakReference[T](x) {
  var invalidated = false
}