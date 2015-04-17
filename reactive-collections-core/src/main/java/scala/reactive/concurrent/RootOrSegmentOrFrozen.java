package scala.reactive.core.concurrent;



import static scala.reactive.core.concurrent.unsafe.instance;



interface RootOrSegmentOrFrozen<T> {

  boolean enqueue(T x);

  Object dequeue();

}
