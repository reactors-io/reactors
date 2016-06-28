package io.reactors.japi;



import java.util.function.Consumer;
import scala.*;
import scala.reflect.*;
import scala.runtime.*;



class Util {
  public static <T> io.reactors.Arrayable<T> arrayable() {
    ClassTag<T> tag = ClassTag$.MODULE$.apply(Object.class);
    io.reactors.Arrayable<T> a = io.reactors.Arrayable$.MODULE$.ref(tag);
    return a;
  }

  public static <T> AbstractFunction0<BoxedUnit> toScalaFunction(Runnable r) {
    return new AbstractFunction0<BoxedUnit>() {
      public BoxedUnit apply() {
        r.run();
        return BoxedUnit.UNIT;
      }
    };
  }

  public static <T> AbstractFunction1<T, BoxedUnit> toScalaFunction(Consumer<T> c) {
    return new AbstractFunction1<T, BoxedUnit>() {
      public BoxedUnit apply(T x) {
        c.accept(x);
        return BoxedUnit.UNIT;
      }
    };
  }

  public static <T> PartialFunction<T, BoxedUnit> toScalaPartialFunction(
    Consumer<T> c
  ) {
    return new AbstractPartialFunction<T, BoxedUnit>() {
      public BoxedUnit apply(T x) {
        c.accept(x);
        return BoxedUnit.UNIT;
      }
      public boolean isDefinedAt(T x) {
        return true;
      }
    };
  }
}
