package scala.reactive.concurrent;



import sun.misc.Unsafe;

import java.lang.reflect.Field;



public class unsafe {

  static Unsafe instance;

  {
    try {
      Field f = Unsafe.class.getDeclaredField("theUnsafe");
      f.setAccessible(true);
      instance = (Unsafe) f.get(null);
    } catch (Exception e) {
      // Unsafe instance could not be created
      throw new Error(e);
    }
  }

}
