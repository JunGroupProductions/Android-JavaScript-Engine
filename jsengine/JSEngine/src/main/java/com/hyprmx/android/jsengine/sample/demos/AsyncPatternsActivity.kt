package com.hyprmx.android.jsengine.sample.demos

import android.os.Looper
import com.hyprmx.android.jsengine.JSEnginePromise
import com.hyprmx.android.jsengine.JSEnginePromiseReceiver
import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R

/**
 * ANR prevention. JavaScript runtimes are single-threaded and every call is synchronized, so a
 * slow script blocks whatever thread calls it. The golden rule: run JS off the main thread and
 * post results back. This screen contrasts the wrong and right approaches, then shows a Promise
 * resolved on the JS job executor and bridged back to the UI thread.
 */
class AsyncPatternsActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.async_patterns_desc

    override fun onRunClicked() {
        info("Tip: the whole run below already happens on a background thread (see DemoActivity.runAsync).")
        info("")

        runAsync {
            demonstrateBlocking()
            demonstratePromiseBridge()
        }
    }

    /**
     * Shows the shape of the anti-pattern vs. the correct pattern. We only ever run the slow work
     * on this background thread — the point is to show *where* it must not run.
     */
    private fun demonstrateBlocking() {
        info("--- Blocking vs. background ---")
        info("BAD:  String r = context.evaluate(slowScript);  // on main thread -> ANR")
        info("GOOD: executor.execute { val r = context.evaluate(slowScript); handler.post { ui(r) } }")
        info("")

        JSDemoRunner.withContext { context ->
            JSDemoRunner.runStep(this, "Ran slow script off the main thread") {
                val slow: JavaScriptObject = context.compileFunction(
                    "function() { var n = 0; for (var i = 0; i < 3000000; i++) { n += i; } return n; }",
                    "slow.js"
                )
                val onMain = Looper.myLooper() == Looper.getMainLooper()
                "sum=${slow.call()} (onMainThread=$onMain)"
            }
        }
        info("")
    }

    /**
     * Resolves a JS Promise using the context's job executor, then bridges the resolved value
     * back onto the UI thread via the base activity's logger. Uses the library's JSEnginePromise
     * proxy interface (same one exercised by the library's testPromise).
     */
    private fun demonstratePromiseBridge() {
        info("--- Promise -> UI thread bridge ---")
        JSDemoRunner.withContext { context ->
            try {
                val jo = context.evaluateForJavaScriptObject(
                    "new Promise(function(resolve) { resolve('resolved by JS'); });"
                )
                val promise = jo.proxyInterface(JSEnginePromise::class.java)
                promise.then(object : JSEnginePromiseReceiver {
                    override fun receive(value: Any?) {
                        // log() marshals the result back to the UI thread for us.
                        log("Promise.then received: $value", false)
                    }
                })
                // With no custom job executor set, pending microtasks (the .then callback) are
                // drained synchronously after the next invocation completes. A trivial evaluate
                // is enough to flush the queue before the context is closed.
                context.evaluate("void 0")
            } catch (t: Throwable) {
                log("Promise bridge FAILED: ${t.javaClass.simpleName}: ${t.message}", true)
            }
        }
    }
}
