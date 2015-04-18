package scala.reactive.core.concurrent;



import static scala.reactive.core.concurrent.unsafe.instance;



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

  protected volatile RootOrSegmentOrFrozen left;

  protected volatile RootOrSegmentOrFrozen right;

  protected final RootOrSegmentOrFrozen READ_LEFT() {
    return (RootOrSegmentOrFrozen) instance.getObject(this, LEFT_OFFSET);
  }

  protected final void WRITE_LEFT(RootOrSegmentOrFrozen obj) {
    instance.putObject(this, LEFT_OFFSET, obj);
  }

  protected final boolean CAS_LEFT(RootOrSegmentOrFrozen oldValue,
    RootOrSegmentOrFrozen newValue) {
    return instance.compareAndSwapObject(this, LEFT_OFFSET, oldValue, newValue);
  }

  protected final RootOrSegmentOrFrozen READ_RIGHT() {
    return (RootOrSegmentOrFrozen) instance.getObject(this, RIGHT_OFFSET);
  }

  protected final void WRITE_RIGHT(RootOrSegmentOrFrozen obj) {
    instance.putObject(this, RIGHT_OFFSET, obj);
  }

  protected final boolean CAS_RIGHT(RootOrSegmentOrFrozen oldValue,
    RootOrSegmentOrFrozen newValue) {
    return instance.compareAndSwapObject(this, RIGHT_OFFSET, oldValue,
      newValue);
  }

}
