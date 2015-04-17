package scala.reactive.core.concurrent;



import static scala.reactive.core.concurrent.unsafe.instance;



abstract class SnapQueueBase<T> {

  protected final static long ROOT_OFFSET;

  static {
    Class<?> cls = SnapQueueBase.class;
    try {
      ROOT_OFFSET = instance.objectFieldOffset(cls.getDeclaredField("root"));
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  protected volatile RootOrSegmentOrFrozen root;

  protected final RootOrSegmentOrFrozen READ_ROOT() {
    return (RootOrSegmentOrFrozen) instance.getObject(this, ROOT_OFFSET);
  }

  protected final void WRITE_ROOT(RootOrSegmentOrFrozen r) {
    instance.putObject(this, ROOT_OFFSET, r);
  }

  protected final boolean CAS_ROOT(RootOrSegmentOrFrozen oldValue,
    RootOrSegmentOrFrozen newValue) {
    return instance.compareAndSwapObject(this, ROOT_OFFSET, oldValue, newValue);
  }

}
