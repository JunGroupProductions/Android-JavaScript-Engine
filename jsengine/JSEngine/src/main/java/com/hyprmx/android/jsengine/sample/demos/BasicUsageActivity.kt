package com.hyprmx.android.jsengine.sample.demos

import com.hyprmx.android.jsengine.JavaScriptObject
import com.hyprmx.android.jsengine.sample.R
import com.hyprmx.android.jsengine.sample.Widget

/**
 * The basics: evaluate a script, call a compiled function, pass arguments, expose a global, and
 * construct a Java object from JavaScript. Mirrors the intro snippets in the module README and
 * the testGlobal / testRoundtrip / testNewObject tests.
 */
class BasicUsageActivity : DemoActivity() {

    override val descriptionRes: Int = R.string.basic_usage_desc

    override fun onRunClicked() = runAsync {
        JSDemoRunner.withContext { context ->
            // 1. Hello world — evaluate an expression and get the result back as a String.
            JSDemoRunner.runStep(this, "Evaluate expression") {
                context.evaluate("'hello, ' + 'world'", String::class.java)
            }

            // 2. Arithmetic — numbers come back as Integer or Double.
            JSDemoRunner.runStep(this, "Evaluate arithmetic") {
                context.evaluate("6 * 7")
            }

            // 3. Compile a function once, then call it with an argument.
            JSDemoRunner.runStep(this, "Call function('JSEngine')") {
                val greet: JavaScriptObject = context.compileFunction(
                    "function(name) { return 'hi ' + name + '!'; }", "greet.js"
                )
                greet.call("JSEngine")
            }

            // 4a. Expose a Java object as a global, then call one of its methods from JS.
            JSDemoRunner.runStep(this, "Global console.log from JS") {
                val console = Console()
                context.globalObject.set("console", console)
                context.evaluate("console.log('logged from JavaScript')")
                console.lastMessage
            }

            // 4b. Pass a Java object as an argument and let JS call one of its methods back.
            JSDemoRunner.runStep(this, "JS calls back into a Java object") {
                val logged = StringBuilder()
                val printer: Printer = object : Printer {
                    override fun print(message: String) {
                        logged.append(message)
                    }
                }
                val fn: JavaScriptObject = context.compileFunction(
                    "function(printer) { printer.print('called from JavaScript'); }", "invoke.js"
                )
                fn.call(printer)
                logged.toString()
            }

            // 5. Construct a Java POJO from JavaScript and read a property back.
            JSDemoRunner.runStep(this, "Construct Java Widget in JS") {
                context.globalObject.set("Widget", Widget::class.java)
                context.evaluate(
                    "var w = new Widget('seed', 1); w.label = 'updated'; w.greet('world');",
                    String::class.java
                )
            }
        }
    }

    /**
     * Single-method interface. When passed as an *argument* to a JS function it is coerced into a
     * callable JS function. Declared as a plain interface (not a Kotlin `fun interface`) so the
     * engine can resolve its interface method from the concrete implementing class.
     */
    interface Printer {
        fun print(message: String)
    }

    /** A Java object exposed as a global; JS calls its named `log` method. */
    class Console {
        var lastMessage: String? = null
            private set

        fun log(message: String) {
            lastMessage = message
        }
    }
}
