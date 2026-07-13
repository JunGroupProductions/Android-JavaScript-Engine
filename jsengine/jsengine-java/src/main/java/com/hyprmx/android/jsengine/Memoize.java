package com.hyprmx.android.jsengine;

import java.lang.reflect.AccessibleObject;
import java.util.HashMap;

public class Memoize<T> {
  public interface MemoizeMap<V> {
    boolean containsKey(Object key);
    V get(Object key);
    V put(Integer key, V value);
    void clear();
  }

  private static class MemoizeMapImpl<V> extends HashMap<Integer, V> implements MemoizeMap<V> {
    /**
     *
     */
    private static final long serialVersionUID = -4020434697394716201L;
  }

  public Memoize(MemoizeMap<T> map) {
    store = map;
  }
  public Memoize() {
    this(new MemoizeMapImpl<>());
  }

  public static int hashCode(Object... objects) {
    int ret = 0;
    for (int i = 0; i < objects.length; i++) {
      Object o = objects[i];
      ret ^= Integer.rotateLeft(o == null ? 0 : o.hashCode(), i);
    }
    ret ^= objects.length;
    return ret;
  }

  MemoizeMap<T> store;
  public T memoize(MemoizeFunc<T> func, Object... args) {
    int hash = hashCode(args);
    return memoize(func, hash);
  }

  void clear() {
    store.clear();
  }

  public T memoize(MemoizeFunc<T> func, Object arg0, Object[] args) {
    int hash = hashCode(args);
    hash ^= arg0 == null ? 0 : arg0.hashCode();
    return memoize(func, hash);
  }

  public T memoize(MemoizeFunc<T> func, Object arg0, Object[] args0, Object[] args1) {
    int hash = hashCode(args0);
    hash ^= hashCode(args1);
    hash ^= arg0 == null ? 0 : arg0.hashCode();
    return memoize(func, hash);
  }

  private T memoize(MemoizeFunc<T> func, int hash) {
    if (store.containsKey(hash)) {
      return store.get(hash);
    }
    T ret = func.process();
    store.put(hash, ret);
    return ret;
  }

  // members is passed through to func.process() as a parameter rather than captured in a
  // closure - see MemoizeArrayFunc for why. The cast is safe: callers always pass a genuine
  // Method[]/Constructor[]/Field[], which is array-covariant with AccessibleObject[].
  public T memoize(MemoizeArrayFunc<T> func, Object arg0, Object[] args) {
    int hash = hashCode(args);
    hash ^= arg0 == null ? 0 : arg0.hashCode();
    return memoize(func, hash, (AccessibleObject[]) args);
  }

  public T memoize(MemoizeArrayFunc<T> func, Object arg0, Object[] args0, Object[] args1) {
    int hash = hashCode(args0);
    hash ^= hashCode(args1);
    hash ^= arg0 == null ? 0 : arg0.hashCode();
    return memoize(func, hash, (AccessibleObject[]) args0);
  }

  private T memoize(MemoizeArrayFunc<T> func, int hash, AccessibleObject[] members) {
    if (store.containsKey(hash)) {
      return store.get(hash);
    }
    T ret = func.process(members);
    store.put(hash, ret);
    return ret;
  }
}
