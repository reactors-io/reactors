package io.reactors.japi;



import scala.reflect.*;



public class Util {
  public static <T> io.reactors.Arrayable<T> arrayable() {
    ClassTag<T> tag = ClassTag$.MODULE$.apply(Object.class);
    io.reactors.Arrayable<T> a = io.reactors.Arrayable$.MODULE$.ref(tag);
    return a;
  }
}
