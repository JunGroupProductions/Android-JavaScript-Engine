package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JSEngineContext
import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R
import java.nio.ByteBuffer

/**
 * Native memory lives outside the JVM heap, so contexts and mapped buffers must be cleaned up
 * explicitly. This screen shows the correct lifecycle (`use {}` / try-finally close), repeated
 * proxy creation followed by GC, and native-heap / mapping-count deltas after purge + gc.
 * Mirrors testRoundtripInterfaceCallbackGC and testHeapBehavior.
 */
class MemoryManagementActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.memory_management_desc

    override fun onRunClicked() = runAsync {
        info("--- Lifecycle ---")
        info("BAD:  val ctx = JSEngineContext.create(); /* ... */  // never closed -> native leak")
        info("GOOD: JSEngineContext.create().use { ctx -> /* ... */ }  // always closed")
        info("")

        // Repeated proxy creation + System.gc(): proves proxies get finalized without leaking.
        JSDemoRunner.withContext { context ->
            JSDemoRunner.runStep(this, "100x proxy create + gc, callbacks fired") {
                // `cb` is declared as a single-method interface on RoundTrip, so the engine coerces
                // it into a callable JS function — invoke it directly as cb(o), not cb.method(o).
                val script =
                    "function() { return { call: function(o, cb) { return cb(o); } }; }"
                val factory: JavaScriptObject = context.compileFunction(script, "factory.js")
                var calls = 0
                val cb = object : Callback {
                    override fun handle(value: Any?): Any? {
                        calls++
                        return value
                    }
                }
                for (i in 0 until 100) {
                    val iface = (factory.call() as JavaScriptObject).proxyInterface(RoundTrip::class.java)
                    iface.call(i, cb)
                    System.gc()
                }
                calls
            }
        }
        info("")

        // Native heap accounting around a sizeable direct ByteBuffer.
        info("--- Native heap accounting ---")
        JSDemoRunner.withContext { context ->
            demonstrateHeap(context)
        }
    }

    private fun demonstrateHeap(context: JSEngineContext) {
        val allocSize = 8_000_000
        val identity: JavaScriptObject =
            context.compileFunction("function(x) { return x; }", "identity.js")

        // Held in a mutable holder so we can null the references out and let GC reclaim them;
        // that's what makes the purge/gc below actually free the mapped native memory.
        val refs = arrayOfNulls<ByteBuffer>(2)
        refs[0] = ByteBuffer.allocateDirect(allocSize)
        refs[1] = identity.call(refs[0]) as ByteBuffer

        info("mapped native count after 1 buffer: ${context.mappedNativeCount}")
        val beforeHeap = context.heapSize
        info("heap before release: $beforeHeap bytes")

        // Drop references so the mappings can be purged.
        refs[0] = null
        refs[1] = null
        for (i in 0 until 10) System.gc()

        val purged = context.purgeNativeMappings()
        context.gc()
        val afterHeap = context.heapSize

        info("purged mappings: $purged")
        log(
            "heap after release: $afterHeap bytes (freed ${beforeHeap - afterHeap})",
            beforeHeap - afterHeap < allocSize
        )
        info("mapped native count after purge: ${context.mappedNativeCount}")
    }

    // Plain interface (not `fun interface`): a synthetic Kotlin SAM-lambda class isn't coerced
    // into a JS function, so callbacks passed into JS must implement a real interface. The method
    // is named `handle` (not `call`) to avoid colliding with JS's Function.prototype.call.
    interface Callback {
        fun handle(value: Any?): Any?
    }

    interface RoundTrip {
        fun call(value: Any?, cb: Callback): Any?
    }
}
