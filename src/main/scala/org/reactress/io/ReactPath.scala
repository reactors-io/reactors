package org.reactress
package io



import java.nio.file.Path
import algebra.Injection



class ReactPath private[reactress] (val nioPath: Path, val fileSystem: Channel[ReactFileSystem.Command]) {
  def directory[T <: AnyRef](self: Isolate[T])(i: Injection[ReactFileSystem.Event, T]): ReactDir = {
    ReactDir(this, self, i)
  }
}


object ReactPath {

}

