package scala.reactive.core.concurrent;



import static scala.reactive.core.concurrent.unsafe.instance;



class SegmentBase<T> {

  protected final static Object EMPTY = null;

  protected final static Object FROZEN = new Object();

  protected final static Object NONE = new Object();

  protected final static Object RETRY = new Object();

  protected final static long ARRAY_OFFSET =
    instance.arrayBaseOffset(Object[].class);

  protected final static long ARRAY_SCALE =
    instance.arrayIndexScale(Object[].class);

  protected final static long HEAD_OFFSET;

  protected final static long LAST_OFFSET;

  static {
    Class<?> cls = SegmentBase.class;
    try {
      HEAD_OFFSET = instance.objectFieldOffset(cls.getDeclaredField("head"));
      LAST_OFFSET = instance.objectFieldOffset(cls.getDeclaredField("last"));
    } catch (Exception e) {
      throw new Error(e);
    }
  }

  protected volatile int head;

  protected volatile int last;

  protected Object[] array;

  protected SegmentBase(int length) {
    array = new Object[length];
  }

  protected final int READ_HEAD() {
    return instance.getInt(this, HEAD_OFFSET);
  }

  protected final void WRITE_HEAD(int newValue) {
    instance.putInt(this, HEAD_OFFSET, newValue);
  }

  protected final boolean CAS_HEAD(int oldValue, int newValue) {
    return instance.compareAndSwapInt(this, HEAD_OFFSET, oldValue, newValue);
  }

  protected final int READ_LAST() {
    return instance.getInt(this, LAST_OFFSET);
  }

  protected final void WRITE_LAST(int newValue) {
    instance.putInt(this, LAST_OFFSET, newValue);
  }

  protected final boolean CAS_LAST(int oldValue, int newValue) {
    return instance.compareAndSwapInt(this, LAST_OFFSET, oldValue, newValue);
  }

  protected final Object READ_ARRAY(int i) {
    return instance.getObject(array, ARRAY_OFFSET + ARRAY_SCALE * i);
  }

  protected final void WRITE_ARRAY(int i, Object newValue) {
    instance.putObject(array, ARRAY_OFFSET + ARRAY_SCALE * i, newValue);
  }

  protected final boolean CAS_ARRAY(int i, Object oldValue, Object newValue) {
    return instance.compareAndSwapObject(array, ARRAY_OFFSET + ARRAY_SCALE * i,
      oldValue, newValue);
  }

}
