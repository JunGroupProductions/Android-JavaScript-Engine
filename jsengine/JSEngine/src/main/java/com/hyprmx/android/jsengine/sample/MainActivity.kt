package com.hyprmx.android.jsengine.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.hyprmx.android.jsengine.JSEngineContext

// Not a UI demo - this is the on-device repro harness for the R8 horizontal-class-merging
// VerifyError fixed by PLAYER-27424 (see JavaObject/JavaMethodObject/Memoize). Runs against
// this app's own minified release build, so a regression under a new AGP/R8 shows up as a
// VerifyError here instead of surfacing later in a real consumer app.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The content view fills the whole screen but draws its single line of text at the
        // top, right under the action bar - hide it so the result is actually visible.
        actionBar?.hide()

        // No app theme is set, so the platform default leaves text/background contrast
        // undefined here - set both explicitly rather than relying on inherited styling.
        val textView = TextView(this)
        textView.textSize = 18f
        textView.setTextColor(Color.BLACK)
        textView.setBackgroundColor(Color.WHITE)
        textView.setPadding(32, 32, 32, 32)
        setContentView(textView)

        val result = runR8VerifyProbe()
        textView.text = result
        Log.i(TAG, result)
    }

    private fun runR8VerifyProbe(): String {
        return try {
            JSEngineContext.create().use { context ->
                context.globalObject.set("Widget", Widget::class.java)
                val script = """
                    var w = new Widget('seed', 1);
                    w.label = 'updated';
                    var readBack = w.label;
                    var greeting = w.greet('world');
                    var w2 = new Widget();
                    readBack + '|' + greeting + '|' + w2.label;
                """.trimIndent()
                val outcome = context.evaluate(script, String::class.java)
                "PASS ($outcome)"
            }
        } catch (t: Throwable) {
            Log.e(TAG, "R8 verify probe failed", t)
            "FAIL ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    companion object {
        private const val TAG = "R8VerifyProbe"
    }
}
