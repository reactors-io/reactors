package org.reactors






package concurrent {

  class AnonymousReactor[T](body: Events[T] => Unit) extends Reactor[T] {
    body(main.events)
  }

}


package object concurrent {

}
