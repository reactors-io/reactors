package org.reactress



import scala.annotation.tailrec
import scala.util.DynamicVariable
import isolate._



trait SysIsolate[T <: AnyRef]
extends ReactIsolate[T, AnyRef] {

  final val sysEvents: Reactive[SysIsolate.Event] = source collect {
    case e: SysIsolate.Event => e
  }

  final val events: Reactive[T] = source collect {
    case e if !e.isInstanceOf[SysIsolate.Event] => e.asInstanceOf[T]
  }

  def later: Enqueuer[T] = frame.eventQueue

}


object SysIsolate {

  sealed trait Event
  case object Start extends Event
  case object EmptyQueue extends Event
  case object Terminate extends Event

  trait Looper[T <: AnyRef]
  extends SysIsolate[T] {
    val fallback: Signal[Option[T]]

    def initialize() {
      val feedback = new Reactive.Emitter[T]
      channel.attach(feedback)
      react <<= sysEvents onCase {
        case SysIsolate.Start | SysIsolate.EmptyQueue =>
          if (fallback().nonEmpty) feedback += fallback().get
          else {
            feedback.close()
            channel.seal()
          }
      }
    }
    initialize()
  }

}
