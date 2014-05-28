package scala.reactive



import java.nio.file.Path



package object io {

  implicit class ReactFileSystemOps(val self: Channel[ReactFileSystem.Command]) extends AnyVal {
    def reactPath(nioPath: Path) = new ReactPath(nioPath, self)
  }

}
