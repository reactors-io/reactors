package scala.reactive
package io



import java.nio.file.Path
import calc.Injection



class ReactPath private[reactive] (val nioPath: Path, val fileSystem: Channel[ReactFileSystem.Command]) {
  def directory[T <: AnyRef](self: Isolate[T])(i: Injection[ReactFileSystem.Event, T]): ReactDir = {
    ReactDir(this, self, i)
  }
}


object ReactPath {

}

