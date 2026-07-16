package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JSEngineContext

/**
 * Small helpers shared by every demo screen. Each screen is fundamentally "create a context,
 * run several labeled JS interactions, report the result or the exception" — this centralizes
 * that shape so the individual demos only contain the interesting JS/Java bits.
 */
object JSDemoRunner {

    /** Callback used to append a line to a demo's on-screen log. */
    fun interface Logger {
        fun log(line: String, isError: Boolean)
    }

    /**
     * Creates a [JSEngineContext] (QuickJS by default), runs [block] with it, and always closes
     * it afterwards. Mirrors the try/finally lifecycle every test in the library follows and the
     * `use {}` pattern the memory-management demo teaches.
     */
    fun <T> withContext(useQuickJS: Boolean = true, block: (JSEngineContext) -> T): T {
        val context = JSEngineContext.create(useQuickJS)
        try {
            return block(context)
        } finally {
            context.close()
        }
    }

    /**
     * Runs a single labeled step, logging either `"label: result"` on success or
     * `"label FAILED: ..."` on any exception. Never rethrows, so one failing step doesn't abort
     * the rest of a demo run.
     */
    fun runStep(logger: Logger, label: String, block: () -> Any?) {
        try {
            val result = block()
            logger.log("$label: ${format(result)}", false)
        } catch (t: Throwable) {
            logger.log("$label FAILED: ${t.javaClass.simpleName}: ${t.message}", true)
        }
    }

    private fun format(result: Any?): String = when (result) {
        null -> "null"
        is ByteArray -> result.joinToString(prefix = "[", postfix = "]") { (it.toInt() and 0xFF).toString() }
        else -> result.toString()
    }
}
