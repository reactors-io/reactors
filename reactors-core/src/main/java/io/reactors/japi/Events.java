package io.reactors.japi;



import java.util.function.Consumer;



public class Events<T> {
  private io.reactors.Events<T> self;

  Events(io.reactors.Events<T> self) {
    this.self = self;
  }

  public void onEvent(Consumer<T> c) {
    self.onEvent(new scala.runtime.AbstractFunction1<T, scala.runtime.BoxedUnit>() {
      public scala.runtime.BoxedUnit apply(T x) {
        c.accept(x);
        return scala.runtime.BoxedUnit.UNIT;
      }
    });
  }

  public static <T> Events<T> never() {
    return new Events(io.reactors.Events$.MODULE$.never());
  }
}
