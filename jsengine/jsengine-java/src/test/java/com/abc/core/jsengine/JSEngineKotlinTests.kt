package com.abc.core.jsengine

import org.junit.Assert
import org.junit.Test
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class JSEngineKotlinTests {
    companion object {
        init {
            // for non-android jvm
            try {
                System.load(File("jsengine-jni/build/lib/main/debug/libjsengine-jni.dylib").canonicalPath);
            }
            catch (e: Error) {
            }
            try {
                System.load(File("../jsengine-jni/build/lib/main/debug/libjsengine-jni.dylib").canonicalPath);
            }
            catch (e: Error) {
            }
        }
    }

    internal interface ArrayTypeInterface {
        fun foo(): Int
    }

    internal interface ArrayInterface {
        val numbers: Array<ArrayTypeInterface>
    }

    @Test
    fun testArray() {
        val context = JSEngineContext.create()
        val iface = context.evaluate("(function() { return [() => 2, () => 3, () => 4, () => 5] })", ArrayInterface::class.java)
        var total = 0
        for (i in iface.numbers) {
            total += i.foo()
        }
        Assert.assertEquals(total.toLong(), 14)
        context.close()
    }

    @Test
    fun testPromise() {
        val context = JSEngineContext.create();

        val script = "new Promise((resolve, reject) => { resolve('hello'); });"
        val promise = context.evaluate(script, JSEnginePromise::class.java)
//        val promise = jo.proxyInterface(JSEnginePromise::class.java)

        var ret = "world"
        val suspendFun = suspend {
            ret = promise.await() as String
        }

        suspendFun.startCoroutine(Continuation(EmptyCoroutineContext) {
        })

        assert(ret == "hello")
    }
}
