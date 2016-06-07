package io.reactors.japi;






public class Channel<T> {
  private io.reactors.Channel<T> self;

  Channel(io.reactors.Channel<T> self) {
    this.self = self;
  }

  void send(T event) {
    this.self.$bang(event);
  }
}
