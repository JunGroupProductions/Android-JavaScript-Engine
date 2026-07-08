package com.hyprmx.android.jsengine;

import org.junit.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class WeakExactHashMapTests {
    private static final int GC_ATTEMPTS = 20;

    // Isolated in its own method so the key has no test-frame local pinning it alive on ART,
    // where locals are kept reachable until the enclosing method exits.
    private static WeakReference<Object> putWeakKey(WeakExactHashMap<Object, Object> map, Object value, ReferenceQueue<Object> queue) {
        Object key = new Object();
        map.put(key, value);
        assertEquals(map.get(key), value);
        return new WeakReference<>(key, queue);
    }

    @Test
    public void testMap() {
        Object value = new Object();
        WeakExactHashMap<Object, Object> map = new WeakExactHashMap<>();
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        WeakReference<Object> ref = putWeakKey(map, value, queue);

        assertEquals(map.size(), 1);

        System.gc();
        System.gc();

        assertEquals(map.size(), 1);

        assertNotNull("key was not collected within " + GC_ATTEMPTS + " GC attempts", awaitCollection(queue));
        assertEquals(null, ref.get());

        map.purge();
        assertEquals(map.size(), 0);
    }

    private static Object awaitCollection(ReferenceQueue<Object> queue) {
        for (int i = 0; i < GC_ATTEMPTS; i++) {
            System.gc();
            try {
                Object collected = queue.remove(100);
                if (collected != null)
                    return collected;
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while waiting for weak reference collection");
            }
        }
        return null;
    }
}
