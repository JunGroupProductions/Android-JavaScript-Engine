package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JSEngineContext
import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R

/**
 * Two high-value performance habits, measured on-device (QuickJS):
 *   1. Compile a function once and reuse it, instead of re-`evaluate`-ing source every call.
 *   2. Do work in a single batched JS call instead of many small Java->JS round trips.
 * Also reports the context's own accumulated script-execution time via
 * resetTotalScriptExecutionTime / getTotalScriptExecutionTime.
 */
class PerformanceActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.performance_desc

    private val iterations = 2_000

    override fun onRunClicked() = runAsync {
        JSDemoRunner.withContext { context ->
            compiledReuseVsReevaluate(context)
            info("")
            batchVsIndividual(context)
        }
    }

    private fun compiledReuseVsReevaluate(context: JSEngineContext) {
        info("--- Compiled reuse vs. re-evaluate ($iterations calls) ---")

        val reevaluate = timeMs {
            for (i in 0 until iterations) {
                context.evaluate("(function(x) { return x * 2; })($i)")
            }
        }
        log("re-evaluate source each call: ${reevaluate} ms", false)

        val reuse = timeMs {
            val fn: JavaScriptObject = context.compileFunction("function(x) { return x * 2; }", "double.js")
            for (i in 0 until iterations) {
                fn.call(i)
            }
        }
        log("compile once + reuse:        ${reuse} ms", false)
        log("compiled reuse is faster: ${reuse < reevaluate}", reuse >= reevaluate)
    }

    private fun batchVsIndividual(context: JSEngineContext) {
        info("--- Batched vs. individual round trips ($iterations items) ---")

        context.resetTotalScriptExecutionTime()

        val individual = timeMs {
            val add: JavaScriptObject = context.compileFunction("function(a, b) { return a + b; }", "add.js")
            var sum = 0.0
            for (i in 0 until iterations) {
                sum += (add.call(i, 1) as Number).toDouble()
            }
        }
        log("individual JS calls: ${individual} ms", false)

        val batched = timeMs {
            val sumAll: JavaScriptObject = context.compileFunction(
                "function(n) { var s = 0; for (var i = 0; i < n; i++) s += i + 1; return s; }",
                "sumAll.js"
            )
            sumAll.call(iterations)
        }
        log("single batched JS call: ${batched} ms", false)
        log("batching is faster: ${batched < individual}", batched >= individual)

        info("context total script time (both approaches): ${context.totalScriptExecutionTime} ms")
    }

    private inline fun timeMs(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }
}
