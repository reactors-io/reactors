package io.reactors.japi;






class Channel<T> {
  private io.reactors.Channel<T> proxy;

  Channel(io.reactors.Channel<T> proxy) {
    this.proxy = proxy;
  }

  void send(T event) {
    this.proxy.$bang(event);
  }
}
