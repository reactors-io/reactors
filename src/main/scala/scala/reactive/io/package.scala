package scala.reactive



import java.nio.file.Path
import calc.Injection



package object io {

  implicit class ReactFileSystemOps(val self: Channel[ReactFileSystem.Command]) extends AnyVal {
    def injectDirectory[T <: AnyRef](nioPath: Path, i: Injection[ReactFileSystem.Event, T]): ReactDir = {
      ReactDir.inject(nioPath, Isolate.of[T], i, self)
    }
  }

}
