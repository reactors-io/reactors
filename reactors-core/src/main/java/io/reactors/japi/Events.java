package io.reactors.japi;



import java.util.function.Consumer;



public class Events<T> {
  private io.reactors.Events<T> self;

  Events(io.reactors.Events<T> self) {
    this.self = self;
  }

  public void onEvent(Consumer<T> c) {
    self.onEvent(Util.toScalaFunction(c));
  }

  public void onReaction(Observer<T> observer) {
    self.onReaction(observer.self);
  }

  public void onEventOrDone(Consumer<T> c, Runnable r) {
    self.onEventOrDone(Util.toScalaFunction(c), Util.toScalaFunction(r));
  }

  public void onDone(Runnable r) {
    self.onDone(Util.toScalaFunction(r), null);
  }

  public void onExcept(Consumer<Throwable> c) {
    self.onExcept(Util.toScalaPartialFunction(c));
  }

  public static <T> Events<T> never() {
    return new Events(io.reactors.Events$.MODULE$.never());
  }

  public static <T> Events.Emitter<T> emitter() {
    return new Events.Emitter(new io.reactors.Events.Emitter<T>());
  }

  public static class Emitter<T> extends Events<T> {
    private io.reactors.Events.Emitter<T> self;

    Emitter(io.reactors.Events.Emitter<T> self) {
      super(self);
      this.self = self;
    }

    public void react(T x) {
      self.react(x);
    }

    public void except(Throwable t) {
      self.except(t);
    }

    public void unreact() {
      self.unreact();
    }
  }
}
