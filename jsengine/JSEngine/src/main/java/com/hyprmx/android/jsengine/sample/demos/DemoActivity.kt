package com.hyprmx.android.jsengine.sample.demos

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.hyprmx.android.jsengine.sample.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Shared base for the six demo screens. Inflates the common `activity_demo` layout, wires the
 * Run button, and centralizes "run off the main thread, post results back" so every demo — not
 * just the async one — keeps JS execution off the UI thread and avoids ANRs.
 */
abstract class DemoActivity : Activity(), JSDemoRunner.Logger {

    private lateinit var outputView: TextView
    private lateinit var scrollView: ScrollView

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /** Description string-resource id shown above the Run button. Keep it short. */
    protected abstract val descriptionRes: Int

    /** Invoked (on the main thread) when the user taps Run. Implementations typically call [runAsync]. */
    protected abstract fun onRunClicked()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demo)

        findViewById<TextView>(R.id.demo_title).setText(descriptionRes)
        outputView = findViewById(R.id.demo_output)
        scrollView = findViewById(R.id.demo_scroll)

        findViewById<Button>(R.id.demo_run).setOnClickListener {
            clearLog()
            onRunClicked()
        }
    }

    /**
     * Runs [work] on a background thread. Any [log] calls made from within [work] are safely
     * marshalled back onto the main thread, so demos can log freely without touching threading.
     */
    protected fun runAsync(work: () -> Unit) {
        executor.execute {
            try {
                work()
            } catch (t: Throwable) {
                log("Unexpected failure: ${t.javaClass.simpleName}: ${t.message}", true)
            }
        }
    }

    /** Appends a colored line to the output log and auto-scrolls. Safe to call from any thread. */
    override fun log(line: String, isError: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { log(line, isError) }
            return
        }
        @Suppress("DEPRECATION")
        val color = resources.getColor(if (isError) R.color.log_fail else R.color.log_pass)
        val span = SpannableString(line + "\n")
        span.setSpan(ForegroundColorSpan(color), 0, line.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        outputView.append(span)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    /** Convenience for logging a plain (non-pass/fail) informational line. */
    protected fun info(line: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { info(line) }
            return
        }
        outputView.append(line + "\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    protected fun clearLog() {
        outputView.text = ""
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
