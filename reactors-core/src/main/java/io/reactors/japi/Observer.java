package io.reactors.japi;



import java.util.function.Consumer;



public abstract class Observer<T> {
  private io.reactors.Observer<T> self;

  Observer(io.reactors.Observer<T> self) {
    this.self = self;
  }

  public void react(T value, Object hint) {
    self.react(value, hint);
  }

  public void except(Throwable t) {
    self.except(t);
  }

  public void unreact() {
    self.unreact();
  }
}
