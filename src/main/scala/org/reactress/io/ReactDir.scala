package org.reactress
package io



import java.nio.file.Path
import algebra.Injection



class ReactDir private[reactress] (
  val path: ReactPath,
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

  def apply[T <: AnyRef](path: ReactPath, isolate: Isolate[T], i: Injection[ReactFileSystem.Event, T]): ReactDir = {
    val commands = new Reactive.Emitter[ReactFileSystem.Command]
    val fileSystem = path.fileSystem
    val eventChannel = isolate.channel.compose(i.function)
    val events = isolate.source.collect(i.inverse.function)

    fileSystem.attach(commands)
    commands += ReactFileSystem.Watch(path, eventChannel)

    new ReactDir(path, commands, events)
  }

}
