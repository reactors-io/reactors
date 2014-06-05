package scala.reactive
package io



import java.nio.file.Path
import calc.Injection



class ReactDir private[reactive] (
  val path: Path,
  val commands: Reactive.Emitter[ReactFileSystem.Command],
  val events: Reactive[ReactFileSystem.Event]
) extends ReactRecord {
  val creations = events.collect { case ReactFileSystem.Created(path) => path }
  val modifications = events.collect { case ReactFileSystem.Modified(path) => path }
  val deletions = events.collect { case ReactFileSystem.Deleted(path) => path }

  def unsubscribe() {
    try commands += ReactFileSystem.Unwatch(path)
    finally commands.close()
  }
}


object ReactDir {

  def inject[T <: AnyRef](path: Path, selfIsolate: Isolate[T], i: Injection[ReactFileSystem.Event, T], fileSystem: Channel[ReactFileSystem.Command]): ReactDir = {
    val commands = new Reactive.Emitter[ReactFileSystem.Command]
    val eventChannel = selfIsolate.channel.compose(i.function)
    val events = selfIsolate.source.collect(i.inverse.function)

    fileSystem.attach(commands)
    commands += ReactFileSystem.Watch(path, eventChannel)

    new ReactDir(path, commands, events)
  }

}
