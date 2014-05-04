package org.reactress
package io



import java.net.URI
import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import scala.collection._
import scala.collection.convert.decorateAsScala._



class ReactFileSystem(val uri: URI) extends Isolate[ReactFileSystem.Command] {
  import ReactFileSystem._

  private val fs = FileSystems.getFileSystem(uri)
  private val watcher = fs.newWatchService
  private val subscriptions = concurrent.TrieMap[WatchKey, SubscriptionInfo]()
  private val directories = mutable.Map[ReactPath, WatchKey]()
  private val poller = new ReactFileSystem.Poller(watcher, subscriptions)

  react <<= source onCase {
    case ReactFileSystem.Watch(dir, channel) =>
      val key = dir.path.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
      val emitter = new Reactive.Emitter[Event]
      channel.attach(emitter)
      directories(dir) = key
      subscriptions(key) = SubscriptionInfo(dir, emitter, channel)
    case ReactFileSystem.Unwatch(dir) =>
      directories.remove(dir) match {
        case None =>
        case Some(key) =>
          key.cancel()
          subscriptions.remove(key)
      }
  }

  react <<= sysEvents onCase {
    case IsolateStarted =>
      poller.start()
    case IsolateTerminated =>
      watcher.close()
  }
}


object ReactFileSystem {

  class Poller(val watcher: WatchService, val subscriptions: concurrent.Map[WatchKey, SubscriptionInfo])
  extends Thread {
    setName(s"ReactiveFileSystem-WatchThread-${util.freshId[Poller]}")
    setDaemon(true)

    override def run() {
      try while (true) {
        val key = watcher.take()
        subscriptions.get(key) match {
          case None => // not added yet, just ignore it
          case Some(SubscriptionInfo(dir, emitter, _)) =>
            for (rawe <- key.pollEvents.asScala) {
              val event = rawe.asInstanceOf[WatchEvent[Path]]
              val kind = event.kind
              val path = ReactPath(dir.path.resolve(event.context))
              kind match {
                case ENTRY_CREATE => emitter += Created(path)
                case ENTRY_MODIFY => emitter += Modified(path)
                case ENTRY_DELETE => emitter += Deleted(path)
                case _ => // no need to do anything
              }
            }
        }
      } catch {
        case e: ClosedWatchServiceException =>
          // we are done -- this means the reactive file system terminated
      }
    }
  }

  private[reactress] case class SubscriptionInfo(dir: ReactPath, emitter: Reactive.Emitter[Event], channel: Channel[Event])

  sealed trait Command
  case class Watch(dir: ReactPath, channel: Channel[Event]) extends Command
  case class Unwatch(dir: ReactPath) extends Command

  sealed trait Event
  case class Created(path: ReactPath) extends Event
  case class Modified(path: ReactPath) extends Event
  case class Deleted(path: ReactPath) extends Event

}
