package io.reactors.common.concurrent;



import static io.reactors.common.concurrent.unsafe.instance;



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

  protected volatile RootOrSegmentOrFrozen<T> root;

  protected final RootOrSegmentOrFrozen<T> READ_ROOT() {
    return (RootOrSegmentOrFrozen<T>) instance.getObject(this, ROOT_OFFSET);
  }

  protected final void WRITE_ROOT(RootOrSegmentOrFrozen<T> r) {
    instance.putObject(this, ROOT_OFFSET, r);
  }

  protected final boolean CAS_ROOT(RootOrSegmentOrFrozen<T> oldValue,
    RootOrSegmentOrFrozen<T> newValue) {
    return instance.compareAndSwapObject(this, ROOT_OFFSET, oldValue, newValue);
  }

}
