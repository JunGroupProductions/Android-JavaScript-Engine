package com.hyprmx.android.jsengine;

import org.junit.Ignore;
import org.junit.Test;

import java.lang.ref.WeakReference;

import static org.junit.Assert.assertEquals;

public class WeakExactHashMapTests {
    @Ignore("Relies on System.gc() deterministically clearing WeakReferences. On ART debuggable builds locals are kept alive until method exit, so the wait loop never terminates and the run hangs.")
    @Test
    public void testMap() {
        Object key = new Object();
        Object value = new Object();
        WeakReference<Object> ref = new WeakReference<>(key);
        WeakExactHashMap<Object, Object> map = new WeakExactHashMap<>();
        map.put(key, value);
        assertEquals(map.get(key), value);
        assertEquals(map.size(), 1);

        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();
        System.gc();

        assertEquals(map.get(key), value);
        assertEquals(map.size(), 1);

        key = null;
        while (ref.get() != null) {
            System.gc();
        }
        map.purge();
        assertEquals(map.size(), 0);
    }
}
