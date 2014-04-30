package org.reactress






package object isolate {

  trait EventObserver[@spec(Int, Long, Double) T] {
    def onEvent(event: T): Unit
    def onSysEvent(event: Isolate.SysEvent): Unit
  }

}
