package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R

/**
 * Errors cross the boundary in both directions with a spliced JS+Java stack trace:
 *   - A JS `throw` surfaces in Java with the JS call frames (func1/func2/func3).
 *   - A Java exception thrown inside a JS-invoked callback propagates back with both stacks.
 *   - `newError(throwable)` lets JS re-throw a Java exception while preserving its original type.
 * Mirrors testDuktapeException / testDuktapeExceptionFromJava / testErrorExcepionCoercion.
 */
class ErrorHandlingActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.error_handling_desc

    override fun onRunClicked() = runAsync {
        JSDemoRunner.withContext { context ->

            // 1. JS-thrown error -> Java, with JS stack frames.
            info("--- JS error -> Java (spliced stack) ---")
            try {
                val script = "function() {" +
                        "function func1() { throw new Error('boom from JS'); }" +
                        "function func2() { func1(); }" +
                        "function func3() { func2(); }" +
                        "func3();" +
                        "}"
                context.compileFunction(script, "throwing.js").call()
                log("expected an exception but none was thrown", true)
            } catch (t: Throwable) {
                log("caught ${t.javaClass.simpleName}: ${t.message}", false)
                logJsFrames(t)
            }
            info("")

            // 2. Java exception thrown inside a JS-invoked callback.
            info("--- Java exception via JS callback ---")
            try {
                val script = "function(cb) {" +
                        "function func1() { cb.callback(); }" +
                        "function func2() { func1(); }" +
                        "function func3() { func2(); }" +
                        "func3();" +
                        "}"
                val cb = object : Callback {
                    override fun callback() {
                        throw IllegalStateException("boom from Java")
                    }
                }
                context.compileFunction(script, "callback.js").call(cb)
                log("expected an exception but none was thrown", true)
            } catch (t: Throwable) {
                log("caught ${t.javaClass.simpleName}: ${t.message}", false)
                logJsFrames(t)
            }
            info("")

            // 3. newError: JS throws a Java exception, original type preserved on the way back.
            info("--- newError round-trip preserves type ---")
            try {
                val fn: JavaScriptObject = context.evaluateForJavaScriptObject(
                    "(function(t) {" +
                            "function foo1() { throw t.make(); }" +
                            "function foo2() { foo1(); }" +
                            "foo2();" +
                            "})"
                )
                fn.call(object : MakeError {
                    override fun make(): JavaScriptObject {
                        return context.newError(NumberFormatException("original Java exception"))
                    }
                })
                log("expected an exception but none was thrown", true)
            } catch (t: Throwable) {
                val preserved = t is NumberFormatException
                log("caught ${t.javaClass.simpleName}: ${t.message}", false)
                log("original exception type preserved: $preserved", !preserved)
                logJsFrames(t)
            }
        }
    }

    private fun logJsFrames(t: Throwable) {
        val frames = t.stackTrace
            .map { it.methodName }
            .filter { it.contains("func") || it.contains("foo") || it.contains("callback") }
            .distinct()
        if (frames.isNotEmpty()) {
            info("  JS frames: ${frames.joinToString(" -> ")}")
        }
    }

    // Plain interfaces (not `fun interface`): callbacks passed into JS must implement a real
    // interface so the engine can coerce them into JS functions.
    interface Callback {
        fun callback()
    }

    interface MakeError {
        fun make(): JavaScriptObject
    }
}
