package com.hyprmx.android.jsengine;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JavaObjectTests {
    // Abstract so getDeclaredConstructor().newInstance() always fails with InstantiationException,
    // regardless of whether reflective access to the private constructor is allowed (nestmates in
    // Java 11+ can call a private constructor of an enclosing/nested class without an access check).
    private abstract static class NoPublicConstructor {
        private NoPublicConstructor() {
        }
    }

    @Test
    public void constructThrowsWhenNoPublicConstructorInstantiationFails() {
        JavaObject javaObject = new JavaObject(null, NoPublicConstructor.class);
        try {
            javaObject.construct();
            fail("expected IllegalArgumentException to be thrown, not returned");
        }
        catch (IllegalArgumentException expected) {
            assertTrue(expected.getCause() instanceof ReflectiveOperationException);
        }
    }
}
