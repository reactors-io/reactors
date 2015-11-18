package scala.reactive.common.concurrent;



import static scala.reactive.common.concurrent.unsafe.instance;



abstract class RootBase<T> extends RootOrSegmentOrFrozen<T> {

  protected final static long LEFT_OFFSET;

  protected final static long RIGHT_OFFSET;

  static {
    Class<?> cls = RootBase.class;
    try {
      LEFT_OFFSET = instance.objectFieldOffset(cls.getDeclaredField("left"));
      RIGHT_OFFSET = instance.objectFieldOffset(cls.getDeclaredField("right"));
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  protected volatile SideBase<T> left;

  protected volatile SideBase<T> right;

  protected final SideBase<T> READ_LEFT() {
    return (SideBase<T>) instance.getObject(this, LEFT_OFFSET);
  }

  protected final void WRITE_LEFT(SideBase<T> obj) {
    instance.putObject(this, LEFT_OFFSET, obj);
  }

  protected final boolean CAS_LEFT(SideBase<T> oldValue,
    SideBase<T> newValue) {
    return instance.compareAndSwapObject(this, LEFT_OFFSET, oldValue, newValue);
  }

  protected final SideBase<T> READ_RIGHT() {
    return (SideBase<T>) instance.getObject(this, RIGHT_OFFSET);
  }

  protected final void WRITE_RIGHT(SideBase<T> obj) {
    instance.putObject(this, RIGHT_OFFSET, obj);
  }

  protected final boolean CAS_RIGHT(SideBase<T> oldValue,
    SideBase<T> newValue) {
    return instance.compareAndSwapObject(this, RIGHT_OFFSET, oldValue,
      newValue);
  }

}
